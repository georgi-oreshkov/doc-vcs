import { Routes, Route, Navigate } from 'react-router-dom';
import { useAuth } from 'react-oidc-context'; 
import AppNavbar from './components/AppNavbar';
import ProtectedRoute from './components/ProtectedRoute';
import LandingView from './views/LandingView';
import OrganizationsView from './views/OrganizationsView';
import DocumentsView from './views/DocumentsView';
import DocumentViewerView from './views/DocumentViewerView';
import ReviewerView from './views/ReviewerView';
import AdminPanelView from './views/AdminPanelView';
import NotificationsView from './views/NotificationsView';

function AuthRedirect({ children }) {
  const auth = useAuth();
  if (auth.isAuthenticated) return <Navigate to="/organizations" replace />;
  return children;
}

export default function App() {
  const auth = useAuth(); 

  if (auth.isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-black text-lime-500 font-sans">
        Authenticating securely...
      </div>
    );
  }

  return (
    <div className="relative min-h-screen flex flex-col overflow-x-hidden bg-black text-white font-sans selection:bg-lime-500/30">
      
      {/* Ambient Background Glows */}
      <div className="fixed inset-0 overflow-hidden pointer-events-none z-0">
        <div className="blob bg-lime-500 w-[600px] h-[600px] top-[-10%] left-[-10%]"></div>
        <div className="blob bg-emerald-600 w-[500px] h-[500px] top-[40%] right-[-10%] opacity-[0.05]" style={{animationDelay: '-5s'}}></div>
      </div>

      <AppNavbar />

      <div className="relative z-10 flex-grow">
        <Routes>
          <Route path="/" element={<AuthRedirect><LandingView /></AuthRedirect>} />
          <Route path="/organizations" element={<ProtectedRoute><OrganizationsView /></ProtectedRoute>} />
          <Route path="/organizations/:orgId/documents" element={<ProtectedRoute><DocumentsView /></ProtectedRoute>} />
          <Route path="/organizations/:orgId/admin" element={<ProtectedRoute><AdminPanelView /></ProtectedRoute>} />
          <Route path="/documents/my" element={<ProtectedRoute><DocumentsView myDocs /></ProtectedRoute>} />
          <Route path="/documents/:docId" element={<ProtectedRoute><DocumentViewerView /></ProtectedRoute>} />
          <Route path="/reviews" element={<ProtectedRoute><ReviewerView /></ProtectedRoute>} />
          <Route path="/notifications" element={<ProtectedRoute><NotificationsView /></ProtectedRoute>} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </div>
     
      <footer className="border-t border-zinc-800 bg-black py-8 text-center text-zinc-500 text-sm mt-auto z-10 relative">
        <p>&copy; 2026 Root Version Control. All rights reserved.</p>
      </footer>
    </div>
  );
}