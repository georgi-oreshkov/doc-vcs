import { useState } from 'react';
import { Input, Button, Select, SelectItem } from "@heroui/react";
import { Search, Plus, Filter, User } from 'lucide-react';
import DocumentCard from '../components/DocumentCard';

export default function DocumentsView({ setView, onSelectDoc }) {
  const [searchQuery, setSearchQuery] = useState('');

  const docs = [
    { id: 1, title: "Q3 Financial Report", author: "Alice Smith", status: "Approved", version: "v2.4", date: "Mar 01, 2026", userRelation: "author" },
    { id: 2, title: "API Integrations Spec", author: "Bob Jones", status: "In Review", version: "v1.1", date: "Mar 05, 2026", userRelation: "reviewer" },
    { id: 3, title: "Database Migration Plan", author: "You", status: "Draft", version: "v0.3", date: "Mar 08, 2026", userRelation: "author" },
  ];

  return (
    <div className="max-w-7xl mx-auto px-6 py-12 w-full z-10 flex-grow">
      <div className="flex justify-between items-center mb-8 border-b border-zinc-800 pb-8">
        <div>
          <h1 className="text-3xl font-bold text-white mb-2">Documents</h1>
          <p className="text-zinc-400 text-sm">Search, filter, and manage your organization's versioned documents.</p>
        </div>
        <Button color="primary" startContent={<Plus size={18} />}>New Document</Button>
      </div>

      <div className="flex gap-4 mb-8 bg-zinc-900/50 p-4 rounded-xl border border-zinc-800/80">
        <Input 
          className="flex-1" variant="bordered"
          startContent={<Search size={18} className="text-zinc-500" />}
          placeholder="Search documents by title..." 
          value={searchQuery} onChange={(e) => setSearchQuery(e.target.value)}
        />
        <Select variant="bordered" className="max-w-xs" placeholder="Filter by Author" startContent={<User size={18} className="text-zinc-500"/>}>
          <SelectItem key="you" value="You">You</SelectItem>
          <SelectItem key="alice" value="Alice Smith">Alice Smith</SelectItem>
        </Select>
        <Select variant="bordered" className="max-w-xs" placeholder="Filter by Status" startContent={<Filter size={18} className="text-zinc-500"/>}>
          <SelectItem key="approved" value="approved">Approved</SelectItem>
          <SelectItem key="review" value="in review">In Review</SelectItem>
        </Select>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        {docs.filter(d => d.title.toLowerCase().includes(searchQuery.toLowerCase())).map(doc => (
          <DocumentCard 
            key={doc.id} doc={doc} 
            onSelect={() => { onSelectDoc(doc); setView('viewer'); }} 
          />
        ))}
      </div>
    </div>
  );
}