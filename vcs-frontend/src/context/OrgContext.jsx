import { createContext, useContext, useState, useMemo } from 'react';
import { useMatch } from 'react-router-dom';
import { useAuth } from 'react-oidc-context';
import { useQuery } from '@tanstack/react-query';
import { getOrganizations } from '../api/organizationsApi';

const OrgContext = createContext(null);

export function OrgProvider({ children }) {
  const [selectedOrg, _setSelectedOrg] = useState(null);
  const auth = useAuth();

  // Derive orgId from route when inside /organizations/:orgId/*
  const orgMatch = useMatch('/organizations/:orgId/*');
  const routeOrgId = orgMatch?.params?.orgId;

  // Load orgs list only when authenticated (avoids 401 loop on logout)
  const { data: orgs } = useQuery({
    queryKey: ['organizations'],
    queryFn: getOrganizations,
    enabled: auth.isAuthenticated,
  });

  // Resolve the active org and role
  const { activeOrg, activeRole } = useMemo(() => {
    // If selectedOrg matches the route, use it directly
    if (selectedOrg && (!routeOrgId || selectedOrg.id === routeOrgId)) {
      return { activeOrg: selectedOrg, activeRole: selectedOrg.my_role || null };
    }
    // Otherwise look up from the orgs list
    if (routeOrgId && orgs) {
      const found = orgs.find(o => o.id === routeOrgId);
      if (found) return { activeOrg: found, activeRole: found.my_role || null };
    }
    // Not inside an org context
    if (!routeOrgId) return { activeOrg: null, activeRole: null };
    return { activeOrg: selectedOrg, activeRole: selectedOrg?.my_role || null };
  }, [selectedOrg, routeOrgId, orgs]);

  const setSelectedOrg = (org) => _setSelectedOrg(org);

  return (
    <OrgContext.Provider value={{ selectedOrg: activeOrg, setSelectedOrg, activeRole }}>
      {children}
    </OrgContext.Provider>
  );
}

export function useOrg() {
  const ctx = useContext(OrgContext);
  if (!ctx) throw new Error('useOrg must be used within OrgProvider');
  return ctx;
}
