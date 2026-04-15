import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
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
} from '../api/versionsApi';

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
