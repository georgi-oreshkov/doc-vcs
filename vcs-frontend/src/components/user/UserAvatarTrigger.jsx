import { DropdownTrigger, User } from "@heroui/react";

export default function UserAvatarTrigger({ photoUrl, displayName, displayRole }) {
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
          description: "text-lime-400 font-bold text-xs uppercase tracking-wider" 
        }}
        description={displayRole}
        name={displayName}
      />
    </DropdownTrigger>
  );
}