import { useState } from 'react';
import { useAuth } from 'react-oidc-context'; // 1. Import the Keycloak hook
import AppNavbar from './components/AppNavbar';
import LandingView from './views/LandingView';
// Notice: We completely removed LoginView and RegisterView because Keycloak handles this!
import OrganizationsView from './views/OrganizationsView';
import DocumentsView from './views/DocumentsView';
import DocumentViewerView from './views/DocumentViewerView';
import ReviewerView from './views/ReviewerView';
// import AdminPanelView from './views/AdminPanelView';

export default function App() {
  const auth = useAuth(); // 2. Initialize the authentication state
  const testBackendConnection = async () => {
    try {
      console.log("Sending request to backend...");
      
      // Thanks to the Vite proxy, '/api/v1/documents' automatically routes to localhost:8080
      const response = await fetch('/api/v1/documents/my', {
        headers: {
          Authorization: `Bearer ${auth.user?.access_token}`
        }
      });

      if (response.ok) {
        const data = await response.json();
        console.log("✅ Success! Backend returned:", data);
        alert("Connection successful! Check your browser console.");
      } else {
        console.error("❌ Backend rejected the request. Status:", response.status);
        alert(`Request failed with status: ${response.status}`);
      }
    } catch (error) {
      console.error("❌ Network error. Is the Spring Boot server running?", error);
    }
  };
  const [currentView, setCurrentView] = useState('landing'); 
  const [selectedDoc, setSelectedDoc] = useState(null)

  // 3. Show a loading screen while Keycloak checks if the user is already logged in
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

      {/* --- TEMPORARY KEYCLOAK TEST BAR --- 
          You can move these buttons into your AppNavbar later! */}
      <div className="relative z-50 bg-zinc-900 border-b border-zinc-800 p-3 flex justify-between items-center px-6 text-sm">
        {auth.isAuthenticated ? (
          <>
            <span className="text-lime-400 font-medium">
              Logged in as: {auth.user?.profile.preferred_username}
            </span>
            <div className="space-x-6">
              {/* This logs the token to your browser console so you can copy it for testing */}
              <button 
                onClick={() => console.log("YOUR TOKEN:", auth.user?.access_token)} 
                className="text-zinc-400 hover:text-white transition-colors"
              >
                Print Token to Console
              </button>
              <button 
                onClick={() => void auth.removeUser()} 
                className="text-red-400 hover:text-red-300 font-medium transition-colors"
              >
                Log Out
              </button>
              <button  onClick={testBackendConnection} 
                className="bg-blue-600 hover:bg-blue-500 text-white px-4 py-1.5 rounded transition-colors ml-4"
              >
                Test Backend Connection
              </button>
            </div>
          </>
        ) : (
          <>
            <span className="text-zinc-400">You are currently browsing as a guest.</span>
            {/* This button triggers the redirect to the Keycloak login screen */}
            <button 
              onClick={() => void auth.signinRedirect()} 
              className="bg-lime-600 hover:bg-lime-500 text-white px-4 py-1.5 rounded transition-colors"
            >
              Log in with Keycloak
            </button>
          </>
        )}
      </div>
      {/* ----------------------------------- */}

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