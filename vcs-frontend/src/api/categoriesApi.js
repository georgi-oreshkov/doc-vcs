import api from './axiosInstance';

export const getCategories = (orgId) =>
  api.get(`/organizations/${orgId}/categories`).then((r) => r.data);

export const createCategory = (orgId, data) =>
  api.post(`/organizations/${orgId}/categories`, data).then((r) => r.data);

export const deleteCategory = (orgId, catId) =>
  api.delete(`/organizations/${orgId}/categories/${catId}`).then((r) => r.data);
