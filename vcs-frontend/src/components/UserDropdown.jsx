import { Dropdown, DropdownTrigger, DropdownMenu, DropdownItem, User } from "@heroui/react";

export default function UserDropdown({ setView }) {
  return (
    <Dropdown 
        placement="bottom-end" 
        classNames={{
        content: "bg-black border border-zinc-800",
        }}
    >
      <DropdownTrigger>
        <User
          as="button"
          avatarProps={{
            isBordered: true,
            color: "primary",
            src: "https://i.pravatar.cc/150?u=a042581f4e29026024d",
          }}
          className="transition-transform text-white hover:opacity-80"
          description="reviewer"
          name="Tony Reichert"
        />
      </DropdownTrigger>
      
      <DropdownMenu aria-label="User Actions" variant="flat" itemClasses={{base: "text-white"}} className="text-foreground">
        <DropdownItem key="profile" isReadOnly className="h-14 gap-2 opacity-100 cursor-default data-[hover=true]:bg-transparent data-[hover=true]:text-white" >
          <p className="font-bold">Signed in as</p>
          <p className="font-bold text-lime-400">Tony Reichert</p>
        </DropdownItem>
        <DropdownItem key="update_photo">Update photo</DropdownItem>
        <DropdownItem key="help_and_feedback">Help & Feedback</DropdownItem>
        <DropdownItem key="logout" color="danger" onPress={() => setView('login')}>
          Log Out
        </DropdownItem>
      </DropdownMenu>
    </Dropdown>
  );
}