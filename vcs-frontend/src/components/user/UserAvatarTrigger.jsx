import { DropdownTrigger, User } from "@heroui/react";

const ROLE_COLORS = {
  ADMIN: 'text-lime-400',
  AUTHOR: 'text-blue-400',
  REVIEWER: 'text-amber-400',
  READER: 'text-zinc-400',
};

export default function UserAvatarTrigger({ photoUrl, displayName, displayRole, roleKey }) {
  const roleColorClass = roleKey ? (ROLE_COLORS[roleKey] || 'text-zinc-400') : 'text-zinc-400';

  return (
    <DropdownTrigger>
      <User
        as="button"
        avatarProps={{
          isBordered: true,
          color: "primary",
          src: photoUrl,
        }}
        className="transition-transform hover:opacity-80"
        classNames={{
          name: "text-white font-semibold text-sm",
          description: `${roleColorClass} font-bold text-xs uppercase tracking-wider`
        }}
        description={displayRole}
        name={displayName}
      />
    </DropdownTrigger>
  );
}