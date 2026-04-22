import api from './axiosInstance';
import axios from 'axios';

export const getVersions = (docId, params = {}) =>
  api.get(`/documents/${docId}/versions`, { params }).then((r) => r.data);

export const createVersion = (docId, data) =>
  api.post(`/documents/${docId}/versions`, data).then((r) => r.data);

export const getVersion = (docId, versionId) =>
  api.get(`/documents/${docId}/versions/${versionId}`).then((r) => r.data);

export const getVersionDownloadUrl = (docId, versionId) =>
  api.get(`/documents/${docId}/versions/${versionId}/download`).then((r) => r.data);

export const rollbackVersion = (docId, versionId) =>
  api.post(`/documents/${docId}/versions/${versionId}/rollback`).then((r) => r.data);

export const approveVersion = (docId, versionId) =>
  api.post(`/documents/${docId}/versions/${versionId}/approve`).then((r) => r.data);

export const rejectVersion = (docId, versionId, data = {}) =>
  api.post(`/documents/${docId}/versions/${versionId}/reject`, data).then((r) => r.data);

export const getPendingReviewVersions = () =>
  api.get('/versions/pending-review').then((r) => r.data);

export const getDiff = (docId, fromVersionId, toVersionId) =>
  api.get(`/documents/${docId}/versions/diff`, {
    params: { from: fromVersionId, to: toVersionId },
  }).then((r) => r.data);

export const getComments = (docId, versionId) =>
  api.get(`/documents/${docId}/versions/${versionId}/comments`).then((r) => r.data);

export const addComment = (docId, versionId, data) =>
  api.post(`/documents/${docId}/versions/${versionId}/comments`, data).then((r) => r.data);

export const uploadFileToS3 = (uploadUrl, file) =>
  axios.put(uploadUrl, file, {
    headers: { 'Content-Type': file.type || 'application/octet-stream' },
  });