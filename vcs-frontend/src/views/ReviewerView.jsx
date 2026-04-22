import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Check } from 'lucide-react';
import { Spinner, Modal, ModalContent, ModalHeader, ModalBody, ModalFooter, Button, Textarea } from "@heroui/react";
import ApprovalCard from '../components/ApprovalCard';
import { usePendingReviewVersions, useApproveVersion, useRejectVersion } from '../hooks/useVersions';

export default function ReviewerView() {
  const navigate = useNavigate();
  const { data: items = [], isLoading } = usePendingReviewVersions();
  const approveVersion = useApproveVersion();
  const rejectVersion = useRejectVersion();

  const [rejectTarget, setRejectTarget] = useState(null); // { docId, versionId }
  const [rejectReason, setRejectReason] = useState('');

  const handleApprove = (req) => {
    approveVersion.mutate({ docId: req.doc_id, versionId: req.id });
  };

  const handleRejectOpen = (req) => {
    setRejectTarget({ docId: req.doc_id, versionId: req.id });
    setRejectReason('');
  };

  const handleRejectConfirm = () => {
    if (!rejectTarget) return;
    rejectVersion.mutate({
      docId: rejectTarget.docId,
      versionId: rejectTarget.versionId,
      data: rejectReason.trim() ? { reason: rejectReason.trim() } : {},
    });
    setRejectTarget(null);
  };

  // Navigates directly to the document for review
  const handleViewDocument = (req) => {
    if (req.org_id && req.doc_id) {
      navigate(`/organizations/${req.org_id}/documents/${req.doc_id}`);
    } else {
      navigate(`/documents/${req.doc_id}`);
    }
  };

  // Map API shape → ApprovalCard expected shape
  const cards = items.map((item) => ({
    id: item.version_id,
    doc_id: item.doc_id,
    title: item.doc_name,
    author: item.author_id,
    date: item.created_at ? new Date(item.created_at).toLocaleDateString() : '—',
    version: `v${item.version_number}`,
  }));

  return (
    <div className="max-w-7xl mx-auto px-6 py-12 w-full z-10 flex-grow">
      <div className="mb-10">
        <h1 className="text-3xl font-bold text-white mb-2">Pending Approvals</h1>
        <p className="text-zinc-400 text-sm">Review and approve document changes requested by your team.</p>
      </div>

      {isLoading ? (
        <div className="flex justify-center py-20"><Spinner color="primary" size="lg" /></div>
      ) : cards.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-20 text-zinc-500 bg-zinc-900/30 rounded-2xl border border-zinc-800/50">
          <Check size={48} className="mb-4 text-lime-500/50" />
          <p className="text-lg text-white">All caught up!</p>
          <p className="text-sm">You have no pending versions to review.</p>
        </div>
      ) : (
        <div className="flex flex-col gap-4">
          {cards.map(req => (
            <ApprovalCard
              key={req.id}
              req={req}
              onApprove={handleApprove}
              onReject={handleRejectOpen}
              onView={handleViewDocument}
            />
          ))}
        </div>
      )}

      {/* Reject reason modal */}
      <Modal isOpen={!!rejectTarget} onOpenChange={(open) => { if (!open) setRejectTarget(null); }}>
        <ModalContent className="bg-zinc-900 border border-zinc-800">
          <ModalHeader className="text-white">Reject Version</ModalHeader>
          <ModalBody>
            <Textarea
              label="Reason (optional)"
              placeholder="Explain why this version is being rejected..."
              value={rejectReason}
              onValueChange={setRejectReason}
              classNames={{ input: 'text-white', label: 'text-zinc-400' }}
              minRows={3}
            />
          </ModalBody>
          <ModalFooter>
            <Button variant="flat" onPress={() => setRejectTarget(null)}>Cancel</Button>
            <Button
              color="danger"
              onPress={handleRejectConfirm}
              isLoading={rejectVersion.isPending}
            >
              Reject
            </Button>
          </ModalFooter>
        </ModalContent>
      </Modal>
    </div>
  );
}