import { Navbar, NavbarBrand, NavbarContent, NavbarItem, Link, Button, Avatar } from "@heroui/react";
import { GitBranch } from 'lucide-react';

export default function AppNavbar({ currentView, setView }) {
  return (
    <Navbar isBordered variant="sticky" className="bg-black/80 backdrop-blur-lg border-zinc-800">
      <NavbarBrand className="cursor-pointer group" onClick={() => setView('landing')}>
        <div className="w-8 h-8 bg-lime-500 rounded-md flex items-center justify-center text-black shadow-[0_0_15px_rgba(163,230,53,0.4)] group-hover:scale-105 transition mr-3">
          <GitBranch size={20} strokeWidth={2.5} />
        </div>
        <p className="font-bold text-inherit text-xl tracking-tight group-hover:text-lime-400 transition text-white">
          Root<span className="text-lime-400">.</span>
        </p>
      </NavbarBrand>

      <NavbarContent className="hidden sm:flex gap-6" justify="center">
        <NavbarItem isActive={currentView === 'organizations'}>
          <Link color={currentView === 'organizations' ? "primary" : "foreground"} className="cursor-pointer" onClick={() => setView('organizations')}>
            Organizations
          </Link>
        </NavbarItem>
        <NavbarItem isActive={currentView === 'documents' || currentView === 'viewer'}>
          <Link color={currentView === 'documents' || currentView === 'viewer' ? "primary" : "foreground"} className="cursor-pointer" onClick={() => setView('documents')}>
            My Documents
          </Link>
        </NavbarItem>
        <NavbarItem isActive={currentView === 'admin'}>
          <Link color={currentView === 'admin' ? "primary" : "foreground"} className="cursor-pointer" onClick={() => setView('admin')}>
            Admin Panel
          </Link>
        </NavbarItem>
      </NavbarContent>

      <NavbarContent justify="end">
        <NavbarItem className="hidden lg:flex">
          <Link className="cursor-pointer text-zinc-300 hover:text-white" onClick={() => setView('login')}>Login</Link>
        </NavbarItem>
        <NavbarItem>
          <Button color="primary" variant="flat" onClick={() => setView('register')}>
            Sign Up
          </Button>
        </NavbarItem>
        <NavbarItem>
            <Avatar isBordered color="primary" src="https://i.pravatar.cc/150?u=a042581f4e29026024d" size="sm" />
        </NavbarItem>
      </NavbarContent>
    </Navbar>
  );
}