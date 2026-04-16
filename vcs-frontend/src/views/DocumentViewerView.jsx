import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Button, Slider, ButtonGroup, Spinner } from "@heroui/react";
import { ArrowLeft, ArrowRight, History, Download, UploadCloud, RotateCcw, Columns, AlignLeft } from 'lucide-react';
import { useDocument } from '../hooks/useDocuments';
import { useVersions, useDiff, useVersionDownloadUrl, useRollbackVersion, useCreateVersion } from '../hooks/useVersions';
import { formatVersionNumber } from '../api/transforms';

export default function DocumentViewerView() {
  const { docId } = useParams();
  const navigate = useNavigate();
  const [diffMode, setDiffMode] = useState('split'); 

  const { data: doc, isLoading: docLoading } = useDocument(docId);
  const { data: versionsData, isLoading: versionsLoading } = useVersions(docId, { page: 0, size: 100 });
  const rollback = useRollbackVersion();
  const downloadQuery = useVersionDownloadUrl(docId, null);

  const versions = (versionsData?.content || versionsData || []).map((v, i) => ({
    ...v,
    value: i,
    label: formatVersionNumber(v.version_number),
    msg: `Version ${v.version_number}`,
    date: v.created_at ? new Date(v.created_at).toLocaleDateString('en-US', { month: 'short', day: '2-digit' }) : '',
  }));

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

  const handleDownload = async () => {
    if (!selectedVersion) return;
    const { getVersionDownloadUrl } = await import('../api/versionsApi');
    const { download_url } = await getVersionDownloadUrl(docId, selectedVersion.id);
    window.open(download_url, '_blank');
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
            <Button color="primary" startContent={<UploadCloud size={18} />}>
              New Version
            </Button>
          ) : (
            <Button color="warning" variant="flat" startContent={<RotateCcw size={18} />} onPress={handleRollback} isLoading={rollback.isPending}>
              Rollback to {selectedVersion?.label}
            </Button>
          )}
        </div>
      </div>

      {/* Code Viewer Area */}
      <div className="flex-grow bg-zinc-950 border border-zinc-800 rounded-xl overflow-hidden flex flex-col relative">
        <div className="bg-zinc-900 border-b border-zinc-800 p-3 flex justify-between items-center">
          <div className="text-sm font-medium flex items-center gap-2">
            <span className="bg-zinc-800 px-2 py-0.5 rounded text-xs font-mono text-zinc-300">{prevVersion ? prevVersion.label : 'None'}</span>
            <span className="text-zinc-500">&rarr;</span>
            <span className="bg-lime-500/20 border border-lime-500/30 px-2 py-0.5 rounded text-xs font-mono text-lime-400 font-bold">{selectedVersion?.label || '—'}</span>
          </div>
          
          <ButtonGroup size="sm">
            <Button variant={diffMode === 'split' ? "solid" : "bordered"} color={diffMode === 'split' ? "default" : "default"} onPress={() => setDiffMode('split')} startContent={<Columns size={14} />} className={diffMode === 'split' ? "bg-zinc-700" : "border-zinc-700 text-zinc-400"}>Split</Button>
            <Button variant={diffMode === 'unified' ? "solid" : "bordered"} color={diffMode === 'unified' ? "default" : "default"} onPress={() => setDiffMode('unified')} startContent={<AlignLeft size={14} />} className={diffMode === 'unified' ? "bg-zinc-700" : "border-zinc-700 text-zinc-400"}>Unified</Button>
          </ButtonGroup>
        </div>

        <div className="flex-grow overflow-auto font-mono text-sm p-8 text-zinc-400 bg-zinc-950/50">
          {diffLoading ? (
            <div className="flex justify-center items-center h-full"><Spinner color="primary" /></div>
          ) : diffData?.diff ? (
            <pre className="whitespace-pre-wrap">{diffData.diff}</pre>
          ) : (
            <div className="flex items-center justify-center h-full text-zinc-600">
              {versions.length === 0 ? 'No versions available.' : 'Select a version to see the diff.'}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}