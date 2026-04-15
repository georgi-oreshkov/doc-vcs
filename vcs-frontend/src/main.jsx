import React from 'react'
import ReactDOM from 'react-dom/client'
import { HeroUIProvider } from "@heroui/react";
import { AuthProvider } from 'react-oidc-context'
import { WebStorageStateStore } from 'oidc-client-ts';
import { BrowserRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ReactQueryDevtools } from '@tanstack/react-query-devtools';
import { OrgProvider } from './context/OrgContext';
import App from './App.jsx'
import './index.css'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 5 * 60 * 1000,
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
});

// Configuration for connecting to the local Keycloak server
const oidcConfig = {
  authority: "http://localhost:18080/realms/vcs",
  client_id: "vcs-frontend",
  redirect_uri: "http://localhost:5173/",
  post_logout_redirect_uri: window.location.origin,
  userStore: new WebStorageStateStore({ store: window.localStorage }),
  onSigninCallback: () => {
    const returnTo = sessionStorage.getItem('returnTo') || '/';
    sessionStorage.removeItem('returnTo');
    window.history.replaceState({}, document.title, returnTo);
  }
};

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <AuthProvider {...oidcConfig}>
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <OrgProvider>
            <HeroUIProvider>
              <main className="dark text-foreground bg-background">
                <App />
              </main>
            </HeroUIProvider>
          </OrgProvider>
        </BrowserRouter>
        <ReactQueryDevtools initialIsOpen={false} />
      </QueryClientProvider>
    </AuthProvider>
  </React.StrictMode>,
)