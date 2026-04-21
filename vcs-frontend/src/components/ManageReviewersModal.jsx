import { useState, useEffect } from 'react';
import {
  Modal,
  ModalContent,
  ModalHeader,
  ModalBody,
  ModalFooter,
  Button,
  Checkbox,
} from '@heroui/react';
import { useUpdateDocument } from '../hooks/useDocuments';

export default function ManageReviewersModal({
  isOpen,
  onOpenChange,
  docId,
  orgUsers = [],
  currentReviewerIds = [],
}) {
  const [selectedIds, setSelectedIds] = useState([]);
  const updateDoc = useUpdateDocument();

  const reviewers = orgUsers.filter((u) => u.role === 'REVIEWER');

  // Sync selection when modal opens or reviewerIds change
  useEffect(() => {
    if (isOpen) {
      setSelectedIds(currentReviewerIds);
    }
  }, [isOpen, currentReviewerIds]);

  const toggleId = (id) => {
    setSelectedIds((prev) =>
      prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]
    );
  };

  const handleSave = (onClose) => {
    if (!docId) return;
    updateDoc.mutate(
      { docId, data: { reviewer_ids: selectedIds } },
      { onSuccess: onClose }
    );
  };

  return (
    <Modal
      isOpen={isOpen}
      onOpenChange={onOpenChange}
      classNames={{
        base: 'bg-zinc-950 border border-zinc-800',
        closeButton: 'hover:bg-white/5 active:bg-white/10 text-zinc-400',
      }}
    >
      <ModalContent>
        {(onClose) => (
          <>
            <ModalHeader className="text-white font-bold text-xl">Manage Reviewers</ModalHeader>
            <ModalBody>
              {reviewers.length === 0 ? (
                <p className="text-zinc-400 text-sm">
                  No users with the Reviewer role are in this organization.
                </p>
              ) : (
                <div className="flex flex-col gap-3">
                  {reviewers.map((u) => (
                    <Checkbox
                      key={u.user_id}
                      isSelected={selectedIds.includes(u.user_id)}
                      onValueChange={() => toggleId(u.user_id)}
                      classNames={{
                        label: 'text-white text-sm',
                        wrapper:
                          'border-zinc-700 before:border-zinc-700 group-data-[selected=true]:before:border-lime-500 group-data-[selected=true]:bg-lime-500',
                      }}
                    >
                      {u.name || u.email || u.user_id}
                    </Checkbox>
                  ))}
                </div>
              )}
            </ModalBody>
            <ModalFooter>
              <Button variant="light" onPress={onClose} className="text-zinc-400">
                Cancel
              </Button>
              <Button
                className="bg-lime-600 text-black font-bold hover:bg-lime-500"
                onPress={() => handleSave(onClose)}
                isLoading={updateDoc.isPending}
                isDisabled={reviewers.length === 0}
              >
                Save
              </Button>
            </ModalFooter>
          </>
        )}
      </ModalContent>
    </Modal>
  );
}
