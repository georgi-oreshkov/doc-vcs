import { DropdownMenu, DropdownItem } from "@heroui/react";
import { useNavigate } from "react-router-dom";

export default function UserMenuContent({ email, userId, onOpenPhotoModal, onLogout }) {
  // Brings in the router so we can navigate to the new page
  const navigate = useNavigate();

  return (
    <DropdownMenu 
      aria-label="User Actions" 
      variant="flat" 
      // Upgraded base styling for smooth, dark hover states
      itemClasses={{ 
        base: "text-zinc-300 data-[hover=true]:text-white data-[hover=true]:bg-zinc-900 transition-colors rounded-md",
      }} 
    >
      {/* Read-only profile header */}
      <DropdownItem 
        key="profile" 
        isReadOnly 
        className="h-16 gap-2 opacity-100 cursor-default data-[hover=true]:bg-transparent"
      >
        <p className="font-bold text-zinc-400 text-xs uppercase">Signed in as</p>
        {/* Adjusted to lime-500 to match the rest of your app's accents */}
        <p className="font-bold text-lime-500 truncate">{email}</p>
        {userId && (
          <p className="text-xs text-zinc-500 truncate">ID: {userId}</p>
        )}
      </DropdownItem>
      
      {/* The New Notifications Link */}
      <DropdownItem 
        key="notifications" 
        onPress={() => navigate('/notifications')}
      >
        Notifications
      </DropdownItem>
      
      <DropdownItem key="update_photo" onPress={onOpenPhotoModal}>
        Update photo
      </DropdownItem>
      
      <DropdownItem key="help_and_feedback">
        Help & Feedback
      </DropdownItem>
      
      {/* Styled logout button with subtle red hover accents */}
      <DropdownItem 
        key="logout" 
        color="danger" 
        onPress={onLogout}
        className="text-red-400 data-[hover=true]:text-red-300 data-[hover=true]:bg-red-500/10"
      >
        Log Out
      </DropdownItem>
    </DropdownMenu>
  );
}