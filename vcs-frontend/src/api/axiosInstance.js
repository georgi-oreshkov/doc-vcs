import axios from 'axios';
import { User } from 'oidc-client-ts';

const OIDC_STORAGE_KEY = `oidc.user:http://localhost:18080/realms/vcs:vcs-frontend`;

const api = axios.create({
  baseURL: '/api/v1',
  headers: { 'Content-Type': 'application/json' },
});

api.interceptors.request.use((config) => {
  const raw = localStorage.getItem(OIDC_STORAGE_KEY);
  if (raw) {
    const user = User.fromStorageString(raw);
    if (user?.access_token) {
      config.headers.Authorization = `Bearer ${user.access_token}`;
    }
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      const raw = localStorage.getItem(OIDC_STORAGE_KEY);
      if (raw) {
        localStorage.removeItem(OIDC_STORAGE_KEY);
      }
      window.location.href = '/';
    }
    return Promise.reject(error);
  }
);

export default api;
