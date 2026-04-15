import { useAuth } from 'react-oidc-context';
import { Navigate, useLocation } from 'react-router-dom';
import { useUserProfile } from '../hooks/useUser';

export default function ProtectedRoute({ children }) {
  const auth = useAuth();
  const location = useLocation();

  // Ensure the user_profiles row exists on the backend.
  // getOrCreateProfile is idempotent; React Query caches the result.
  const { isLoading: profileLoading } = useUserProfile({
    enabled: auth.isAuthenticated,
  });

  if (auth.isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-black text-lime-500 font-sans">
        Authenticating securely...
      </div>
    );
  }

  if (!auth.isAuthenticated) {
    sessionStorage.setItem('returnTo', location.pathname);
    auth.signinRedirect();
    return null;
  }

  if (profileLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-black text-lime-500 font-sans">
        Loading profile...
      </div>
    );
  }

  return children;
}
