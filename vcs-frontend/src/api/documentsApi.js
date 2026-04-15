import api from './axiosInstance';

export const getMyDocuments = () =>
  api.get('/documents/my').then((r) => r.data);

export const getOrgDocuments = (orgId, params = {}) =>
  api.get(`/organizations/${orgId}/documents`, { params }).then((r) => r.data);

export const createDocument = (orgId, data) =>
  api.post(`/organizations/${orgId}/documents`, data).then((r) => r.data);

export const getDocument = (docId) =>
  api.get(`/documents/${docId}`).then((r) => r.data);

export const updateDocument = (docId, data) =>
  api.patch(`/documents/${docId}`, data).then((r) => r.data);

export const deleteDocument = (docId) =>
  api.delete(`/documents/${docId}`).then((r) => r.data);
