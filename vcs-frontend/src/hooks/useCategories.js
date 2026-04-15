import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { getCategories, createCategory, deleteCategory } from '../api/categoriesApi';

export function useCategories(orgId) {
  return useQuery({
    queryKey: ['organizations', orgId, 'categories'],
    queryFn: () => getCategories(orgId),
    enabled: !!orgId,
  });
}

export function useCreateCategory() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ orgId, data }) => createCategory(orgId, data),
    onSuccess: (_, { orgId }) => {
      qc.invalidateQueries({ queryKey: ['organizations', orgId, 'categories'] });
    },
  });
}

export function useDeleteCategory() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ orgId, catId }) => deleteCategory(orgId, catId),
    onSuccess: (_, { orgId }) => {
      qc.invalidateQueries({ queryKey: ['organizations', orgId, 'categories'] });
    },
  });
}
