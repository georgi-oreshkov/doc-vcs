import { useState } from 'react';
import { useAuth } from 'react-oidc-context';
import { useNavigate, useLocation } from 'react-router-dom';
import { Navbar, NavbarContent, NavbarMenuToggle } from "@heroui/react";

import NavLogo from "./navbar/NavLogo";
import DesktopLinks from "./navbar/DesktopLinks";
import AuthSection from "./navbar/AuthSection";
import MobileMenuContent from "./navbar/MobileMenuContent";

export const NAV_ITEMS = [
  { label: "Organizations", path: "/organizations" },
  { label: "My Documents", path: "/documents/my" },
  { label: "Approvals", path: "/reviews" },
];

export default function AppNavbar() {
  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const auth = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const handleNavigation = (path) => {
    navigate(path);
    setIsMenuOpen(false);
  };

  return (
    <Navbar 
      isBordered 
      className="bg-black/80 backdrop-blur-lg border-zinc-800"
      isMenuOpen={isMenuOpen}
      onMenuOpenChange={setIsMenuOpen}
    >
      <NavbarContent justify="start">
        <NavLogo onNavigate={handleNavigation} isAuthenticated={auth.isAuthenticated} />
      </NavbarContent>

      <NavbarContent className="sm:hidden" justify="center">
        <NavbarMenuToggle className="text-white" aria-label={isMenuOpen ? "Close menu" : "Open menu"} />
      </NavbarContent>

      <DesktopLinks 
        navItems={NAV_ITEMS} 
        currentPath={location.pathname} 
        onNavigate={handleNavigation} 
      />

      <AuthSection />

      <MobileMenuContent 
        navItems={NAV_ITEMS} 
        currentPath={location.pathname} 
        onNavigate={handleNavigation} 
      />
    </Navbar>
  );
}