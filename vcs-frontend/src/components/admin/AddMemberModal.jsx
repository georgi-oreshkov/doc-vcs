import { useState } from 'react';
import {
  Modal, ModalContent, ModalHeader, ModalBody, ModalFooter,
  Input, Select, SelectItem, Button,
} from "@heroui/react";

const ROLES = ['ADMIN', 'AUTHOR', 'REVIEWER', 'READER'];

export default function AddMemberModal({ isOpen, onOpenChange, onSubmit, isLoading }) {
  const [userId, setUserId] = useState('');
  const [role, setRole] = useState('READER');

  const handleSubmit = (onClose) => {
    if (!userId.trim()) return;
    onSubmit({ user_id: userId.trim(), role }, onClose);
  };

  const handleClose = () => {
    setUserId('');
    setRole('READER');
  };

  return (
    <Modal isOpen={isOpen} onOpenChange={onOpenChange} onClose={handleClose} classNames={{ base: "bg-zinc-900 border border-zinc-800" }}>
      <ModalContent>
        {(onClose) => (
          <>
            <ModalHeader className="text-white">Add Member</ModalHeader>
            <ModalBody>
              <Input
                label="User ID"
                placeholder="Enter user UUID"
                value={userId}
                onValueChange={setUserId}
                variant="bordered"
                classNames={{ inputWrapper: "border-zinc-700" }}
              />
              <Select
                label="Role"
                selectedKeys={[role]}
                onSelectionChange={(keys) => setRole([...keys][0] || 'READER')}
                variant="bordered"
                classNames={{ trigger: "border-zinc-700" }}
              >
                {ROLES.map((r) => (
                  <SelectItem key={r}>
                    {r.charAt(0) + r.slice(1).toLowerCase()}
                  </SelectItem>
                ))}
              </Select>
            </ModalBody>
            <ModalFooter>
              <Button variant="light" onPress={onClose}>Cancel</Button>
              <Button color="primary" onPress={() => handleSubmit(onClose)} isLoading={isLoading} isDisabled={!userId.trim()}>
                Add
              </Button>
            </ModalFooter>
          </>
        )}
      </ModalContent>
    </Modal>
  );
}
