import { useState, useEffect } from 'react';
import { Modal, ModalContent, ModalHeader, ModalBody, ModalFooter, Button, Input } from "@heroui/react";

export default function OrganizationModal({ isOpen, onOpenChange, editingOrg, onSave, isSaving }) {
  const isEditing = !!editingOrg;
  const [name, setName] = useState('');

  useEffect(() => {
    setName(editingOrg?.name || '');
  }, [editingOrg]);

  return (
    <Modal 
      isOpen={isOpen} 
      onOpenChange={onOpenChange} 
      placement="center"
      classNames={{
        // Reverting to the dark zinc base
        base: "bg-zinc-950 border border-zinc-800",
        closeButton: "hover:bg-white/5 active:bg-white/10 text-zinc-400"
      }}
    >
      <ModalContent>
        {(onClose) => (
          <>
            <ModalHeader className="text-white font-bold text-xl">
              {isEditing ? "Organization Settings" : "Create New Organization"}
            </ModalHeader>
            <ModalBody className="py-6">
              <Input 
                label="Organization Name" 
                placeholder="e.g. OpenSource Labs" 
                value={name}
                onChange={(e) => setName(e.target.value)}
                variant="bordered" 
                classNames={{ 
                  label: "text-zinc-400",
                  input: "text-white placeholder:text-zinc-600",
                  // Reverting to the lime green focus ring
                  inputWrapper: "border-zinc-800 bg-zinc-900/50 hover:border-zinc-700 focus-within:!border-lime-500" 
                }}
              />
            </ModalBody>
            <ModalFooter>
              <Button 
                variant="light" 
                onPress={onClose}
                className="text-zinc-400 hover:text-white"
              >
                Cancel
              </Button>
              <Button 
                onPress={() => onSave({ name }, onClose)}
                isDisabled={!name.trim()}
                isLoading={isSaving}
                // Reverting to the high-contrast lime primary button
                className="bg-lime-600 text-black font-bold hover:bg-lime-500 disabled:bg-zinc-800 disabled:text-zinc-600 transition-colors"
              >
                {isEditing ? "Save Changes" : "Create"}
              </Button>
            </ModalFooter>
          </>
        )}
      </ModalContent>
    </Modal>
  );
}