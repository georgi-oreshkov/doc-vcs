import api from './axiosInstance';

export const getOrganizations = () =>
  api.get('/organizations').then((r) => r.data);

export const createOrganization = (data) =>
  api.post('/organizations', data).then((r) => r.data);

export const getOrganization = (orgId) =>
  api.get(`/organizations/${orgId}`).then((r) => r.data);

export const updateOrganization = (orgId, data) =>
  api.put(`/organizations/${orgId}`, data).then((r) => r.data);

export const deleteOrganization = (orgId) =>
  api.delete(`/organizations/${orgId}`).then((r) => r.data);

export const getOrgUsers = (orgId) =>
  api.get(`/organizations/${orgId}/users`).then((r) => r.data);

export const addOrUpdateOrgUser = (orgId, data) =>
  api.put(`/organizations/${orgId}/users`, data).then((r) => r.data);

export const removeOrgUser = (orgId, userId) =>
  api.delete(`/organizations/${orgId}/users/${userId}`).then((r) => r.data);
