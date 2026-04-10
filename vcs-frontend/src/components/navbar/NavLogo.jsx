import { NavbarBrand } from "@heroui/react";
import { GitBranch } from 'lucide-react';

export default function NavLogo({ onNavigate, isAuthenticated }) {
  return (
    <NavbarBrand 
      // If logged in, normal cursor. If logged out, add pointer and 'group' for hover effects.
      className={isAuthenticated ? "cursor-default" : "cursor-pointer group"} 
      // If logged in, do nothing. If logged out, navigate to landing.
      onClick={isAuthenticated ? undefined : () => onNavigate('landing')}
    >
      <div className="w-8 h-8 bg-lime-500 rounded-md flex items-center justify-center text-black shadow-[0_0_15px_rgba(163,230,53,0.4)] group-hover:scale-105 transition mr-3">
        <GitBranch size={20} strokeWidth={2.5} />
      </div>
      <p className="font-bold text-inherit text-xl tracking-tight group-hover:text-lime-400 transition text-white">
        Root<span className="text-lime-400">.</span>
      </p>
    </NavbarBrand>
  );
}