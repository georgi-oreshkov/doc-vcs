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
  const token = getAccessToken();
  const url = `/api/v1/notifications/stream`;

  // If we have a token, use fetch streaming so we can set the Authorization header.
  // EventSource cannot set headers, and many dev proxies strip query auth.
  if (token) {
    const controller = new AbortController();
    (async () => {
      try {
        const res = await fetch(url, {
          method: 'GET',
          headers: { Authorization: `Bearer ${token}` },
          signal: controller.signal,
        });

        if (!res.ok) {
          const text = await res.text().catch(() => '');
          console.error('notifications SSE fetch failed', res.status, text);
          return;
        }

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
            // parse SSE block
            const lines = block.split(/\r?\n/);
            const dataLines = lines.filter(l => l.startsWith('data:')).map(l => l.slice(5).trim());
            if (dataLines.length === 0) continue;
            const dataStr = dataLines.join('\n');
            try { onMessage(JSON.parse(dataStr)); } catch (e) { console.error('failed to parse notification event', e); }
          }
        }
      } catch (e) {
        if (e.name === 'AbortError') return;
        console.error('notifications SSE fetch error', e);
      }
    })();

    return { close: () => controller.abort() };
  }

  // Fallback: try EventSource with access_token query param (no headers possible)
  const qs = `?access_token=${encodeURIComponent('')}`;
  const es = new EventSource(url + qs);
  es.onmessage = (ev) => {
    try {
      const parsed = JSON.parse(ev.data);
      onMessage(parsed);
    } catch (e) {
      console.error('failed to parse notification event', e);
    }
  };
  es.onerror = (err) => {
    console.error('notifications SSE error', err);
  };
  return es;
};
