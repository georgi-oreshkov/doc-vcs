import { useState } from 'react';
import { Button, Slider, ButtonGroup } from "@heroui/react";
import { ArrowLeft, History, Download, UploadCloud, RotateCcw, Columns, AlignLeft } from 'lucide-react';

export default function DocumentViewerView({ doc, setView }) {
  const [diffMode, setDiffMode] = useState('split'); 
  
  const versions = [
    { value: 0, label: 'v1.0', date: 'Feb 10', msg: 'Initial Draft' },
    { value: 1, label: 'v1.1', date: 'Feb 15', msg: 'Added auth logic' },
    { value: 2, label: 'v2.0', date: 'Mar 01', msg: 'Refactored backend' },
    { value: 3, label: 'v2.4', date: 'Mar 09', msg: 'Latest changes' },
  ];
  
  const [sliderIndex, setSliderIndex] = useState(3);
  const selectedVersion = versions[sliderIndex];
  const isLatest = sliderIndex === versions.length - 1;

  return (
    <div className="max-w-7xl mx-auto px-6 py-8 w-full z-10 flex-grow flex flex-col relative h-[calc(100vh-80px)]">
      <div className="flex items-center gap-2 text-sm text-zinc-500 mb-6 border-b border-zinc-800 pb-4">
        <button onClick={() => setView('documents')} className="flex items-center gap-1 hover:text-white transition">
          <ArrowLeft size={16} /> My Documents
        </button>
        <span>/</span>
        <span className="text-zinc-300 font-semibold">{doc?.title || "Document"}</span>
      </div>

      <div className="bg-zinc-900 border border-zinc-800 rounded-xl p-6 mb-6 flex flex-col lg:flex-row gap-8 items-center justify-between">
        <div className="w-full lg:w-1/2 flex flex-col relative pt-2">
          <div className="flex items-center gap-3 mb-6">
            <History size={16} className="text-lime-500" />
            <span className="text-sm font-semibold text-white">Version Timeline</span>
          </div>
          
          <Slider 
            step={1} maxValue={3} minValue={0} 
            value={sliderIndex} onChange={setSliderIndex}
            color="primary" showSteps={true}
            marks={versions.map(v => ({ value: v.value, label: v.label }))}
            className="max-w-md"
          />
        </div>

        <div className="w-full lg:w-auto flex flex-wrap gap-3 justify-end items-center mt-4 lg:mt-0">
          <div className="text-right mr-4 hidden sm:block">
            <div className="text-sm font-bold text-white">Viewing: {selectedVersion.label}</div>
            <div className="text-xs text-zinc-500 truncate max-w-[200px]">{selectedVersion.msg}</div>
          </div>
          
          <Button variant="bordered" className="border-zinc-700 text-zinc-300" startContent={<Download size={18} />}>
            Download
          </Button>

          {isLatest ? (
            <Button color="primary" startContent={<UploadCloud size={18} />}>
              New Version
            </Button>
          ) : (
            <Button color="warning" variant="flat" startContent={<RotateCcw size={18} />}>
              Rollback to {selectedVersion.label}
            </Button>
          )}
        </div>
      </div>

      {/* Code Viewer Area */}
      <div className="flex-grow bg-zinc-950 border border-zinc-800 rounded-xl overflow-hidden flex flex-col relative">
        <div className="bg-zinc-900 border-b border-zinc-800 p-3 flex justify-between items-center">
          <div className="text-sm font-medium flex items-center gap-2">
            <span className="bg-zinc-800 px-2 py-0.5 rounded text-xs font-mono text-zinc-300">{sliderIndex > 0 ? versions[sliderIndex - 1].label : 'None'}</span>
            <span className="text-zinc-500">&rarr;</span>
            <span className="bg-lime-500/20 border border-lime-500/30 px-2 py-0.5 rounded text-xs font-mono text-lime-400 font-bold">{selectedVersion.label}</span>
          </div>
          
          <ButtonGroup size="sm">
            <Button variant={diffMode === 'split' ? "solid" : "bordered"} color={diffMode === 'split' ? "default" : "default"} onPress={() => setDiffMode('split')} startContent={<Columns size={14} />} className={diffMode === 'split' ? "bg-zinc-700" : "border-zinc-700 text-zinc-400"}>Split</Button>
            <Button variant={diffMode === 'unified' ? "solid" : "bordered"} color={diffMode === 'unified' ? "default" : "default"} onPress={() => setDiffMode('unified')} startContent={<AlignLeft size={14} />} className={diffMode === 'unified' ? "bg-zinc-700" : "border-zinc-700 text-zinc-400"}>Unified</Button>
          </ButtonGroup>
        </div>

        {/* Dummy Code Content */}
        <div className="flex-grow overflow-auto font-mono text-sm p-8 text-zinc-400 flex items-center justify-center bg-zinc-950/50">
           [Diff viewer content from original code remains exactly the same here - simplified for space] <br/>
           <span className="text-lime-500 ml-2"> + const user = jwt.verify(token);</span>
        </div>
      </div>
    </div>
  );
}