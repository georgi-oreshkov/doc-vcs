import { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Button, Slider, ButtonGroup, Spinner, useDisclosure, addToast } from "@heroui/react";
import { ArrowLeft, History, Download, UploadCloud, RotateCcw, Columns, AlignLeft } from 'lucide-react';
import { useDocument } from '../hooks/useDocuments';
import { useVersions, useDiff, useRollbackVersion } from '../hooks/useVersions';
import { formatVersionNumber } from '../api/transforms';
import { getVersionDownloadUrl } from '../api/versionsApi';
import NewVersionModal from '../components/NewVersionModal';

export default function DocumentViewerView() {
  const { docId } = useParams();
  const navigate = useNavigate();
  const [diffMode, setDiffMode] = useState('split');
  const [renderedDiff, setRenderedDiff] = useState(null);
  const [diffRendering, setDiffRendering] = useState(false);
  const [v1Preview, setV1Preview] = useState(null);
  const [v1PreviewLoading, setV1PreviewLoading] = useState(false);

  const { isOpen: isNewVersionOpen, onOpen: onNewVersionOpen, onOpenChange: onNewVersionOpenChange } = useDisclosure();

  const { data: doc, isLoading: docLoading } = useDocument(docId);
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

  const { data: diffData, isLoading: diffLoading } = useDiff(
    docId,
    prevVersion?.id,
    selectedVersion?.id
  );

  // Render the diff: parse the JSON payload from the server, fetch both document
  // versions as text, then compute and display the unified diff via WASM.
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
        const { fromUrl, toUrl } = payload;

        const [fromResp, toResp] = await Promise.all([fetch(fromUrl), fetch(toUrl)]);
        if (cancelled) return;
        const [fromText, toText] = await Promise.all([fromResp.text(), toResp.text()]);
        if (cancelled) return;

        // Lazy-load WASM
        const wasmMod = await import('../../pkg/diff_wasm.js');
        if (cancelled) return;
        await wasmMod.default();
        const diff = wasmMod.generate_unified_diff_wasm(fromText, toText);
        if (!cancelled) setRenderedDiff(diff);
      } catch (err) {
        if (!cancelled) {
          console.error('[DocumentViewer] diff rendering failed:', err);
          setRenderedDiff(null);
        }
      } finally {
        if (!cancelled) setDiffRendering(false);
      }
    })();

    return () => { cancelled = true; };
  }, [diffData]);

  // When viewing v1 (no previous version), fetch the document content and show
  // the first ~15 lines as a preview since there is no diff to display.
  const isV1 = effectiveIndex === 0 && versions.length > 0;
  const selectedVersionId = selectedVersion?.id;

  useEffect(() => {
    if (!isV1 || !selectedVersionId || !docId) {
      setV1Preview(null);
      setV1PreviewLoading(false);
      return;
    }

    let cancelled = false;
    setV1PreviewLoading(true);
    setV1Preview(null);

    (async () => {
      try {
        const { download_url } = await getVersionDownloadUrl(docId, selectedVersionId);
        const urlStr = String(download_url ?? '');
        if (!urlStr || cancelled) return;
        const resp = await fetch(urlStr);
        if (cancelled) return;
        const text = await resp.text();
        if (!cancelled) setV1Preview(text.split('\n').slice(0, 15).join('\n'));
      } catch (err) {
        console.error('[DocumentViewer] v1 preview failed:', err);
      } finally {
        if (!cancelled) setV1PreviewLoading(false);
      }
    })();

    return () => { cancelled = true; };
  }, [isV1, selectedVersionId, docId]);

  const handleDownload = async () => {
    if (!selectedVersion) return;
    try {
      const { download_url } = await getVersionDownloadUrl(docId, selectedVersion.id);
      const urlStr = String(download_url ?? '');
      if (!urlStr) {
        addToast({
          title: 'Document being prepared',
          description: "The document is being reconstructed — you'll receive a notification when it's ready.",
          color: 'primary',
        });
        return;
      }
      window.open(urlStr, '_blank');
    } catch (err) {
      addToast({ title: 'Download failed', description: err?.message, color: 'danger' });
    }
  };

  const handleRollback = () => {
    if (!selectedVersion) return;
    rollback.mutate({ docId, versionId: selectedVersion.id });
  };

  if (docLoading || versionsLoading) {
    return (
      <div className="flex justify-center items-center py-40">
        <Spinner color="primary" size="lg" />
      </div>
    );
  }

  return (
    <div className="max-w-7xl mx-auto px-6 py-8 w-full z-10 flex-grow flex flex-col relative h-[calc(100vh-80px)]">
      <div className="flex items-center gap-2 text-sm text-zinc-500 mb-6 border-b border-zinc-800 pb-4">
        <button onClick={() => navigate(-1)} className="flex items-center gap-1 hover:text-white transition">
          <ArrowLeft size={16} /> Back
        </button>
        <span>/</span>
        <span className="text-zinc-300 font-semibold">{doc?.name || "Document"}</span>
      </div>

      <div className="bg-zinc-900 border border-zinc-800 rounded-xl p-6 mb-6 flex flex-col lg:flex-row gap-8 items-center justify-between">
        <div className="w-full lg:w-1/2 flex flex-col relative pt-2">
          <div className="flex items-center gap-3 mb-6">
            <History size={16} className="text-lime-500" />
            <span className="text-sm font-semibold text-white">Version Timeline</span>
          </div>
          
          {versions.length > 0 && (
            <Slider 
              step={1} maxValue={versions.length - 1} minValue={0} 
              value={effectiveIndex} onChange={setSliderIndex}
              color="primary" showSteps={true}
              marks={versions.map(v => ({ value: v.value, label: v.label }))}
              className="max-w-md"
            />
          )}
        </div>

        <div className="w-full lg:w-auto flex flex-wrap gap-3 justify-end items-center mt-4 lg:mt-0">
          {selectedVersion && (
            <div className="text-right mr-4 hidden sm:block">
              <div className="text-sm font-bold text-white">Viewing: {selectedVersion.label}</div>
              <div className="text-xs text-zinc-500 truncate max-w-[200px]">{selectedVersion.msg}</div>
            </div>
          )}
          
          <Button variant="bordered" className="border-zinc-700 text-zinc-300" startContent={<Download size={18} />} onPress={handleDownload}>
            Download
          </Button>

          {isLatest ? (
            <Button color="primary" startContent={<UploadCloud size={18} />} onPress={onNewVersionOpen}>
              New Version
            </Button>
          ) : (
            <Button color="warning" variant="flat" startContent={<RotateCcw size={18} />} onPress={handleRollback} isLoading={rollback.isPending}>
              Rollback to {selectedVersion?.label}
            </Button>
          )}
        </div>
      </div>

      {/* Diff Viewer Area */}
      <div className="flex-grow bg-zinc-950 border border-zinc-800 rounded-xl overflow-hidden flex flex-col relative">
        <div className="bg-zinc-900 border-b border-zinc-800 p-3 flex justify-between items-center">
          <div className="text-sm font-medium flex items-center gap-2">
            <span className="bg-zinc-800 px-2 py-0.5 rounded text-xs font-mono text-zinc-300">{prevVersion ? prevVersion.label : 'Initial'}</span>
            <span className="text-zinc-500">&rarr;</span>
            <span className="bg-lime-500/20 border border-lime-500/30 px-2 py-0.5 rounded text-xs font-mono text-lime-400 font-bold">{selectedVersion?.label || '—'}</span>
          </div>
          
          <ButtonGroup size="sm">
            <Button variant={diffMode === 'split' ? "solid" : "bordered"} color={diffMode === 'split' ? "default" : "default"} onPress={() => setDiffMode('split')} startContent={<Columns size={14} />} className={diffMode === 'split' ? "bg-zinc-700" : "border-zinc-700 text-zinc-400"}>Split</Button>
            <Button variant={diffMode === 'unified' ? "solid" : "bordered"} color={diffMode === 'unified' ? "default" : "default"} onPress={() => setDiffMode('unified')} startContent={<AlignLeft size={14} />} className={diffMode === 'unified' ? "bg-zinc-700" : "border-zinc-700 text-zinc-400"}>Unified</Button>
          </ButtonGroup>
        </div>

        <div className="flex-grow overflow-auto font-mono text-sm bg-zinc-950/50">
          {diffLoading || diffRendering || v1PreviewLoading ? (
            <div className="flex justify-center items-center h-full"><Spinner color="primary" /></div>
          ) : renderedDiff ? (
            diffMode === 'split' ? (
              <SplitDiffView diff={renderedDiff} />
            ) : (
              <UnifiedDiffView diff={renderedDiff} />
            )
          ) : v1Preview !== null ? (
            <V1PreviewView content={v1Preview} />
          ) : (
            <div className="flex items-center justify-center h-full text-zinc-600 p-8">
              {versions.length === 0 ? 'No versions available.' : 'Select a version to see the diff.'}
            </div>
          )}
        </div>
      </div>

      <NewVersionModal
        isOpen={isNewVersionOpen}
        onOpenChange={onNewVersionOpenChange}
        docId={docId}
        versions={versionsData?.content || versionsData || []}
      />
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
    <table className="w-full table-fixed border-collapse text-xs">
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
  const leftLines = [];
  const rightLines = [];

  for (const line of lines) {
    if (line.startsWith('-')) {
      leftLines.push(line);
      rightLines.push(null);
    } else if (line.startsWith('+')) {
      leftLines.push(null);
      rightLines.push(line);
    } else {
      leftLines.push(line);
      rightLines.push(line);
    }
  }

  const maxLen = Math.max(leftLines.length, rightLines.length);

  return (
    <table className="w-full table-fixed border-collapse text-xs">
      <tbody>
        {Array.from({ length: maxLen }, (_, i) => {
          const left = leftLines[i] ?? '';
          const right = rightLines[i] ?? '';
          return (
            <tr key={i}>
              <td className={`px-4 py-0.5 whitespace-pre-wrap break-all w-1/2 border-r border-zinc-800 ${left ? lineClass(left) : 'text-zinc-700'}`}>
                {left || ' '}
              </td>
              <td className={`px-4 py-0.5 whitespace-pre-wrap break-all w-1/2 ${right ? lineClass(right) : 'text-zinc-700'}`}>
                {right || ' '}
              </td>
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}

function V1PreviewView({ content }) {
  const lines = content.split('\n');
  return (
    <table className="w-full table-fixed border-collapse text-xs">
      <tbody>
        {lines.map((line, i) => (
          <tr key={i} className="text-emerald-400 bg-emerald-950/40">
            <td className="select-none w-12 text-right pr-4 py-0.5 text-zinc-600 border-r border-zinc-800">{i + 1}</td>
            <td className="px-4 py-0.5 whitespace-pre-wrap break-all">{line || ' '}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
