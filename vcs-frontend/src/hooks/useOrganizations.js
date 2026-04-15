import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  getOrganizations,
  createOrganization,
  getOrganization,
  updateOrganization,
  deleteOrganization,
  getOrgUsers,
  addOrUpdateOrgUser,
  removeOrgUser,
} from '../api/organizationsApi';

export function useOrganizations() {
  return useQuery({
    queryKey: ['organizations'],
    queryFn: getOrganizations,
  });
}

export function useOrganization(orgId) {
  return useQuery({
    queryKey: ['organizations', orgId],
    queryFn: () => getOrganization(orgId),
    enabled: !!orgId,
  });
}

export function useCreateOrganization() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: createOrganization,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['organizations'] });
    },
  });
}

export function useUpdateOrganization() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ orgId, data }) => updateOrganization(orgId, data),
    onSuccess: (_, { orgId }) => {
      qc.invalidateQueries({ queryKey: ['organizations'] });
      qc.invalidateQueries({ queryKey: ['organizations', orgId] });
    },
  });
}

export function useDeleteOrganization() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: deleteOrganization,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['organizations'] });
    },
  });
}

export function useOrgUsers(orgId) {
  return useQuery({
    queryKey: ['organizations', orgId, 'users'],
    queryFn: () => getOrgUsers(orgId),
    enabled: !!orgId,
  });
}

export function useAddOrgUser() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ orgId, data }) => addOrUpdateOrgUser(orgId, data),
    onSuccess: (_, { orgId }) => {
      qc.invalidateQueries({ queryKey: ['organizations', orgId, 'users'] });
    },
  });
}

export function useRemoveOrgUser() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ orgId, userId }) => removeOrgUser(orgId, userId),
    onSuccess: (_, { orgId }) => {
      qc.invalidateQueries({ queryKey: ['organizations', orgId, 'users'] });
    },
  });
}
