import { useState } from 'react';
import {
  Table, TableHeader, TableColumn, TableBody, TableRow, TableCell,
  Button, Chip, Select, SelectItem, Spinner, useDisclosure, User,
} from "@heroui/react";
import { Plus, Trash2 } from 'lucide-react';
import { useOrgUsers, useAddOrgUser, useRemoveOrgUser } from '../../hooks/useOrganizations';
import AddMemberModal from './AddMemberModal';

const ROLES = ['ADMIN', 'AUTHOR', 'REVIEWER', 'READER'];
const ROLE_COLORS = { ADMIN: 'primary', AUTHOR: 'secondary', REVIEWER: 'warning', READER: 'default' };

export default function AdminMembersTab({ orgId }) {
  const { data: users = [], isLoading } = useOrgUsers(orgId);
  const addUser = useAddOrgUser();
  const removeUser = useRemoveOrgUser();
  const { isOpen, onOpen, onOpenChange } = useDisclosure();
  const [removingId, setRemovingId] = useState(null);

  const handleRolesChange = (userId, roles) => {
    addUser.mutate({ orgId, data: { user_id: userId, roles } });
  };

  const handleRemove = (userId) => {
    setRemovingId(userId);
    removeUser.mutate({ orgId, userId }, {
      onSettled: () => setRemovingId(null),
    });
  };

  const handleAddMember = (data, onClose) => {
    addUser.mutate({ orgId, data }, { onSuccess: onClose });
  };

  if (isLoading) {
    return <div className="flex justify-center py-12"><Spinner size="lg" color="primary" /></div>;
  }

  return (
    <div className="pt-6">
      <div className="flex justify-between items-center mb-6">
        <p className="text-zinc-400 text-sm">{users.length} member{users.length !== 1 ? 's' : ''}</p>
        <Button
          className="bg-lime-600 text-black font-bold hover:bg-lime-500 disabled:bg-zinc-800 disabled:text-zinc-600 transition-colors"
          size="sm"
          startContent={<Plus size={16} />}
          onPress={onOpen}
        >
          Add Member
        </Button>
      </div>

      <Table
        aria-label="Organization members"
        classNames={{
          wrapper: "bg-zinc-900/50 border border-zinc-800 rounded-xl",
          th: "bg-zinc-800/50 text-zinc-400 text-xs uppercase",
          td: "text-white",
        }}
      >
        <TableHeader>
          <TableColumn>User</TableColumn>
          <TableColumn>Roles</TableColumn>
          <TableColumn width={100}>Actions</TableColumn>
        </TableHeader>
        <TableBody>
          {users.map((u) => {
            const currentRoles = Array.isArray(u.roles) ? u.roles : (u.role ? [u.role] : []);
            return (
              <TableRow key={u.user_id}>
                <TableCell>
                  <User
                    name={u.name || 'Unknown'}
                    description={u.email || u.user_id}
                    avatarProps={{
                      name: (u.name || '?')[0],
                      size: 'sm',
                      classNames: { base: 'bg-zinc-700' },
                    }}
                  />
                </TableCell>
                <TableCell>
                  <Select
                    aria-label="Roles"
                    size="sm"
                    variant="bordered"
                    selectionMode="multiple"
                    selectedKeys={new Set(currentRoles)}
                    onSelectionChange={(keys) => {
                      const roles = [...keys];
                      if (roles.length > 0) handleRolesChange(u.user_id, roles);
                    }}
                    classNames={{ trigger: "min-w-[160px] border-zinc-700" }}
                    renderValue={(items) => (
                      <div className="flex flex-wrap gap-1">
                        {items.map((item) => (
                          <Chip
                            key={item.key}
                            size="sm"
                            color={ROLE_COLORS[item.key] || 'default'}
                            variant="flat"
                          >
                            {item.key.charAt(0) + item.key.slice(1).toLowerCase()}
                          </Chip>
                        ))}
                      </div>
                    )}
                  >
                    {ROLES.map((r) => (
                      <SelectItem key={r}>
                        {r.charAt(0) + r.slice(1).toLowerCase()}
                      </SelectItem>
                    ))}
                  </Select>
                </TableCell>
                <TableCell>
                  <Button
                    isIconOnly
                    size="sm"
                    variant="light"
                    color="danger"
                    isLoading={removingId === u.user_id}
                    onPress={() => handleRemove(u.user_id)}
                    aria-label="Remove member"
                  >
                    <Trash2 size={16} />
                  </Button>
                </TableCell>
              </TableRow>
            );
          })}
        </TableBody>
      </Table>

      <AddMemberModal isOpen={isOpen} onOpenChange={onOpenChange} onSubmit={handleAddMember} isLoading={addUser.isPending} />
    </div>
  );
}
