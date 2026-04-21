import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import {
  getVersions,
  createVersion,
  getVersion,
  getVersionDownloadUrl,
  rollbackVersion,
  approveVersion,
  rejectVersion,
  getDiff,
  getComments,
  addComment,
  uploadFileToS3,
} from '../api/versionsApi';
import { connectNotificationsStream } from '../api/notificationsApi';

export function useVersions(docId, params = {}) {
  return useQuery({
    queryKey: ['documents', docId, 'versions', params],
    queryFn: () => getVersions(docId, params),
    enabled: !!docId,
  });
}

export function useVersion(docId, versionId) {
  return useQuery({
    queryKey: ['documents', docId, 'versions', versionId],
    queryFn: () => getVersion(docId, versionId),
    enabled: !!docId && !!versionId,
  });
}

export function useVersionDownloadUrl(docId, versionId, options = {}) {
  return useQuery({
    queryKey: ['documents', docId, 'versions', versionId, 'download'],
    queryFn: () => getVersionDownloadUrl(docId, versionId),
    enabled: false,
    ...options,
  });
}

export function useCreateVersion() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ docId, data }) => createVersion(docId, data),
    onSuccess: (_, { docId }) => {
      qc.invalidateQueries({ queryKey: ['documents', docId, 'versions'] });
      qc.invalidateQueries({ queryKey: ['documents', docId] });
    },
  });
}

export function useRollbackVersion() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ docId, versionId }) => rollbackVersion(docId, versionId),
    onSuccess: (_, { docId }) => {
      qc.invalidateQueries({ queryKey: ['documents', docId, 'versions'] });
      qc.invalidateQueries({ queryKey: ['documents', docId] });
    },
  });
}

export function useApproveVersion() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ docId, versionId }) => approveVersion(docId, versionId),
    onSuccess: (_, { docId }) => {
      qc.invalidateQueries({ queryKey: ['documents', docId, 'versions'] });
      qc.invalidateQueries({ queryKey: ['documents', docId] });
      qc.invalidateQueries({ queryKey: ['requests'] });
    },
  });
}

export function useRejectVersion() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ docId, versionId, data }) => rejectVersion(docId, versionId, data),
    onSuccess: (_, { docId }) => {
      qc.invalidateQueries({ queryKey: ['documents', docId, 'versions'] });
      qc.invalidateQueries({ queryKey: ['documents', docId] });
      qc.invalidateQueries({ queryKey: ['requests'] });
    },
  });
}

export function useDiff(docId, fromVersionId, toVersionId) {
  return useQuery({
    queryKey: ['documents', docId, 'diff', fromVersionId, toVersionId],
    queryFn: () => getDiff(docId, fromVersionId, toVersionId),
    enabled: !!docId && !!fromVersionId && !!toVersionId,
  });
}

export function useComments(docId, versionId) {
  return useQuery({
    queryKey: ['documents', docId, 'versions', versionId, 'comments'],
    queryFn: () => getComments(docId, versionId),
    enabled: !!docId && !!versionId,
  });
}

export function useAddComment() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ docId, versionId, data }) => addComment(docId, versionId, data),
    onSuccess: (_, { docId, versionId }) => {
      qc.invalidateQueries({ queryKey: ['documents', docId, 'versions', versionId, 'comments'] });
    },
  });
}

// ─── Upload new version with diff logic ──────────────────────────────────────

const TEXT_MIME_PREFIXES = ['text/', 'application/json', 'application/xml', 'application/javascript'];

function isTextFile(file) {
  return TEXT_MIME_PREFIXES.some((p) => file.type.startsWith(p)) || file.name.endsWith('.txt') || file.name.endsWith('.md');
}

async function sha256hex(blob) {
  const buf = await blob.arrayBuffer();
  const hash = await crypto.subtle.digest('SHA-256', buf);
  return Array.from(new Uint8Array(hash)).map((b) => b.toString(16).padStart(2, '0')).join('');
}

