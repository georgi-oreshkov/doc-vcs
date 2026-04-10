import { NavbarContent, NavbarItem, Link } from "@heroui/react";

export default function DesktopLinks({ navItems, currentView, onNavigate }) {
  return (
    <NavbarContent className="hidden sm:flex gap-6 ml-6" justify="start">
      {navItems.map((item) => {
        const isActive = currentView === item.view || (item.view === 'documents' && currentView === 'viewer');
        
        return (
          <NavbarItem key={item.view} isActive={isActive}>
            <Link 
              color={isActive ? "primary" : "foreground"} 
              className="cursor-pointer transition-colors hover:text-white" 
              onClick={() => onNavigate(item.view)}
            >
              {item.label}
            </Link>
          </NavbarItem>
        );
      })}
    </NavbarContent>
  );
}