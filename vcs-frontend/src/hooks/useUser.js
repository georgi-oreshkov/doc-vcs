import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { getUserProfile, updateUserProfile } from '../api/userApi';

export function useUserProfile(options = {}) {
  return useQuery({
    queryKey: ['user', 'profile'],
    queryFn: getUserProfile,
    ...options,
  });
}

export function useUpdateProfile() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: updateUserProfile,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['user', 'profile'] });
    },
  });
}
