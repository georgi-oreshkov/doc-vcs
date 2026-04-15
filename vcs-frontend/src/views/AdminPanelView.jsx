import { useParams, useNavigate } from 'react-router-dom';
import { Tabs, Tab, Spinner } from "@heroui/react";
import { useOrg } from '../context/OrgContext';
import { useOrganization } from '../hooks/useOrganizations';
import AdminMembersTab from '../components/admin/AdminMembersTab';
import AdminCategoriesTab from '../components/admin/AdminCategoriesTab';
import AdminSettingsTab from '../components/admin/AdminSettingsTab';

export default function AdminPanelView() {
  const { orgId } = useParams();
  const navigate = useNavigate();
  const { activeRole } = useOrg();
  const { data: org, isLoading } = useOrganization(orgId);

  if (activeRole && activeRole !== 'ADMIN') {
    return (
      <div className="max-w-7xl mx-auto px-6 py-12 text-center">
        <h1 className="text-2xl font-bold text-red-400 mb-2">Access Denied</h1>
        <p className="text-zinc-400">You need the Admin role to access this panel.</p>
      </div>
    );
  }

  if (isLoading) {
    return (
      <div className="flex justify-center items-center py-24">
        <Spinner size="lg" color="primary" />
      </div>
    );
  }

  return (
    <div className="max-w-7xl mx-auto px-6 py-12 w-full z-10 flex-grow">
      <div className="mb-10">
        <h1 className="text-3xl font-bold text-white mb-2">Admin Panel</h1>
        <p className="text-zinc-400 text-sm">{org?.name ?? 'Organization'} &mdash; Manage members, categories, and settings.</p>
      </div>

      <Tabs
        aria-label="Admin sections"
        color="primary"
        variant="underlined"
        classNames={{
          tabList: "border-b border-zinc-800",
          cursor: "bg-lime-500",
          tab: "text-zinc-400 data-[selected=true]:text-white",
        }}
      >
        <Tab key="members" title="Members">
          <AdminMembersTab orgId={orgId} />
        </Tab>
        <Tab key="categories" title="Categories">
          <AdminCategoriesTab orgId={orgId} />
        </Tab>
        <Tab key="settings" title="Settings">
          <AdminSettingsTab orgId={orgId} org={org} onDeleted={() => navigate('/organizations')} />
        </Tab>
      </Tabs>
    </div>
  );
}