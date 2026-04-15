import { useState } from 'react';
import {
  Input, Button, Divider,
  Modal, ModalContent, ModalHeader, ModalBody, ModalFooter,
  useDisclosure,
} from "@heroui/react";
import { useUpdateOrganization, useDeleteOrganization } from '../../hooks/useOrganizations';

export default function AdminSettingsTab({ orgId, org, onDeleted }) {
  const updateOrg = useUpdateOrganization();
  const deleteOrg = useDeleteOrganization();
  const { isOpen, onOpen, onOpenChange } = useDisclosure();
  const [name, setName] = useState(org?.name || '');

  const handleSave = () => {
    if (!name.trim() || name.trim() === org?.name) return;
    updateOrg.mutate({ orgId, data: { name: name.trim() } });
  };

  const handleDelete = (onClose) => {
    deleteOrg.mutate(orgId, {
      onSuccess: () => {
        onClose();
        onDeleted();
      },
    });
  };

  return (
    <div className="pt-6 max-w-xl">
      <h3 className="text-lg font-semibold text-white mb-4">General</h3>
      <div className="flex gap-3 mb-2">
        <Input
          label="Organization Name"
          value={name}
          onValueChange={setName}
          variant="bordered"
          classNames={{ inputWrapper: "border-zinc-700" }}
        />
        <Button
          color="primary"
          onPress={handleSave}
          isLoading={updateOrg.isPending}
          isDisabled={!name.trim() || name.trim() === org?.name}
          className="self-end"
        >
          Save
        </Button>
      </div>

      <Divider className="my-10 bg-zinc-800" />

      <h3 className="text-lg font-semibold text-red-400 mb-2">Danger Zone</h3>
      <p className="text-zinc-400 text-sm mb-4">
        Deleting this organization is permanent. All documents, versions, and memberships will be removed.
      </p>
      <Button color="danger" variant="bordered" onPress={onOpen}>
        Delete Organization
      </Button>

      <Modal isOpen={isOpen} onOpenChange={onOpenChange} classNames={{ base: "bg-zinc-900 border border-zinc-800" }}>
        <ModalContent>
          {(onClose) => (
            <>
              <ModalHeader className="text-red-400">Confirm Deletion</ModalHeader>
              <ModalBody>
                <p className="text-zinc-300">
                  Are you sure you want to delete <strong>{org?.name}</strong>? This action cannot be undone.
                </p>
              </ModalBody>
              <ModalFooter>
                <Button variant="light" onPress={onClose}>Cancel</Button>
                <Button color="danger" onPress={() => handleDelete(onClose)} isLoading={deleteOrg.isPending}>
                  Delete
                </Button>
              </ModalFooter>
            </>
          )}
        </ModalContent>
      </Modal>
    </div>
  );
}
