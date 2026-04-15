import api from './axiosInstance';

export const getUserProfile = () =>
  api.get('/user/profile').then((r) => r.data);

export const updateUserProfile = (data) =>
  api.patch('/user/profile', data).then((r) => r.data);
