import { useAuth } from 'react-oidc-context';
import { NavbarContent, NavbarItem, Link, Button } from "@heroui/react";
import UserDropdown from "../UserDropdown"; 
import NotificationMenu from "./NotificationMenu";

export default function AuthSection() {
  const auth = useAuth();
  
  const handleLogin = () => auth.signinRedirect();
  
  const handleRegister = () => {
    auth.signinRedirect({
      prompt: 'create',
      extraQueryParams: { 
        kc_action: 'register' 
      }
    });
  };

  return (
    <NavbarContent justify="end">
      {!auth.isAuthenticated ? (
        <>
          <NavbarItem className="hidden sm:flex">
            <Link className="cursor-pointer text-zinc-300 hover:text-white" onClick={handleLogin}>
              Login
            </Link>
          </NavbarItem>
          <NavbarItem className="hidden sm:flex">
            <Button color="primary" variant="flat" onPress={() => handleRegister()}>
              Sign Up
            </Button>
          </NavbarItem>
        </>
      ) : (
        <>
        <NavbarItem className="flex items-center gap-2">
          <NotificationMenu />
          <UserDropdown />
        </NavbarItem>
        </>
      )}
    </NavbarContent>
  );
}