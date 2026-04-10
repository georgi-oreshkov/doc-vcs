import { useAuth } from 'react-oidc-context';
import { NavbarContent, NavbarItem, Link, Button } from "@heroui/react";
import UserDropdown from "../UserDropdown"; 

export default function AuthSection({ setView }) {
  const auth = useAuth();
  const handleLogin = () => auth.signinRedirect();

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
            <Button color="primary" variant="flat" onPress={handleLogin}>
              Sign Up
            </Button>
          </NavbarItem>
        </>
      ) : (
        <NavbarItem>
          <UserDropdown setView={setView} />
        </NavbarItem>
      )}
    </NavbarContent>
  );
}