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
    <Modal 
      isOpen={isOpen} 
      onOpenChange={onOpenChange} 
      onClose={handleClose} 
      classNames={{ 
        base: "bg-zinc-950 border border-zinc-800", 
        closeButton: "hover:bg-white/5 active:bg-white/10 text-zinc-400" 
      }}
    >
      <ModalContent>
        {(onClose) => (
          <>
            <ModalHeader className="text-white font-bold text-xl">Add Member</ModalHeader>
            <ModalBody>
              <Input
                label="User ID"
                placeholder="Enter user UUID"
                value={userId}
                onValueChange={setUserId}
                variant="bordered"
                classNames={{ 
                  label: "text-zinc-400",
                  input: "text-white placeholder:text-zinc-600",
                  inputWrapper: "border-zinc-800 bg-zinc-900/50 hover:border-zinc-700 focus-within:!border-lime-500" 
                }}
              />
              <Select
                label="Role"
                selectedKeys={[role]}
                onSelectionChange={(keys) => setRole([...keys][0] || 'READER')}
                variant="bordered"
                classNames={{ 
                  label: "text-zinc-400",
                  // 1. Force the selected value text to be white
                  value: "!text-white group-data-[has-value=true]:text-white",
                  // 2. Force the trigger box text to be white
                  trigger: "border-zinc-800 bg-zinc-900/50 hover:border-zinc-700 data-[open=true]:!border-lime-500 !text-white",
                  popoverContent: "bg-zinc-900 border border-zinc-800"
                }}
              >
                {ROLES.map((r) => (
                  <SelectItem 
                    key={r} 
                    textValue={r.charAt(0) + r.slice(1).toLowerCase()} 
                    // 3. Force the individual unselected options to be white
                    className="!text-white data-[hover=true]:bg-zinc-800 data-[hover=true]:!text-lime-400"
                  >
                    {r.charAt(0) + r.slice(1).toLowerCase()}
                  </SelectItem>
                ))}
              </Select>
            </ModalBody>
            <ModalFooter>
              <Button 
                variant="light" 
                onPress={onClose}
                className="text-red-400 hover:text-red"
              >
                Cancel
              </Button>
              <Button 
                onPress={() => handleSubmit(onClose)} 
                isLoading={isLoading} 
                isDisabled={!userId.trim()}
                // Overrides the default blue with your custom lime theme
                className="bg-lime-600 text-black font-bold hover:bg-lime-500 disabled:bg-zinc-800 disabled:text-zinc-600 transition-colors"
              >
                Add Member
              </Button>
            </ModalFooter>
          </>
        )}
      </ModalContent>
    </Modal>
  );
}