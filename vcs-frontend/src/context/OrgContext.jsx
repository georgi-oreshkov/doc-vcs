import { createContext, useContext, useState, useMemo, useEffect } from 'react';
import { useMatch } from 'react-router-dom';
import { useAuth } from 'react-oidc-context';
import { useQuery } from '@tanstack/react-query';
import { getOrganizations } from '../api/organizationsApi';

const OrgContext = createContext(null);

const ROLE_PRIORITY = ['ADMIN', 'AUTHOR', 'REVIEWER', 'READER'];

/** Returns the highest-privilege role from an array, or null. */
function primaryRole(roles) {
  if (!Array.isArray(roles) || roles.length === 0) return null;
  return ROLE_PRIORITY.find((r) => roles.includes(r)) || roles[0];
}

export function OrgProvider({ children }) {
  const [selectedOrg, _setSelectedOrg] = useState(null);
  const auth = useAuth();

  // Derive orgId from route when inside /organizations/:orgId/* or when viewing docs from that org
  const orgMatch = useMatch('/organizations/:orgId/*');
  const routeOrgId = orgMatch?.params?.orgId;

  // Load orgs list only when authenticated (avoids 401 loop on logout)
  const { data: orgs } = useQuery({
    queryKey: ['organizations'],
    queryFn: getOrganizations,
    enabled: auth.isAuthenticated,
  });

  // If the URL contains an orgId, ensure selectedOrg is set so the
  // organization context persists when navigating away from org routes.
  useEffect(() => {
    if (routeOrgId && orgs) {
      const found = orgs.find(o => o.id === routeOrgId);
      if (found && (!selectedOrg || selectedOrg.id !== routeOrgId)) {
        _setSelectedOrg(found);
      }
    }
    // Intentionally do NOT clear selectedOrg when leaving the org route.
    // The user must explicitly "Leave" the workspace to clear it.
  }, [routeOrgId, orgs, selectedOrg]);

  // Resolve the active org and roles
  const { activeOrg, activeRoles, activeRole } = useMemo(() => {
    let org = null;
    // Priority 1: If in an org route, look up from orgs list (most reliable)
    if (routeOrgId && orgs) {
      org = orgs.find(o => o.id === routeOrgId) || null;
    }
    // Priority 2: Use selectedOrg if set (handles navigation outside org routes)
    if (!org && selectedOrg) {
      org = selectedOrg;
    }
    if (!org) return { activeOrg: null, activeRoles: [], activeRole: null };
    const roles = Array.isArray(org.my_roles) ? org.my_roles : (org.my_role ? [org.my_role] : []);
    return { activeOrg: org, activeRoles: roles, activeRole: primaryRole(roles) };
  }, [selectedOrg, routeOrgId, orgs]);

  const setSelectedOrg = (org) => _setSelectedOrg(org);

  return (
    <OrgContext.Provider value={{ selectedOrg: activeOrg, setSelectedOrg, activeRole, activeRoles }}>
      {children}
    </OrgContext.Provider>
  );
}


export function useOrg() {
  const ctx = useContext(OrgContext);
  if (!ctx) throw new Error('useOrg must be used within OrgProvider');
  return ctx;
}
