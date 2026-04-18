import { DropdownMenu, DropdownItem } from "@heroui/react";

export default function UserMenuContent({ email, userId, onOpenPhotoModal, onLogout }) {
  return (
    <DropdownMenu 
      aria-label="User Actions" 
      variant="flat" 
      itemClasses={{ base: "text-white" }} 
      className="text-foreground"
    >
      <DropdownItem 
        key="profile" 
        isReadOnly 
        className="h-16 gap-2 opacity-100 cursor-default data-[hover=true]:bg-transparent data-[hover=true]:text-white"
      >
        <p className="font-bold text-zinc-400 text-xs uppercase">Signed in as</p>
        <p className="font-bold text-lime-400 truncate">{email}</p>
        {userId && (
          <p className="text-xs text-zinc-500 truncate">ID: {userId}</p>
        )}
      </DropdownItem>
      
      <DropdownItem key="update_photo" onPress={onOpenPhotoModal}>
        Update photo
      </DropdownItem>
      
      <DropdownItem key="help_and_feedback">
        Help & Feedback
      </DropdownItem>
      
      <DropdownItem key="logout" color="danger" onPress={onLogout}>
        Log Out
      </DropdownItem>
    </DropdownMenu>
  );
}