/**
 * Waits for a DOCUMENT_RECONSTRUCTED SSE notification for the given docId.
 * Resolves with the reconstructed download URL, or rejects on timeout.
 */
function waitForReconstructedUrl(docId, timeoutMs = 15000) {
  return new Promise((resolve, reject) => {
    let stream;
    const timer = setTimeout(() => {
      stream?.close?.();
      reject(new Error('timeout'));
    }, timeoutMs);

    const parsePayload = (rawPayload) => {
      if (!rawPayload) return null;
      if (typeof rawPayload === 'object') return rawPayload;
      if (typeof rawPayload === 'string') {
        try {
          return JSON.parse(rawPayload);
        } catch {
          return null;
        }
      }
      return null;
    };

    stream = connectNotificationsStream((event) => {
      const payload = parsePayload(event?.payload);
      if (
        event?.type === 'DOCUMENT_RECONSTRUCTED' &&
        payload?.docId === String(docId)
      ) {
        const reconstructedUrl = payload.downloadUrl ?? payload.presignedDownloadUrl ?? null;
        if (!reconstructedUrl) {
          return;
        }
        clearTimeout(timer);
        stream?.close?.();
        resolve(reconstructedUrl);
      }
    });
  });
}

export function useUploadVersion(docId, versions) {
  const qc = useQueryClient();
  const [uploadStep, setUploadStep] = useState('idle');

  const mutation = useMutation({
    mutationFn: async ({ file, isDraft }) => {
      setUploadStep('fetching_base');

      // Lazy-load WASM module
      const wasmModule = await import('../../pkg/diff_wasm.js');
      await wasmModule.default();
      const { generate_unified_diff_wasm } = wasmModule;

      const sortedVersions = [...(versions || [])].sort((a, b) => a.version_number - b.version_number);
      const latestVersion = sortedVersions[sortedVersions.length - 1] ?? null;
      const nextVersionNumber = latestVersion ? latestVersion.version_number + 1 : 1;
      const isSnapshotPeriod = nextVersionNumber % 30 === 0;
      const canDiff = latestVersion && !isSnapshotPeriod && isTextFile(file);

      let isDiff = false;
      let diffPreview = '';
      let contentToUpload = file;

      if (canDiff) {
        try {
          // Fetch base version download URL
          const { download_url } = await getVersionDownloadUrl(docId, latestVersion.id);
          let baseContent;

          if (download_url && String(download_url) !== '') {
            // SNAPSHOT — immediately available
            const resp = await fetch(String(download_url));
            baseContent = await resp.text();
          } else {
            // DIFF — wait for reconstruction via SSE
            try {
              const reconstructedUrl = await waitForReconstructedUrl(docId, 15000);
              const resp = await fetch(reconstructedUrl);
              baseContent = await resp.text();
            } catch {
              // Timeout fallback: upload as full snapshot
              baseContent = null;
            }
          }

          if (baseContent !== null) {
            setUploadStep('computing_diff');
            const newContent = await file.text();
            const diff = generate_unified_diff_wasm(baseContent, newContent);

            const diffBlob = new Blob([diff], { type: 'text/plain' });
            if (diffBlob.size < file.size) {
              isDiff = true;
              diffPreview = diff.substring(0, 500);
              contentToUpload = diffBlob;
            }
          }
        } catch (err) {
          // Non-fatal: fall back to SNAPSHOT upload
          console.warn('[useUploadVersion] diff computation failed, uploading as snapshot:', err);
        }
      }

      setUploadStep('uploading');

      const checksum = await sha256hex(contentToUpload);
      const { upload_url } = await createVersion(docId, {
        checksum,
        is_draft: isDraft,
        is_diff: isDiff,
        diff_preview: diffPreview || null,
      });

      await uploadFileToS3(upload_url, contentToUpload);

      setUploadStep('done');
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['documents', docId, 'versions'] });
      qc.invalidateQueries({ queryKey: ['documents', docId] });
    },
    onError: () => {
      setUploadStep('idle');
    },
  });

  return { ...mutation, uploadStep, setUploadStep };
}
