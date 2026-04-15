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

  const handleRoleChange = (userId, role) => {
    addUser.mutate({ orgId, data: { user_id: userId, role } });
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
        <Button color="primary" size="sm" startContent={<Plus size={16} />} onPress={onOpen}>
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
          <TableColumn>Role</TableColumn>
          <TableColumn width={100}>Actions</TableColumn>
        </TableHeader>
        <TableBody>
          {users.map((u) => (
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
                  aria-label="Role"
                  size="sm"
                  variant="bordered"
                  selectedKeys={[u.role]}
                  onSelectionChange={(keys) => {
                    const role = [...keys][0];
                    if (role && role !== u.role) handleRoleChange(u.user_id, role);
                  }}
                  classNames={{ trigger: "min-w-[130px] border-zinc-700" }}
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
          ))}
        </TableBody>
      </Table>

      <AddMemberModal isOpen={isOpen} onOpenChange={onOpenChange} onSubmit={handleAddMember} isLoading={addUser.isPending} />
    </div>
  );
}
