import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useDisclosure, Button, Spinner, Modal, ModalContent, ModalHeader, ModalBody, ModalFooter } from "@heroui/react";
import { Plus, ArrowLeft } from 'lucide-react';
import { useAuth } from 'react-oidc-context';
import DocumentCard from '../components/DocumentCard';
import DocumentsFilter, { useDocumentFilters } from '../components/DocumentsFilter';
import NewDocumentModal from '../components/NewDocumentModal';
import ManageReviewersModal from '../components/ManageReviewersModal';
import { useOrgDocuments, useMyDocuments, useCreateDocument, useDeleteDocument } from '../hooks/useDocuments';
import { useOrg } from '../context/OrgContext';
import { useOrganization, useOrgUsers } from '../hooks/useOrganizations';
import { useCategories } from '../hooks/useCategories';
import { displayStatus } from '../api/transforms';

export default function DocumentsView({ myDocs }) {
  const navigate = useNavigate();
  const { orgId } = useParams();
  const { selectedOrg, activeRole } = useOrg();
  const auth = useAuth();
  const currentUserId = auth.user?.profile?.sub;

  const { isOpen, onOpen, onOpenChange } = useDisclosure();
  const { isOpen: isDeleteOpen, onOpen: onDeleteOpen, onOpenChange: onDeleteOpenChange } = useDisclosure();
  const { isOpen: isReviewersOpen, onOpen: onReviewersOpen, onOpenChange: onReviewersOpenChange } = useDisclosure();

  const [docToDelete, setDocToDelete] = useState(null);
  const [docForReviewers, setDocForReviewers] = useState(null);
  const [selectedCategory, setSelectedCategory] = useState(new Set([]));

  const effectiveOrgId = orgId || selectedOrg?.id;
  const { data: orgData } = useOrganization(effectiveOrgId);
  const { data: orgUsersData = [] } = useOrgUsers(myDocs ? null : effectiveOrgId);
  const orgUsers = Array.isArray(orgUsersData) ? orgUsersData : [];
  const { data: categoriesData = [] } = useCategories(myDocs ? null : effectiveOrgId);

  const categoryId = Array.from(selectedCategory)[0] ?? undefined;
  const { data: orgDocsData, isLoading: orgLoading } = useOrgDocuments(effectiveOrgId, categoryId ? { category_id: categoryId } : {});
  const { data: myDocsData, isLoading: myLoading } = useMyDocuments();
  const createDoc = useCreateDocument();
  const deleteDoc = useDeleteDocument();

  const rawDocs = myDocs ? (myDocsData || []) : (orgDocsData?.content || orgDocsData || []);
  const isLoading = myDocs ? myLoading : orgLoading;

  const docs = rawDocs.map(doc => {
    const authorUser = orgUsers.find(u => u.user_id === doc.author_id);
    const categoryName = categoriesData.find(c => c.id === doc.category_id)?.name ?? null;
    return {
      id: doc.id,
      title: doc.name,
      author: authorUser?.name || (doc.author_id ? doc.author_id.slice(0, 8) : ''),
      authorId: doc.author_id,
      status: displayStatus(doc.status),
      version: doc.latest_version_id ? null : 'v0',
      date: doc.created_at || '',
      userRelation: activeRole?.toLowerCase() || 'reader',
      canDelete: activeRole === 'ADMIN' || doc.author_id === currentUserId,
      canManageReviewers: activeRole === 'ADMIN',
      reviewerIds: doc.reviewer_ids || [],
      categoryName,
    };
  });

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

  const handleDelete = (docId) => {
    setDocToDelete(docId);
    onDeleteOpen();
  };

  const confirmDelete = (onClose) => {
    if (!docToDelete) return;
    deleteDoc.mutate(docToDelete, {
      onSuccess: () => {
        setDocToDelete(null);
        onClose();
      },
    });
  };

  const handleManageReviewers = (docId) => {
    const doc = rawDocs.find(d => d.id === docId);
    setDocForReviewers(doc || { id: docId });
    onReviewersOpen();
  };

  return (
    <div className="max-w-7xl mx-auto px-6 py-12 w-full z-10 flex-grow">
      {!myDocs && (selectedOrg || orgData || effectiveOrgId) && (
        <div className="flex items-center gap-2 text-sm text-zinc-500 mb-4">
          <button onClick={() => navigate(-1)} className="flex items-center gap-1 hover:text-white transition">
            <ArrowLeft size={16} /> Back
          </button>
          <span>/</span>
          <span className="text-zinc-300 font-semibold">{orgData?.name || selectedOrg?.name || 'Workspace'}</span>
        </div>
      )}

      <div className="flex justify-between items-center mb-8 border-b border-zinc-800 pb-8">
        <div>
          <h1 className="text-3xl font-bold text-white mb-2">{myDocs ? 'My Documents' : 'Documents'}</h1>
          <p className="text-zinc-400 text-sm">Search, filter, and manage your organization's versioned documents.</p>
        </div>
        {!myDocs && (activeRole === 'AUTHOR' || activeRole === 'ADMIN') && (
          <Button
            className="bg-lime-600 text-black font-bold hover:bg-lime-500 disabled:bg-zinc-800 disabled:text-zinc-600 transition-colors"
            startContent={<Plus size={18} />} onPress={onOpen}
          >
            New Document
          </Button>
        )}
      </div>

      <DocumentsFilter
        {...filterProps}
        orgUsers={orgUsers}
        categories={categoriesData}
        selectedCategory={selectedCategory}
        setSelectedCategory={setSelectedCategory}
      />

      {isLoading ? (
        <div className="flex justify-center py-20"><Spinner color="primary" size="lg" /></div>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
          {filteredDocuments.map(doc => (
            <DocumentCard
              key={doc.id}
              doc={doc}
              onSelect={() => navigate(`/documents/${doc.id}`)}
              canDelete={doc.canDelete}
              canManageReviewers={doc.canManageReviewers}
              onDelete={handleDelete}
              onManageReviewers={handleManageReviewers}
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
        categories={categoriesData}
      />

      {/* Delete confirmation modal */}
      <Modal
        isOpen={isDeleteOpen}
        onOpenChange={onDeleteOpenChange}
        classNames={{
          base: "bg-zinc-950 border border-zinc-800",
          closeButton: "hover:bg-white/5 active:bg-white/10 text-zinc-400",
        }}
      >
        <ModalContent>
          {(onClose) => (
            <>
              <ModalHeader className="text-white font-bold text-xl">Delete Document</ModalHeader>
              <ModalBody>
                <p className="text-zinc-300">Are you sure you want to delete this document? This action cannot be undone.</p>
              </ModalBody>
              <ModalFooter>
                <Button variant="light" onPress={onClose} className="text-zinc-400">
                  Cancel
                </Button>
                <Button
                  color="danger"
                  onPress={() => confirmDelete(onClose)}
                  isLoading={deleteDoc.isPending}
                >
                  Delete
                </Button>
              </ModalFooter>
            </>
          )}
        </ModalContent>
      </Modal>

      {/* Manage reviewers modal */}
      <ManageReviewersModal
        isOpen={isReviewersOpen}
        onOpenChange={onReviewersOpenChange}
        docId={docForReviewers?.id}
        orgUsers={orgUsers}
        currentReviewerIds={docForReviewers?.reviewer_ids || []}
      />
    </div>
  );
}
