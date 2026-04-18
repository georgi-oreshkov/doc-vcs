import { useAuth } from 'react-oidc-context';
import { Navigate, useLocation } from 'react-router-dom';
import { useUserProfile } from '../hooks/useUser';
import { Button } from '@heroui/react';
import { LogIn } from 'lucide-react';

export default function ProtectedRoute({ children }) {
  const auth = useAuth();
  const location = useLocation();

  // Ensure the user_profiles row exists on the backend.
  // getOrCreateProfile is idempotent; React Query caches the result.
  const { isLoading: profileLoading } = useUserProfile({
    enabled: auth.isAuthenticated,
  });

  // If auth is still loading, show a brief loading state
  if (auth.isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-black">
        <div className="animate-pulse text-zinc-500">Loading...</div>
      </div>
    );
  }

  // If not authenticated, show login/signup message instead of redirecting
  if (!auth.isAuthenticated) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-zinc-950 to-black px-4">
        <div className="text-center max-w-md">
          <LogIn className="text-zinc-400 mx-auto mb-4" size={48} />
          <h1 className="text-2xl font-bold text-white mb-2">Sign In Required</h1>
          <p className="text-zinc-400 mb-6">
            Please log in or sign up to access the features and manage your documents.
          </p>
          <div className="flex gap-3 justify-center">
            <Button
              color="primary"
              onPress={() => {
                sessionStorage.setItem('returnTo', location.pathname);
                auth.signinRedirect();
              }}
              className="px-6"
            >
              Sign In
            </Button>
            <Button
              variant="bordered"
              onPress={() => {
                sessionStorage.setItem('returnTo', location.pathname);
                auth.signinRedirect();
              }}
              className="px-6"
            >
              Sign Up
            </Button>
          </div>
        </div>
      </div>
    );
  }

  if (profileLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-black text-zinc-400 font-sans">
        Loading profile...
      </div>
    );
  }

  return children;
}
