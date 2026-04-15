import { useNavigate, useParams } from 'react-router-dom';
import { useDisclosure, Button, Spinner } from "@heroui/react";
import { Plus } from 'lucide-react';
import DocumentCard from '../components/DocumentCard';
import DocumentsFilter, { useDocumentFilters } from '../components/DocumentsFilter';
import NewDocumentModal from '../components/NewDocumentModal';
import { useOrgDocuments, useMyDocuments, useCreateDocument } from '../hooks/useDocuments';
import { useOrg } from '../context/OrgContext';
import { displayStatus } from '../api/transforms';

export default function DocumentsView({ myDocs }) {
  const navigate = useNavigate();
  const { orgId } = useParams();
  const { selectedOrg } = useOrg();
  const { isOpen, onOpen, onOpenChange } = useDisclosure();

  const effectiveOrgId = orgId || selectedOrg?.id;
  
  const { data: orgDocsData, isLoading: orgLoading } = useOrgDocuments(effectiveOrgId, {}, );
  const { data: myDocsData, isLoading: myLoading } = useMyDocuments();
  const createDoc = useCreateDocument();

  const rawDocs = myDocs ? (myDocsData || []) : (orgDocsData?.content || orgDocsData || []);
  const isLoading = myDocs ? myLoading : orgLoading;

  const docs = rawDocs.map(doc => ({
    id: doc.id,
    title: doc.name,
    author: doc.author_id,
    status: displayStatus(doc.status),
    version: doc.latest_version_id ? '' : 'v0',
    date: doc.created_at || '',
    userRelation: 'author',
  }));

  const filterProps = useDocumentFilters(docs);
  const { filteredDocuments } = filterProps; 

  const handleCreateDocument = (data, file, onClose) => {
    createDoc.mutate(
      { orgId: effectiveOrgId, data },
      {
        onSuccess: async (response) => {
          if (response.upload_url && file) {
            const { uploadFileToS3 } = await import('../api/versionsApi');
            await uploadFileToS3(response.upload_url, file);
          }
          onClose();
        },
      }
    );
  };

  return (
    <div className="max-w-7xl mx-auto px-6 py-12 w-full z-10 flex-grow">
      <div className="flex justify-between items-center mb-8 border-b border-zinc-800 pb-8">
        <div>
          <h1 className="text-3xl font-bold text-white mb-2">{myDocs ? 'My Documents' : 'Documents'}</h1>
          <p className="text-zinc-400 text-sm">Search, filter, and manage your organization's versioned documents.</p>
        </div>
        {!myDocs && (
          <Button color="primary" startContent={<Plus size={18} />} onPress={onOpen}>
            New Document
          </Button>
        )}
      </div>

      <DocumentsFilter {...filterProps} />

      {isLoading ? (
        <div className="flex justify-center py-20"><Spinner color="primary" size="lg" /></div>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
          {filteredDocuments.map(doc => (
            <DocumentCard 
              key={doc.id} doc={doc} 
              onSelect={() => navigate(`/documents/${doc.id}`)} 
            />
          ))}
          
          {filteredDocuments.length === 0 && (
            <p className="text-zinc-500 col-span-2">No documents match your filters.</p>
          )}
        </div>
      )}
      <NewDocumentModal 
        isOpen={isOpen} 
        onOpenChange={onOpenChange} 
        onSave={handleCreateDocument}
        isSaving={createDoc.isPending}
      />
    </div>
  );
}