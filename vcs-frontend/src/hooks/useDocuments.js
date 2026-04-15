import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  getMyDocuments,
  getOrgDocuments,
  getDocument,
  createDocument,
  updateDocument,
  deleteDocument,
} from '../api/documentsApi';

export function useMyDocuments() {
  return useQuery({
    queryKey: ['documents', 'my'],
    queryFn: getMyDocuments,
  });
}

export function useOrgDocuments(orgId, filters = {}) {
  return useQuery({
    queryKey: ['organizations', orgId, 'documents', filters],
    queryFn: () => getOrgDocuments(orgId, filters),
    enabled: !!orgId,
  });
}

export function useDocument(docId) {
  return useQuery({
    queryKey: ['documents', docId],
    queryFn: () => getDocument(docId),
    enabled: !!docId,
  });
}

export function useCreateDocument() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ orgId, data }) => createDocument(orgId, data),
    onSuccess: (_, { orgId }) => {
      qc.invalidateQueries({ queryKey: ['organizations', orgId, 'documents'] });
      qc.invalidateQueries({ queryKey: ['documents', 'my'] });
    },
  });
}

export function useUpdateDocument() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ docId, data }) => updateDocument(docId, data),
    onSuccess: (_, { docId }) => {
      qc.invalidateQueries({ queryKey: ['documents', docId] });
    },
  });
}

export function useDeleteDocument() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: deleteDocument,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['organizations'] });
      qc.invalidateQueries({ queryKey: ['documents'] });
    },
  });
}
