import { useAuth } from 'react-oidc-context';
import { NavbarMenu, NavbarMenuItem, Link, Button } from "@heroui/react";

export default function MobileMenuContent({ navItems, currentView, onNavigate }) {
  const auth = useAuth();
  const handleLogin = () => auth.signinRedirect();

  return (
    <NavbarMenu className="bg-black/95 pt-8 border-t border-zinc-800 backdrop-blur-xl">
      {navItems.map((item) => (
        <NavbarMenuItem key={`${item.view}-mobile`}>
          <Link
            className="w-full text-white text-xl mb-4"
            color={currentView === item.view ? "primary" : "foreground"}
            onClick={() => onNavigate(item.view)}
            size="lg"
          >
            {item.label}
          </Link>
        </NavbarMenuItem>
      ))}
      
      {!auth.isAuthenticated && (
        <div className="flex flex-col gap-4 mt-8 pt-8 border-t border-zinc-800 w-full">
          <Button variant="bordered" className="w-full text-white border-zinc-700" onPress={handleLogin}>
            Log In
          </Button>
          <Button color="primary" className="w-full" onPress={handleLogin}>
            Sign Up
          </Button>
        </div>
      )}
    </NavbarMenu>
  );
}