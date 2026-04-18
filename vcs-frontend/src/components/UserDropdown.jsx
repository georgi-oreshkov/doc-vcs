import { useAuth } from 'react-oidc-context';
import { Dropdown, useDisclosure } from "@heroui/react";
import { useOrg } from '../context/OrgContext';

import UserAvatarTrigger from "./user/UserAvatarTrigger";
import UserMenuContent from "./user/UserMenuContent";
import UpdatePhoto from "./UpdatePhoto";

const ROLE_LABELS = {
  ADMIN: 'Admin',
  AUTHOR: 'Author',
  REVIEWER: 'Reviewer',
  READER: 'Reader',
};

export default function UserDropdown() {
  const auth = useAuth();
  const { activeRole } = useOrg();
  const { isOpen, onOpen, onOpenChange } = useDisclosure();

  // --- 1. Data Extraction & Formatting ---
  const profile = auth.user?.profile;
  const firstName = profile?.given_name || "";
  const lastName = profile?.family_name || "";
  
  const displayName = [firstName, lastName].join(" ").trim() 
    || profile?.name 
    || profile?.preferred_username 
    || "User";
    
  const userEmail = profile?.email || "No email provided";
  const photoUrl = profile?.picture || `https://i.pravatar.cc/150?u=${profile?.sub || 'default'}`;

  // --- 2. Role from org context (null when not inside an org) ---
  const displayRole = activeRole ? ROLE_LABELS[activeRole] || activeRole : null;

  // --- 3. Actions ---
  const handleLogout = () => auth.signoutRedirect();

  return (
    <>
      <Dropdown 
        placement="bottom-end" 
        classNames={{ content: "bg-black border border-zinc-800" }}
      >
        <UserAvatarTrigger 
          photoUrl={photoUrl} 
          displayName={displayName} 
          displayRole={displayRole}
          roleKey={activeRole}
        />
        
        <UserMenuContent 
          email={userEmail} 
          userId={profile?.sub}
          onOpenPhotoModal={onOpen} 
          onLogout={handleLogout} 
        />
      </Dropdown>
      
      <UpdatePhoto
        isOpen={isOpen} 
        onOpenChange={onOpenChange} 
        currentPhotoUrl={photoUrl} 
      />
    </>
  );
}