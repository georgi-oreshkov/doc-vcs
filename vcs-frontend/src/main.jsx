import React from 'react'
import ReactDOM from 'react-dom/client'
import { HeroUIProvider } from "@heroui/react";
import { AuthProvider } from 'react-oidc-context'
import App from './App.jsx'
import './index.css'

// Configuration for connecting to the local Keycloak server
const oidcConfig = {
  authority: "http://localhost:18080/realms/vcs",
  client_id: "vcs-frontend",
  redirect_uri: "http://localhost:5173/",
  response_type: "code"
};

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    {/* We wrap the app in AuthProvider so every component knows if the user is logged in */}
    <AuthProvider {...oidcConfig}>
      <HeroUIProvider>
        <main className="dark text-foreground bg-background">
          <App />
        </main>
      </HeroUIProvider>
    </AuthProvider>
  </React.StrictMode>,
)