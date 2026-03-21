import { useState } from 'react';
import { Input, Button, useDisclosure } from "@heroui/react";
import { Search, Plus } from 'lucide-react';
import OrganizationCard from '../components/OrganizationCard';
import OrganizationModal from '../components/OrganizationModal';

export default function OrganizationsView({ setView }) {
  const { isOpen, onOpen, onOpenChange } = useDisclosure();
  const [editingOrg, setEditingOrg] = useState(null);
  const [searchQuery, setSearchQuery] = useState('');

  const orgs = [
    { id: 1, name: "Acme Corporation", members: 124, role: "admin", docs: 45 },
    { id: 2, name: "OpenSource Labs", members: 3042, role: "author", docs: 128 },
    { id: 3, name: "Project Phoenix", members: 8, role: "reviewer", docs: 12 },
  ];

  const filteredOrgs = orgs.filter(o => o.name.toLowerCase().includes(searchQuery.toLowerCase()));

  // this function will be called both for creating a new org (with null) and for editing (with the org data)
  const handleOpenModal = (org = null) => {
    setEditingOrg(org);
    onOpen();
  };

  return (
    <div className="max-w-7xl mx-auto px-6 py-12 w-full z-10 flex-grow">
      {/* Header & Controls */}
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

      {/* Grid with organization cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        {filteredOrgs.map(org => (
          <OrganizationCard 
            key={org.id} 
            org={org} 
            setView={setView} 
            onOpenModal={handleOpenModal} 
          />
        ))}
      </div>

      <OrganizationModal 
        isOpen={isOpen} 
        onOpenChange={onOpenChange} 
        editingOrg={editingOrg} 
      />
    </div>
  );
}