import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Input, Button, useDisclosure, Spinner } from "@heroui/react";
import { Search, Plus } from 'lucide-react';
import OrganizationCard from '../components/OrganizationCard';
import OrganizationModal from '../components/OrganizationModal';
import { useOrganizations, useCreateOrganization, useUpdateOrganization } from '../hooks/useOrganizations';
import { useOrg } from '../context/OrgContext';

export default function OrganizationsView() {
  const navigate = useNavigate();
  const { setSelectedOrg } = useOrg();
  const { isOpen, onOpen, onOpenChange } = useDisclosure();
  const [editingOrg, setEditingOrg] = useState(null);
  const [searchQuery, setSearchQuery] = useState('');

  const { data: orgs = [], isLoading, error } = useOrganizations();
  const createOrg = useCreateOrganization();
  const updateOrg = useUpdateOrganization();

  const filteredOrgs = orgs.filter(o => o.name.toLowerCase().includes(searchQuery.toLowerCase()));

  const handleOpenModal = (org = null) => {
    setEditingOrg(org);
    onOpen();
  };

  const handleSaveOrg = (data, onClose) => {
    if (editingOrg) {
      updateOrg.mutate({ orgId: editingOrg.id, data }, { onSuccess: onClose });
    } else {
      createOrg.mutate(data, { onSuccess: onClose });
    }
  };

  const handleSelectOrg = (org) => {
    setSelectedOrg(org);
    navigate(`/organizations/${org.id}/documents`);
  };

  return (
    <div className="max-w-7xl mx-auto px-6 py-12 w-full z-10 flex-grow">
      <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-6 mb-10">
        <div>
          <h1 className="text-3xl font-bold text-white mb-2">Organizations</h1>
          <p className="text-zinc-400 text-sm">Select a workspace to manage documents and versions.</p>
        </div>
        <div className="flex gap-4 w-full md:w-auto">
          <Input 
            startContent={<Search size={18} className="text-zinc-500" />}
            placeholder="Search organizations..." 
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full sm:w-64"
            variant="bordered"
          />
          <Button color="primary" startContent={<Plus size={18} />} onPress={() => handleOpenModal()}>
            Create
          </Button>
        </div>
      </div>

      {isLoading ? (
        <div className="flex justify-center py-20"><Spinner color="primary" size="lg" /></div>
      ) : error ? (
        <p className="text-red-400 text-center py-20">Failed to load organizations.</p>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {filteredOrgs.map(org => (
            <OrganizationCard 
              key={org.id} 
              org={org} 
              onSelect={handleSelectOrg} 
              onOpenModal={handleOpenModal} 
            />
          ))}
        </div>
      )}

      <OrganizationModal 
        isOpen={isOpen} 
        onOpenChange={onOpenChange} 
        editingOrg={editingOrg}
        onSave={handleSaveOrg}
        isSaving={createOrg.isPending || updateOrg.isPending}
      />
    </div>
  );
}