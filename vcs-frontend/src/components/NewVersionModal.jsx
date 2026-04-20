import { useRef, useState } from 'react';
import {
  Modal,
  ModalContent,
  ModalHeader,
  ModalBody,
  ModalFooter,
  Button,
  Switch,
  addToast,
} from '@heroui/react';
import { UploadCloud, FileText, X, CheckCircle, Loader } from 'lucide-react';
import { useUploadVersion } from '../hooks/useVersions';

const STEP_LABELS = {
  idle: null,
  fetching_base: 'Fetching previous version…',
  computing_diff: 'Computing diff…',
  uploading: 'Uploading to storage…',
  done: 'Done!',
};

export default function NewVersionModal({ isOpen, onOpenChange, docId, versions }) {
  const [file, setFile] = useState(null);
  const [isDraft, setIsDraft] = useState(false);
  const fileInputRef = useRef(null);

  const { mutate: uploadVersion, isPending, uploadStep, setUploadStep } = useUploadVersion(docId, versions);

  const handleFileChange = (e) => {
    if (e.target.files?.[0]) setFile(e.target.files[0]);
  };

  const handleClose = (onClose) => {
    if (isPending) return;
    setFile(null);
    setIsDraft(false);
    setUploadStep('idle');
    onClose();
  };

  const handleUpload = (onClose) => {
    if (!file) return;
    uploadVersion(
      { file, isDraft },
      {
        onSuccess: () => {
          addToast({
            title: 'Version uploaded',
            description: isDraft ? 'Draft saved successfully.' : 'Version submitted for review.',
            color: 'success',
          });
          setTimeout(() => handleClose(onClose), 800);
        },
        onError: (err) => {
          addToast({
            title: 'Upload failed',
            description: err?.message ?? 'An error occurred during upload.',
            color: 'danger',
          });
        },
      }
    );
  };

  const stepLabel = STEP_LABELS[uploadStep];

  return (
    <Modal
      isOpen={isOpen}
      onOpenChange={onOpenChange}
      isDismissable={!isPending}
      hideCloseButton={isPending}
      backdrop="blur"
      classNames={{
        base: 'bg-zinc-950 border border-zinc-800',
        header: 'border-b border-zinc-800',
        footer: 'border-t border-zinc-800',
      }}
    >
      <ModalContent>
        {(onClose) => (
          <>
            <ModalHeader className="text-white">Upload New Version</ModalHeader>

            <ModalBody className="py-6 gap-6">
              {/* File drop zone */}
              <div>
                <p className="text-sm text-zinc-400 mb-2">File</p>
                {!file ? (
                  <div
                    onClick={() => fileInputRef.current?.click()}
                    className="border-2 border-dashed border-zinc-700 hover:border-zinc-500 hover:bg-zinc-900/50 transition-colors rounded-xl p-8 flex flex-col items-center justify-center cursor-pointer gap-2"
                  >
                    <UploadCloud className="text-zinc-500" size={32} />
                    <p className="text-sm text-zinc-300">Click to browse or drag and drop</p>
                    <p className="text-xs text-zinc-500">Any file type up to 50 MB</p>
                  </div>
                ) : (
                  <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-4 flex items-center justify-between">
                    <div className="flex items-center gap-3 overflow-hidden">
                      <FileText className="text-blue-500 shrink-0" size={24} />
                      <div className="truncate">
                        <p className="text-sm text-white truncate">{file.name}</p>
                        <p className="text-xs text-zinc-500">{(file.size / 1024 / 1024).toFixed(2)} MB</p>
                      </div>
                    </div>
                    {!isPending && (
                      <Button isIconOnly variant="light" size="sm" onPress={() => setFile(null)}>
                        <X className="text-zinc-400 hover:text-red-400" size={18} />
                      </Button>
                    )}
                  </div>
                )}
                <input type="file" ref={fileInputRef} className="hidden" onChange={handleFileChange} />
              </div>

              {/* Draft toggle */}
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-sm text-zinc-300">Save as draft</p>
                  <p className="text-xs text-zinc-500">Drafts are not submitted for review</p>
                </div>
                <Switch
                  isSelected={isDraft}
                  onValueChange={setIsDraft}
                  isDisabled={isPending}
                  color="primary"
                />
              </div>

              {/* Progress indicator */}
              {isPending && stepLabel && (
                <div className="flex items-center gap-3 bg-zinc-900 border border-zinc-800 rounded-lg px-4 py-3">
                  {uploadStep === 'done' ? (
                    <CheckCircle size={18} className="text-lime-500 shrink-0" />
                  ) : (
                    <Loader size={18} className="text-lime-400 shrink-0 animate-spin" />
                  )}
                  <p className="text-sm text-zinc-300">{stepLabel}</p>
                </div>
              )}
            </ModalBody>

            <ModalFooter>
              <Button
                color="danger"
                variant="light"
                onPress={() => handleClose(onClose)}
                isDisabled={isPending}
              >
                Cancel
              </Button>
              <Button
                className="bg-lime-600 text-black font-bold hover:bg-lime-500 disabled:bg-zinc-800 disabled:text-zinc-600 transition-colors"
                onPress={() => handleUpload(onClose)}
                isDisabled={!file || isPending}
                isLoading={isPending}
              >
                {isDraft ? 'Save Draft' : 'Submit Version'}
              </Button>
            </ModalFooter>
          </>
        )}
      </ModalContent>
    </Modal>
  );
}
