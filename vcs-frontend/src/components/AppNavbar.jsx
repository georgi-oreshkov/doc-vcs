import { useState } from 'react';
import { useAuth } from 'react-oidc-context';
import { Navbar, NavbarContent, NavbarMenuToggle } from "@heroui/react";

// Import our modular pieces
import NavLogo from "./navbar/NavLogo";
import DesktopLinks from "./navbar/DesktopLinks";
import AuthSection from "./navbar/AuthSection";
import MobileMenuContent from "./navbar/MobileMenuContent";

// Define navigation items in one place
export const NAV_ITEMS = [
  { label: "Organizations", view: "organizations" },
  { label: "My Documents", view: "documents" },
  { label: "Admin Panel", view: "admin" },
  { label: "Approvals", view: "reviewer" },
];

export default function AppNavbar({ currentView, setView }) {
  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const auth = useAuth();

  // --- THE SMART ROUTER ---
  const handleNavigation = (view) => {
    // If they try to go to 'landing' but are logged in, hijack it to 'documents'
    const targetView = (view === 'landing' && auth.isAuthenticated) ? 'documents' : view;
    
    setView(targetView);
    setIsMenuOpen(false); // Auto-close mobile menu on click
  };

  return (
    <Navbar 
      isBordered 
      className="bg-black/80 backdrop-blur-lg border-zinc-800"
      isMenuOpen={isMenuOpen}
      onMenuOpenChange={setIsMenuOpen}
    >
      {/* 1. Logo */}
      <NavbarContent justify="start">
        <NavLogo onNavigate={handleNavigation} isAuthenticated={auth.isAuthenticated} />
      </NavbarContent>

      {/* 2. Mobile Hamburger Toggle */}
      <NavbarContent className="sm:hidden" justify="center">
        <NavbarMenuToggle className="text-white" aria-label={isMenuOpen ? "Close menu" : "Open menu"} />
      </NavbarContent>

      {/* 3. Desktop Links */}
      <DesktopLinks 
        navItems={NAV_ITEMS} 
        currentView={currentView} 
        onNavigate={handleNavigation} 
      />

      {/* 4. Auth & User Profile */}
      <AuthSection setView={setView} />

      {/* 5. Mobile Menu Overlay */}
      <MobileMenuContent 
        navItems={NAV_ITEMS} 
        currentView={currentView} 
        onNavigate={handleNavigation} 
      />
    </Navbar>
  );
}