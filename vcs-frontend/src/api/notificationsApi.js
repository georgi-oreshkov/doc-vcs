import api from './axiosInstance';
import { User } from 'oidc-client-ts';

const OIDC_STORAGE_KEY = `oidc.user:http://localhost:18080/realms/vcs:vcs-frontend`;

function getAccessToken() {
  const raw = localStorage.getItem(OIDC_STORAGE_KEY);
  if (!raw) return null;
  try {
    const user = User.fromStorageString(raw);
    return user?.access_token ?? null;
  } catch (e) {
    return null;
  }
}

export const getNotifications = (params = {}) =>
  api.get('/notifications', { params }).then((r) => {
    const data = r.data;
    // backend returns a Page<NotificationDto> — prefer content if present
    return data?.content ?? data;
  });

export const markAsRead = (id) =>
  api.post(`/notifications/${id}/read`).then((r) => r.data);

export const connectNotificationsStream = (onMessage) => {
  const url = `/api/v1/notifications/stream`;
  let closed = false;
  let retryDelay = 2000;
  const controller = new AbortController();

  const connect = async () => {
    if (closed) return;
    const token = getAccessToken();
    if (!token) return;

    try {
      const res = await fetch(url, {
        method: 'GET',
        headers: { Authorization: `Bearer ${token}` },
        signal: controller.signal,
      });

      if (!res.ok) {
        const text = await res.text().catch(() => '');
        console.error('notifications SSE fetch failed', res.status, text);
        // Don't retry on auth errors
        if (res.status === 401 || res.status === 403) return;
      } else {
        retryDelay = 2000; // reset backoff on successful connection
        const reader = res.body.getReader();
        const dec = new TextDecoder('utf-8');
        let buf = '';

        while (true) {
          const { done, value } = await reader.read();
          if (done) break;
          buf += dec.decode(value, { stream: true });

          let idx;
          while ((idx = buf.indexOf('\n\n')) !== -1) {
            const block = buf.slice(0, idx).trim();
            buf = buf.slice(idx + 2);
            const lines = block.split(/\r?\n/);
            const dataLines = lines.filter(l => l.startsWith('data:')).map(l => l.slice(5).trim());
            if (dataLines.length === 0) continue;
            const dataStr = dataLines.join('\n');
            try { onMessage(JSON.parse(dataStr)); } catch (e) { console.error('failed to parse notification event', e); }
          }
        }
      }
    } catch (e) {
      if (e.name === 'AbortError') return;
      console.warn('notifications SSE fetch error, retrying in', retryDelay, 'ms', e);
    }

    if (!closed) {
      setTimeout(connect, retryDelay);
      retryDelay = Math.min(retryDelay * 2, 30000); // exponential backoff, max 30s
    }
  };

  connect();
  return { close: () => { closed = true; controller.abort(); } };
};
