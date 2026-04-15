import { createContext, useContext, useState } from 'react';

const OrgContext = createContext(null);

export function OrgProvider({ children }) {
  const [selectedOrg, setSelectedOrg] = useState(null);

  return (
    <OrgContext.Provider value={{ selectedOrg, setSelectedOrg }}>
      {children}
    </OrgContext.Provider>
  );
}

export function useOrg() {
  const ctx = useContext(OrgContext);
  if (!ctx) throw new Error('useOrg must be used within OrgProvider');
  return ctx;
}
