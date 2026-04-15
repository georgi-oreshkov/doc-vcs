import api from './axiosInstance';

export const getRequests = (params = {}) =>
  api.get('/requests', { params }).then((r) => r.data);

export const createForkRequest = (data) =>
  api.post('/requests/fork', data).then((r) => r.data);

export const actionRequest = (requestId, data) =>
  api.patch(`/requests/${requestId}/action`, data).then((r) => r.data);

export const cancelRequest = (requestId) =>
  api.delete(`/requests/${requestId}`).then((r) => r.data);
