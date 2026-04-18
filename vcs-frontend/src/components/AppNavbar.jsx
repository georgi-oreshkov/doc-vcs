import { useState, useMemo } from 'react';
import { useAuth } from 'react-oidc-context';
import { useNavigate, useLocation } from 'react-router-dom';
import { Navbar, NavbarContent, NavbarMenuToggle } from "@heroui/react";

import NavLogo from "./navbar/NavLogo";
import DesktopLinks from "./navbar/DesktopLinks";
import AuthSection from "./navbar/AuthSection";
import MobileMenuContent from "./navbar/MobileMenuContent";
import { useOrg } from '../context/OrgContext';

const BASE_NAV_ITEMS = [
  { label: "Organizations", path: "/organizations" },
  { label: "My Documents", path: "/documents/my" },
];

export default function AppNavbar() {
  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const auth = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const { selectedOrg, activeRole } = useOrg();

  const navItems = useMemo(() => {
    let items = [...BASE_NAV_ITEMS];
    
    // Add Approvals tab if user is ADMIN or REVIEWER
    if (activeRole === 'ADMIN' || activeRole === 'REVIEWER') {
      items.push({ label: "Approvals", path: "/reviews" });
    }
    
    // Add Admin tab if user is ADMIN
    if (selectedOrg && activeRole === 'ADMIN') {
      items.push({ label: "Admin", path: `/organizations/${selectedOrg.id}/admin` });
    }
    
    return items;
  }, [selectedOrg, activeRole]);

  // Function to determine if a nav item should be active
  const isNavItemActive = (item) => {
    const path = location.pathname;

    if (item.path === "/organizations") {
      // Highlight Organizations for any /organizations/* route
      return path === "/organizations" || path.startsWith("/organizations/");
    } else if (item.path === "/documents/my") {
      // Only highlight My Documents for the explicit my-documents route
      return path === "/documents/my" || path.startsWith("/documents/my/");
    } else if (item.path === "/reviews") {
      return path === "/reviews" || path.startsWith("/reviews/");
    }
    return path === item.path || path.startsWith(item.path + '/');
  };

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
        navItems={navItems} 
        currentPath={location.pathname} 
        onNavigate={handleNavigation}
        isNavItemActive={isNavItemActive}
      />

      <AuthSection />

      <MobileMenuContent 
        navItems={navItems} 
        currentPath={location.pathname} 
        onNavigate={handleNavigation}
        isNavItemActive={isNavItemActive} 
      />
    </Navbar>
  );
}