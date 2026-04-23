import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Button, Slider, ButtonGroup, Spinner, useDisclosure, addToast, Modal, ModalContent, ModalHeader, ModalBody, ModalFooter, Textarea, Select, SelectItem, Chip } from "@heroui/react";
import { ArrowLeft, History, Download, UploadCloud, RotateCcw, Columns, AlignLeft, Send, CheckCircle, XCircle, Loader } from 'lucide-react';
import { useDocument, useUpdateDocument } from '../hooks/useDocuments';
import { useVersions, useDiff, useRollbackVersion, useApproveVersion, useRejectVersion } from '../hooks/useVersions';
import { formatVersionNumber } from '../api/transforms';
import { getVersionDownloadUrl, requestReview } from '../api/versionsApi';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useOrg } from '../context/OrgContext';
import NewVersionModal from '../components/NewVersionModal';
import CommentsPanel from '../components/CommentsPanel';
import { useCategories } from '../hooks/useCategories';

export default function DocumentViewerView() {
  const { docId } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [diffMode, setDiffMode] = useState('split');
  const [renderedDiff, setRenderedDiff] = useState(null);
  const [diffRendering, setDiffRendering] = useState(false);
  const [v1Preview, setV1Preview] = useState(null);
  const [v1PreviewLoading, setV1PreviewLoading] = useState(false);

  const { isOpen: isNewVersionOpen, onOpen: onNewVersionOpen, onOpenChange: onNewVersionOpenChange } = useDisclosure();
  const [rejectReason, setRejectReason] = useState('');
  const [showRejectModal, setShowRejectModal] = useState(false);

  const { activeRoles } = useOrg();
  const isReviewer = activeRoles.includes('REVIEWER') || activeRoles.includes('ADMIN');
  const canEditCategory = activeRoles.includes('ADMIN') || activeRoles.includes('AUTHOR');

  const { data: doc, isLoading: docLoading } = useDocument(docId);
  const { data: categoriesData = [] } = useCategories(doc?.org_id ?? null);
  const updateDocument = useUpdateDocument();

  const [selectedCategoryKeys, setSelectedCategoryKeys] = useState(new Set([]));
  useEffect(() => {
    const id = doc?.category_id;
    setSelectedCategoryKeys(typeof id === 'string' ? new Set([id]) : new Set([]));
  }, [doc?.category_id]);
  const { data: versionsData, isLoading: versionsLoading } = useVersions(docId, { page: 0, size: 100 });
  const rollback = useRollbackVersion();

  const versions = (versionsData?.content || versionsData || [])
    .map(v => ({
      ...v,
      label: formatVersionNumber(v.version_number),
      msg: `Version ${v.version_number}`,
      date: v.created_at ? new Date(v.created_at).toLocaleDateString('en-US', { month: 'short', day: '2-digit' }) : '',
    }))
    .sort((a, b) => a.version_number - b.version_number)
    .map((v, i) => ({ ...v, value: i }));

  const [sliderIndex, setSliderIndex] = useState(null);
  const effectiveIndex = sliderIndex ?? (versions.length > 0 ? versions.length - 1 : 0);
  const selectedVersion = versions[effectiveIndex];
  const prevVersion = effectiveIndex > 0 ? versions[effectiveIndex - 1] : null;
  const isLatest = effectiveIndex === versions.length - 1;
  const isUploading = selectedVersion?.is_uploading === true;
  useEffect(() => {
    setSliderIndex(null);
  }, [versions.length]);

  // ТУК Е ЗАЩИТАТА: Проверява дали има версия, която чака ревю
  const hasPendingReview = versions.some(v => v.status === 'PENDING');

  // Mutation for requesting a review
  const reviewMutation = useMutation({
    mutationFn: () => requestReview(docId, selectedVersion.id),
    onSuccess: () => {
      addToast({ title: 'Review Requested', description: 'Reviewers have been notified.', color: 'success' });
      queryClient.invalidateQueries({ queryKey: ['documents', docId, 'versions'] });
      queryClient.invalidateQueries({ queryKey: ['documents', docId] });
    },
    onError: (err) => {
      addToast({ title: 'Failed to request review', description: err?.message, color: 'danger' });
    }
  });

  const approveVersion = useApproveVersion();
  const rejectVersion = useRejectVersion();

  const handleApprove = () => {
    approveVersion.mutate(
      { docId, versionId: selectedVersion.id },
      { onSuccess: () => addToast({ title: 'Approved', description: 'Version approved.', color: 'success' }) }
    );
  };

  const handleRejectConfirm = () => {
    rejectVersion.mutate(
      { docId, versionId: selectedVersion.id, data: rejectReason.trim() ? { reason: rejectReason.trim() } : {} },
      { onSuccess: () => { setShowRejectModal(false); setRejectReason(''); addToast({ title: 'Rejected', color: 'warning' }); } }
    );
  };

  const { data: diffData, isLoading: diffLoading } = useDiff(docId, prevVersion?.id, selectedVersion?.id);

  // Render the diff view
  useEffect(() => {
    if (!diffData?.diff) {
      setRenderedDiff(null);
      setDiffRendering(false);
      return;
    }
    let cancelled = false;
    setDiffRendering(true);
    setRenderedDiff(null);

    (async () => {
      try {
        const payload = JSON.parse(diffData.diff);
        const [fromResp, toResp] = await Promise.all([fetch(payload.fromUrl), fetch(payload.toUrl)]);
        if (cancelled) return;
        const [fromText, toText] = await Promise.all([fromResp.text(), toResp.text()]);
        if (cancelled) return;

        const wasmMod = await import('../../pkg/diff_wasm.js');
        if (cancelled) return;
        await wasmMod.default();
        const diff = wasmMod.generate_unified_diff_wasm(fromText, toText);
        if (!cancelled) setRenderedDiff(diff);
      } catch (err) {
        if (!cancelled) { console.error(err); setRenderedDiff(null); }
      } finally {
        if (!cancelled) setDiffRendering(false);
      }
    })();
    return () => { cancelled = true; };
  }, [diffData]);

  const isV1 = effectiveIndex === 0 && versions.length > 0;
  
  // Logic for Version 1 full preview rendering
  useEffect(() => {
    if (!isV1 || !selectedVersion?.id || !docId) {
      setV1Preview(null);
      setV1PreviewLoading(false);
      return;
    }
    let cancelled = false;
    setV1PreviewLoading(true);
    setV1Preview(null);

    (async () => {
      try {
        const { download_url } = await getVersionDownloadUrl(docId, selectedVersion.id);
        if (!download_url || cancelled) return;
        const resp = await fetch(download_url);
        if (cancelled) return;
        const text = await resp.text();
        
        // Removed the slice restriction to load the full document
        if (!cancelled) setV1Preview(text);
      } catch (err) {
        console.error(err);
      } finally {
        if (!cancelled) setV1PreviewLoading(false);
      }
    })();
    return () => { cancelled = true; };
  }, [isV1, selectedVersion?.id, docId]);

  const handleDownload = async () => {
    if (!selectedVersion) return;
    try {
      const { download_url } = await getVersionDownloadUrl(docId, selectedVersion.id);
      if (!download_url) {
        addToast({ title: 'Preparing', description: "Document being prepared.", color: 'primary' });
        return;
      }
      const a = document.createElement('a');
      a.href = download_url;
      a.download = `${doc?.name ?? 'document'}_v${selectedVersion.version_number}`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
    } catch (err) {
      addToast({ title: 'Download failed', description: err?.message, color: 'danger' });
    }
  };

  if (docLoading || versionsLoading) return <div className="flex justify-center py-40"><Spinner color="primary" size="lg" /></div>;

  return (
    <div className="max-w-7xl mx-auto px-6 py-8 w-full z-10 flex-grow flex flex-col relative h-full">
      <div className="flex items-center gap-2 text-sm text-zinc-500 mb-6 border-b border-zinc-800 pb-4">
        <button onClick={() => navigate(-1)} className="flex items-center gap-1 hover:text-white transition"><ArrowLeft size={16} /> Back</button>
        <span>/</span>
        <span className="text-zinc-300 font-semibold">{doc?.name || "Document"}</span>
      </div>

      <div className="bg-zinc-900 border border-zinc-800 rounded-xl p-6 mb-6 flex flex-col lg:flex-row gap-8 items-center justify-between">
        <div className="w-full lg:w-1/2 flex flex-col relative pt-2">
          <div className="flex items-center gap-3 mb-6">
            <History size={16} className="text-lime-500" />
            <span className="text-sm font-semibold text-white">Version Timeline</span>
          </div>
          
          {/* Prevent slider rendering if there is only 1 version */}
          {versions.length > 1 ? (
            <Slider
              step={1} 
              maxValue={versions.length - 1} 
              minValue={0}
              value={effectiveIndex} 
              onChange={setSliderIndex}
              color="primary" 
              showSteps={versions.length <= 20} 
              marks={
                versions.length <= 10 
                  ? // If 10 or fewer versions, show all of them
                    versions.map(v => ({ value: v.value, label: v.label }))
                  : // If more than 10, apply the interval filter
                    versions
                      .filter((v, i) => i === 0 || i === versions.length - 1 || v.version_number % 10 === 0)
                      .map(v => ({ value: v.value, label: v.label }))
              }
              className="max-w-md"
            />
          ) : (
            <div className="text-sm text-zinc-500 bg-zinc-800/30 border border-zinc-800 rounded-lg px-4 py-2 inline-flex w-fit font-mono">
              V1 (Initial Version)
            </div>
          )}

          {categoriesData.length > 0 && (
            <div className="mt-6 flex items-center gap-3">
              <span className="text-xs text-zinc-500 shrink-0">Category</span>
              {canEditCategory ? (
                <Select
                  isClearable
                  size="sm"
                  variant="bordered"
                  aria-label="Document category"
                  placeholder="None"
                  className="max-w-[200px]"
                  selectedKeys={selectedCategoryKeys}
                  onSelectionChange={(keys) => {
                    const val = Array.from(keys)[0] ?? null;
                    setSelectedCategoryKeys(val ? new Set([val]) : new Set([]));
                    updateDocument.mutate(
                      { docId, data: { category_id: val } },
                      { onSuccess: () => addToast({ title: 'Category updated', color: 'success' }) }
                    );
                  }}
                  classNames={{ trigger: "border-zinc-700 bg-zinc-900/50 h-8 min-h-8", value: "text-zinc-300 text-xs" }}
                >
                  {categoriesData.map((c) => (
                    <SelectItem key={c.id}>{c.name}</SelectItem>
                  ))}
                </Select>
              ) : (
                typeof doc?.category_id === 'string' ? (
                  <Chip size="sm" variant="flat" className="bg-zinc-800 text-zinc-300 text-xs">
                    {categoriesData.find(c => c.id === doc.category_id)?.name ?? 'Unknown'}
                  </Chip>
                ) : (
                  <span className="text-xs text-zinc-600 italic">None</span>
                )
              )}
            </div>
          )}
        </div>

        <div className="w-full lg:w-auto flex flex-wrap gap-3 justify-end items-center mt-4 lg:mt-0">
          {selectedVersion && (
            <div className="text-right mr-4 hidden sm:block">
              <div className="text-sm font-bold text-white flex items-center gap-2 justify-end">
                Viewing: {selectedVersion.label}
                {isUploading && (
                  <span className="bg-blue-500/20 text-blue-400 text-[10px] px-2 py-0.5 rounded uppercase flex items-center gap-1">
                    <Loader size={10} className="animate-spin" /> Uploading
                  </span>
                )}
                {!isUploading && selectedVersion.status === 'DRAFT' && (
                  <span className="bg-default-500/20 text-default-400 text-[10px] px-2 py-0.5 rounded uppercase">Draft</span>
                )}
                {!isUploading && selectedVersion.status === 'PENDING' && (
                  <span className="bg-warning-500/20 text-warning-400 text-[10px] px-2 py-0.5 rounded uppercase">Reviewing</span>
                )}
              </div>
            </div>
          )}

          {isLatest && !isUploading && selectedVersion?.status === 'DRAFT' && (
            <Button color="secondary" startContent={<Send size={16} />} onPress={() => reviewMutation.mutate()} isLoading={reviewMutation.isPending}>
              Request Review
            </Button>
          )}

          {isLatest && !isUploading && selectedVersion?.status === 'PENDING' && isReviewer && (
            <>
              <Button color="success" variant="flat" startContent={<CheckCircle size={16} />} onPress={handleApprove} isLoading={approveVersion.isPending}>
                Approve
              </Button>
              <Button color="danger" variant="flat" startContent={<XCircle size={16} />} onPress={() => setShowRejectModal(true)}>
                Reject
              </Button>
            </>
          )}

          <Button variant="bordered" className="border-zinc-700 text-zinc-300" startContent={<Download size={18} />} onPress={handleDownload} isDisabled={isUploading}>Download</Button>

          {isLatest ? (
            <Button
              color="primary"
              startContent={<UploadCloud size={18} />}
              onPress={onNewVersionOpen}
              isDisabled={isUploading}
            >
              New Version
            </Button>
          ) : (
            <Button 
              color="warning" 
              variant="flat" 
              startContent={<RotateCcw size={18} />} 
              onPress={() => rollback.mutate({ docId, versionId: selectedVersion.id })} 
              isLoading={rollback.isPending}
              isDisabled={hasPendingReview} // ТУК Е ЗАЩИТАТА
            >
              Rollback
            </Button>
          )}
        </div>
      </div>

      {/* Main viewer container with fixed boundaries for internal scrolling */}
      <div className="flex-grow max-h-[600px] min-h-[400px] bg-zinc-950 border border-zinc-800 rounded-xl overflow-hidden flex flex-col relative mb-8">
        <div className="bg-zinc-900 border-b border-zinc-800 p-3 flex justify-between items-center shrink-0">
          <div className="text-sm font-medium flex items-center gap-2">
            <span className="bg-zinc-800 px-2 py-0.5 rounded text-xs font-mono text-zinc-300">{prevVersion ? prevVersion.label : 'Initial'}</span>
            <span className="text-zinc-500">&rarr;</span>
            <span className="bg-lime-500/20 border border-lime-500/30 px-2 py-0.5 rounded text-xs font-mono text-lime-400 font-bold">{selectedVersion?.label || '—'}</span>
          </div>
          <ButtonGroup size="sm">
            <Button variant={diffMode === 'split' ? "solid" : "bordered"} color="default" onPress={() => setDiffMode('split')} startContent={<Columns size={14} />} className={diffMode === 'split' ? "bg-zinc-700" : "border-zinc-700 text-zinc-400"}>Split</Button>
            <Button variant={diffMode === 'unified' ? "solid" : "bordered"} color="default" onPress={() => setDiffMode('unified')} startContent={<AlignLeft size={14} />} className={diffMode === 'unified' ? "bg-zinc-700" : "border-zinc-700 text-zinc-400"}>Unified</Button>
          </ButtonGroup>
        </div>

        {/* Scrollable area preventing white overscroll bounce via overscroll-contain */}
        <div className="flex-grow overflow-auto font-mono text-sm bg-zinc-950 text-zinc-300 overscroll-contain">
          {diffLoading || diffRendering || v1PreviewLoading ? (
            <div className="flex justify-center items-center h-full"><Spinner color="primary" /></div>
          ) : renderedDiff ? (
            diffMode === 'split' ? <SplitDiffView diff={renderedDiff} /> : <UnifiedDiffView diff={renderedDiff} />
          ) : v1Preview !== null ? (
            <V1PreviewView content={v1Preview} />
          ) : (
            <div className="flex items-center justify-center h-full text-zinc-600 p-8">Select a version to see the diff.</div>
          )}
        </div>
      </div>

      {selectedVersion && (
        <div className="shrink-0">
          <CommentsPanel docId={docId} versionId={selectedVersion.id} />
        </div>
      )}

      <NewVersionModal isOpen={isNewVersionOpen} onOpenChange={onNewVersionOpenChange} docId={docId} versions={versionsData?.content || versionsData || []} />

      <Modal isOpen={showRejectModal} onOpenChange={(open) => { if (!open) setShowRejectModal(false); }}>
        <ModalContent className="bg-zinc-900 border border-zinc-800">
          <ModalHeader className="text-white">Reject Version</ModalHeader>
          <ModalBody>
            <Textarea
              label="Reason (optional)"
              placeholder="Explain why this version is being rejected..."
              value={rejectReason}
              onValueChange={setRejectReason}
              variant="bordered"
              classNames={{ 
                input: 'text-white',
                label: 'text-zinc-400',
                inputWrapper: '!bg-transparent border-white hover:border-lime-500 data-[focus=true]:border-lime-500'
             }}
              minRows={3}
            />
          </ModalBody>
          <ModalFooter>
            <Button 
            color="primary" variant="light"
            onPress={() => setShowRejectModal(false)}>
              Cancel
            </Button>
            <Button color="danger" onPress={handleRejectConfirm} isLoading={rejectVersion.isPending}>Reject</Button>
          </ModalFooter>
        </ModalContent>
      </Modal>
    </div>
  );
}

