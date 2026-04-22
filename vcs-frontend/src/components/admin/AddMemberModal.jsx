import { useState, useEffect } from 'react';
import {
  Modal, ModalContent, ModalHeader, ModalBody, ModalFooter,
  Input, Button, Spinner, Checkbox, CheckboxGroup,
} from "@heroui/react";
import { searchUsers } from '../../api/userApi';

const ROLES = ['ADMIN', 'AUTHOR', 'REVIEWER', 'READER'];

const inputClass = {
  label: "text-zinc-400",
  input: "text-white placeholder:text-zinc-600",
  inputWrapper: "border-zinc-800 bg-zinc-900/50 hover:border-zinc-700 focus-within:!border-lime-500",
};

export default function AddMemberModal({ isOpen, onOpenChange, onSubmit, isLoading }) {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState([]);
  const [searching, setSearching] = useState(false);
  const [selectedUser, setSelectedUser] = useState(null);
  const [selectedRoles, setSelectedRoles] = useState(['READER']);

  useEffect(() => {
    if (!query.trim()) { setResults([]); return; }
    const t = setTimeout(async () => {
      setSearching(true);
      try {
        const res = await searchUsers(query.trim());
        setResults(res || []);
      } catch {
        setResults([]);
      } finally {
        setSearching(false);
      }
    }, 300);
    return () => clearTimeout(t);
  }, [query]);

  const handleSelectUser = (user) => {
    setSelectedUser(user);
    setResults([]);
    setQuery('');
  };

  const handleSubmit = (onClose) => {
    if (!selectedUser || selectedRoles.length === 0) return;
    onSubmit({ user_id: selectedUser.id, roles: selectedRoles }, onClose);
  };

  const handleClose = () => {
    setQuery('');
    setResults([]);
    setSelectedUser(null);
    setSelectedRoles(['READER']);
  };

  return (
    <Modal
      isOpen={isOpen}
      onOpenChange={onOpenChange}
      onClose={handleClose}
      classNames={{
        base: "bg-zinc-950 border border-zinc-800",
        closeButton: "hover:bg-white/5 active:bg-white/10 text-zinc-400",
      }}
    >
      <ModalContent>
        {(onClose) => (
          <>
            <ModalHeader className="text-white font-bold text-xl">Add Member</ModalHeader>
            <ModalBody>
              {!selectedUser ? (
                <div className="relative">
                  <Input
                    label="Search by name or email"
                    placeholder="Type to search users..."
                    value={query}
                    onValueChange={setQuery}
                    variant="bordered"
                    classNames={inputClass}
                    endContent={searching && <Spinner size="sm" color="primary" />}
                    autoFocus
                  />
                  {results.length > 0 && (
                    <div className="absolute z-50 w-full mt-1 bg-zinc-900 border border-zinc-700 rounded-xl shadow-2xl max-h-52 overflow-y-auto">
                      {results.map((u) => (
                        <button
                          key={u.id}
                          type="button"
                          onClick={() => handleSelectUser(u)}
                          className="w-full text-left px-4 py-3 hover:bg-zinc-800 transition-colors flex flex-col border-b border-zinc-800 last:border-0"
                        >
                          <span className="text-white text-sm font-medium">{u.name || 'Unknown'}</span>
                          <span className="text-zinc-400 text-xs">{u.email}</span>
                        </button>
                      ))}
                    </div>
                  )}
                  {query.trim() && !searching && results.length === 0 && (
                    <p className="text-zinc-500 text-xs mt-2 px-1">No users found.</p>
                  )}
                </div>
              ) : (
                <div className="flex flex-col gap-4">
                  <div className="flex items-center gap-3 p-3 bg-zinc-800/50 rounded-xl border border-zinc-700">
                    <div className="w-9 h-9 rounded-full bg-zinc-700 flex items-center justify-center text-sm font-bold text-white shrink-0">
                      {(selectedUser.name || '?')[0].toUpperCase()}
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="text-white text-sm font-semibold truncate">{selectedUser.name}</div>
                      <div className="text-zinc-400 text-xs truncate">{selectedUser.email}</div>
                    </div>
                    <button
                      type="button"
                      onClick={() => setSelectedUser(null)}
                      className="text-zinc-500 hover:text-white text-xs shrink-0 transition-colors"
                    >
                      Change
                    </button>
                  </div>

                  <CheckboxGroup
                    label="Roles"
                    value={selectedRoles}
                    onChange={setSelectedRoles}
                    classNames={{ label: "text-zinc-400 text-sm mb-1" }}
                  >
                    {ROLES.map((r) => (
                      <Checkbox
                        key={r}
                        value={r}
                        classNames={{ label: "text-white text-sm" }}
                      >
                        {r.charAt(0) + r.slice(1).toLowerCase()}
                      </Checkbox>
                    ))}
                  </CheckboxGroup>
                </div>
              )}
            </ModalBody>
            <ModalFooter>
              <Button variant="light" onPress={onClose} color="danger">Cancel</Button>
              <Button
                onPress={() => handleSubmit(onClose)}
                isLoading={isLoading}
                isDisabled={!selectedUser || selectedRoles.length === 0}
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
