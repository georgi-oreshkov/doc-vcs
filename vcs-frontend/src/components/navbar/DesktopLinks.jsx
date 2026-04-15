import { NavbarContent, NavbarItem, Link } from "@heroui/react";

export default function DesktopLinks({ navItems, currentPath, onNavigate }) {
  return (
    <NavbarContent className="hidden sm:flex gap-6 ml-6" justify="start">
      {navItems.map((item) => {
        const isActive = currentPath === item.path || currentPath.startsWith(item.path + '/');
        
        return (
          <NavbarItem key={item.path} isActive={isActive}>
            <Link 
              color={isActive ? "primary" : "foreground"} 
              className="cursor-pointer transition-colors hover:text-white" 
              onClick={() => onNavigate(item.path)}
            >
              {item.label}
            </Link>
          </NavbarItem>
        );
      })}
    </NavbarContent>
  );
}