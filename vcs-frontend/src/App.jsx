import { useEffect, useState } from 'react';
import { useAuth } from 'react-oidc-context'; 
import AppNavbar from './components/AppNavbar';
import LandingView from './views/LandingView';
import OrganizationsView from './views/OrganizationsView';
import DocumentsView from './views/DocumentsView';
import DocumentViewerView from './views/DocumentViewerView';
import ReviewerView from './views/ReviewerView';
// import AdminPanelView from './views/AdminPanelView';

export default function App() {
  const auth = useAuth(); 
  
  const [currentView, setCurrentView] = useState('landing'); 
  const [selectedDoc, setSelectedDoc] = useState(null);

  // --- THE BOUNCER LOGIC ---
  useEffect(() => {
    // We check the variable (currentView) and run the setter function (setCurrentView)
    if (auth.isAuthenticated && currentView === 'landing') {
      setCurrentView('organizations'); 
    }
  }, [auth.isAuthenticated, currentView]); 

  // Show a loading screen while Keycloak checks if the user is already logged in
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

      <AppNavbar currentView={currentView} setView={setCurrentView} />

      {/* Simple Router */}
      <div className="relative z-10 flex-grow">
        {currentView === 'landing' && <LandingView />}
        {currentView === 'organizations' && <OrganizationsView setView={setCurrentView} />}
        {currentView === 'documents' && <DocumentsView setView={setCurrentView} onSelectDoc={setSelectedDoc} />}
        {currentView === 'viewer' && <DocumentViewerView setView={setCurrentView} doc={selectedDoc} />}
        {currentView === 'reviewer' && <ReviewerView setView={setCurrentView} onSelectDoc={setSelectedDoc} />}
        {/* {currentView === 'admin' && <AdminPanelView setView={setCurrentView} />} */}
      </div>
     
      <footer className="border-t border-zinc-800 bg-black py-8 text-center text-zinc-500 text-sm mt-auto z-10 relative">
        <p>&copy; 2026 Root Version Control. All rights reserved.</p>
      </footer>
    </div>
  );
}