// ─── Diff Rendering Components ────────────────────────────────────────────────

function lineClass(line) {
  if (line.startsWith('+')) return 'text-emerald-400 bg-emerald-950/40';
  if (line.startsWith('-')) return 'text-red-400 bg-red-950/40';
  if (line.startsWith('@@')) return 'text-blue-400 bg-blue-950/20';
  return 'text-zinc-400';
}

function UnifiedDiffView({ diff }) {
  const lines = diff.split('\n');
  return (
    <table className="w-full table-fixed border-collapse text-xs bg-zinc-950">
      <tbody>
        {lines.map((line, i) => (
          <tr key={i} className={lineClass(line)}>
            <td className="select-none w-12 text-right pr-4 py-0.5 text-zinc-600 border-r border-zinc-800">{i + 1}</td>
            <td className="px-4 py-0.5 whitespace-pre-wrap break-all">{line || ' '}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function SplitDiffView({ diff }) {
  const lines = diff.split('\n');
  const leftLines = [], rightLines = [];
  for (const line of lines) {
    if (line.startsWith('-')) { leftLines.push(line); rightLines.push(null); }
    else if (line.startsWith('+')) { leftLines.push(null); rightLines.push(line); }
    else { leftLines.push(line); rightLines.push(line); }
  }
  const maxLen = Math.max(leftLines.length, rightLines.length);
  return (
    <table className="w-full table-fixed border-collapse text-xs bg-zinc-950">
      <tbody>
        {Array.from({ length: maxLen }, (_, i) => (
          <tr key={i}>
            <td className={`px-4 py-0.5 whitespace-pre-wrap break-all w-1/2 border-r border-zinc-800 ${leftLines[i] ? lineClass(leftLines[i]) : 'text-zinc-700'}`}>{leftLines[i] || ' '}</td>
            <td className={`px-4 py-0.5 whitespace-pre-wrap break-all w-1/2 ${rightLines[i] ? lineClass(rightLines[i]) : 'text-zinc-700'}`}>{rightLines[i] || ' '}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

// Renders the first version with a dark container and green highlighted lines 
function V1PreviewView({ content }) {
  const lines = content.split('\n');
  return (
    <table className="w-full table-fixed border-collapse text-xs bg-zinc-950">
      <tbody>
        {lines.map((line, i) => (
          <tr key={i} className="text-emerald-400 bg-emerald-950/40">
            <td className="select-none w-12 text-right pr-4 py-0.5 text-zinc-600 border-r border-zinc-800 bg-zinc-950">{i + 1}</td>
            <td className="px-4 py-0.5 whitespace-pre-wrap break-all pl-4">{line || ' '}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}