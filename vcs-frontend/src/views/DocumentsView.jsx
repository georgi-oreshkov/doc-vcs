import { useDisclosure, Button } from "@heroui/react";
import { Plus } from 'lucide-react';
import DocumentCard from '../components/DocumentCard';
import AppNavbar from "../components/AppNavbar"; 
import DocumentsFilter, { useDocumentFilters } from '../components/DocumentsFilter';
import NewDocumentModal from '../components/NewDocumentModal';

export default function DocumentsView({ setView, onSelectDoc }) {
  const { isOpen, onOpen, onOpenChange } = useDisclosure();
 
  const docs = [
    { id: 1, title: "Q3 Financial Report", author: "Alice Smith", status: "Approved", version: "v2.4", date: "Mar 01, 2026", userRelation: "author" },
    { id: 2, title: "API Integrations Spec", author: "Bob Jones", status: "In Review", version: "v1.1", date: "Mar 05, 2026", userRelation: "reviewer" },
    { id: 3, title: "Database Migration Plan", author: "You", status: "Draft", version: "v0.3", date: "Mar 08, 2026", userRelation: "author" },
  ];

  // Filters
  const filterProps = useDocumentFilters(docs);
  const { filteredDocuments } = filterProps; 

  return (
    <div className="max-w-7xl mx-auto px-6 py-12 w-full z-10 flex-grow">
      <div className="flex justify-between items-center mb-8 border-b border-zinc-800 pb-8">
        <div>
          <h1 className="text-3xl font-bold text-white mb-2">Documents</h1>
          <p className="text-zinc-400 text-sm">Search, filter, and manage your organization's versioned documents.</p>
        </div>
        <Button color="primary" startContent={<Plus size={18} />} onPress={onOpen}>
          New Document
        </Button>
      </div>

      {/* Подаваме всички стейтове към филтъра чрез spread оператора */}
      <DocumentsFilter {...filterProps} />

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        {filteredDocuments.map(doc => (
          <DocumentCard 
            key={doc.id} doc={doc} 
            onSelect={() => { onSelectDoc(doc); setView('viewer'); }} 
          />
        ))}
        
        {filteredDocuments.length === 0 && (
          <p className="text-zinc-500 col-span-2">No documents match your filters.</p>
        )}
      </div>
      <NewDocumentModal isOpen={isOpen} onOpenChange={onOpenChange} />
    </div>
  );
}