import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { getRequests, createForkRequest, actionRequest, cancelRequest } from '../api/requestsApi';

export function useRequests(filters = {}) {
  return useQuery({
    queryKey: ['requests', filters],
    queryFn: () => getRequests(filters),
  });
}

export function useCreateForkRequest() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: createForkRequest,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['requests'] });
    },
  });
}

export function useActionRequest() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ requestId, data }) => actionRequest(requestId, data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['requests'] });
      qc.invalidateQueries({ queryKey: ['documents'] });
    },
  });
}

export function useCancelRequest() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: cancelRequest,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['requests'] });
    },
  });
}
