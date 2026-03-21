import { useState } from 'react';
import { 
  Navbar, NavbarBrand, NavbarContent, NavbarItem, Link, Button, 
  NavbarMenuToggle, NavbarMenu, NavbarMenuItem 
} from "@heroui/react";
import { GitBranch } from 'lucide-react';
import UserDropdown from "./UserDropdown";

export default function AppNavbar({ currentView, setView }) {
  const [isMenuOpen, setIsMenuOpen] = useState(false);

  const navItems = [
    { label: "Organizations", view: "organizations" },
    { label: "My Documents", view: "documents" },
    { label: "Admin Panel", view: "admin" },
  ];

  const handleNavigation = (view) => {
    setView(view);
    setIsMenuOpen(false);
  };

  return (
    <Navbar 
      isBordered 
      variant="sticky" 
      className="bg-black/80 backdrop-blur-lg border-zinc-800"
      isMenuOpen={isMenuOpen}
      onMenuOpenChange={setIsMenuOpen}
    >
      {/* 1. Logo */}
      <NavbarContent justify="start">
        <NavbarBrand className="cursor-pointer group" onClick={() => handleNavigation('landing')}>
          <div className="w-8 h-8 bg-lime-500 rounded-md flex items-center justify-center text-black shadow-[0_0_15px_rgba(163,230,53,0.4)] group-hover:scale-105 transition mr-3">
            <GitBranch size={20} strokeWidth={2.5} />
          </div>
          <p className="font-bold text-inherit text-xl tracking-tight group-hover:text-lime-400 transition text-white">
            Root<span className="text-lime-400">.</span>
          </p>
        </NavbarBrand>
      </NavbarContent>

      {/* 2. Sandwich Button */}
      <NavbarContent className="sm:hidden" justify="center">
        <NavbarMenuToggle className="text-white" aria-label={isMenuOpen ? "Close menu" : "Open menu"} />
      </NavbarContent>

      {/* 3. Desktop Links */}
      <NavbarContent className="hidden sm:flex gap-6 ml-6" justify="start">
        {navItems.map((item) => (
          <NavbarItem key={item.view} isActive={currentView === item.view || (item.view === 'documents' && currentView === 'viewer')}>
            <Link 
              color={currentView === item.view || (item.view === 'documents' && currentView === 'viewer') ? "primary" : "foreground"} 
              className="cursor-pointer transition-colors hover:text-white" 
              onClick={() => handleNavigation(item.view)}
            >
              {item.label}
            </Link>
          </NavbarItem>
        ))}
      </NavbarContent>

      {/* 4. Actions: Profile / Login //TO-DO: have a way to determine if the user is logged in or not */} 
      <NavbarContent justify="end">
        {/* {currentView === 'login' || currentView === 'register' || currentView === 'landing' ? ( */}
          <>
            <NavbarItem className="hidden sm:flex">
              <Link className="cursor-pointer text-zinc-300 hover:text-white" onClick={() => handleNavigation('login')}>Login</Link>
            </NavbarItem>
            <NavbarItem className="hidden sm:flex">
              <Button color="primary" variant="flat" onPress={() => handleNavigation('register')}>
                Sign Up
              </Button>
            </NavbarItem>
          </>
        {/* ) : ( */}
          <NavbarItem>
            <UserDropdown setView={setView} />
          </NavbarItem>
        {/* )} */}
      </NavbarContent>

      {/* 5. Mobile Menu */}
      <NavbarMenu className="bg-black/95 pt-8 border-t border-zinc-800 backdrop-blur-xl">
        {navItems.map((item) => (
          <NavbarMenuItem key={`${item.view}-mobile`}>
            <Link
              className="w-full text-white text-xl mb-4"
              color={currentView === item.view ? "primary" : "foreground"}
              onClick={() => handleNavigation(item.view)}
              size="lg"
            >
              {item.label}
            </Link>
          </NavbarMenuItem>
        ))}
        
        {/* {(currentView === 'login' || currentView === 'register' || currentView === 'landing') && ( */}
          <div className="flex flex-col gap-4 mt-8 pt-8 border-t border-zinc-800 w-full">
            <Button variant="bordered" className="w-full text-white border-zinc-700" onPress={() => handleNavigation('login')}>
              Log In
            </Button>
            <Button color="primary" className="w-full" onPress={() => handleNavigation('register')}>
              Sign Up
            </Button>
          </div>
        {/* )} */}
      </NavbarMenu>
    </Navbar>
  );
}