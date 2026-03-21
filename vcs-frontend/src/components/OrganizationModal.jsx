import { Modal, ModalContent, ModalHeader, ModalBody, ModalFooter, Button, Input, Textarea } from "@heroui/react";

export default function OrganizationModal({ isOpen, onOpenChange, editingOrg }) {
  const isEditing = !!editingOrg;

  return (
    <Modal 
      isOpen={isOpen} 
      onOpenChange={onOpenChange} 
      placement="center"
      classNames={{
        base: "bg-zinc-950 border border-zinc-800",
        header: "border-b border-zinc-800 text-white",
        footer: "border-t border-zinc-800",
        closeButton: "hover:bg-zinc-800 active:bg-zinc-700 text-white",
      }}
    >
      <ModalContent>
        {(onClose) => (
          <>
            <ModalHeader className="flex flex-col gap-1">
              {isEditing ? "Organization Settings" : "Create New Organization"}
            </ModalHeader>
            <ModalBody className="py-6">
              <Input 
                label="Organization Name" 
                placeholder="e.g. OpenSource Labs" 
                defaultValue={editingOrg?.name || ''} 
                variant="bordered" 
                classNames={{ inputWrapper: "border-zinc-700 text-white" }}
              />
              <Textarea 
                label="Description" 
                placeholder="Brief description of the workspace..." 
                variant="bordered" 
                classNames={{ inputWrapper: "border-zinc-700 text-white" }}
              />
            </ModalBody>
            <ModalFooter>
              <Button color="danger" variant="light" onPress={onClose}>
                Cancel
              </Button>
              <Button color="primary" onPress={onClose}>
                {isEditing ? "Save Changes" : "Create"}
              </Button>
            </ModalFooter>
          </>
        )}
      </ModalContent>
    </Modal>
  );
}