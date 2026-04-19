import { useEffect, useState } from 'react';
import { getNotifications } from '../api/notificationsApi';

function timeAgo(iso) {
  if (!iso) return '';
  const diff = Date.now() - new Date(iso).getTime();
  const s = Math.floor(diff / 1000);
  if (s < 60) return `${s}s ago`;
  const m = Math.floor(s / 60);
  if (m < 60) return `${m}m ago`;
  const h = Math.floor(m / 60);
  if (h < 24) return `${h}h ago`;
  const d = Math.floor(h / 24);
  return `${d}d ago`;
}

export default function NotificationsView() {
  const [notifications, setNotifications] = useState([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    let mounted = true;
    setLoading(true);
    getNotifications()
      .then((data) => {
        if (!mounted) return;
        setNotifications(Array.isArray(data) ? data : []);
      })
      .catch((e) => {
        console.error('fetch notifications', e);
      })
      .finally(() => mounted && setLoading(false));

    return () => {
      mounted = false;
    };
  }, []);

  return (
    <div className="max-w-4xl mx-auto px-6 py-12 w-full flex-grow">
      <div className="mb-8">
        <h1 className="text-3xl font-bold text-white mb-2">Notifications</h1>
        <p className="text-zinc-400">View all your recent alerts and activity.</p>
      </div>

      <div className="bg-zinc-950 border border-zinc-800 rounded-xl overflow-hidden shadow-xl">
        {loading ? (
          <div className="p-12 text-center text-zinc-500">Loading…</div>
        ) : notifications.length === 0 ? (
          <div className="p-12 text-center text-zinc-500">You have no new notifications.</div>
        ) : (
          <div className="flex flex-col">
            {notifications.map((notif) => {
              let payload = null;
              try { payload = notif.payload ? JSON.parse(notif.payload) : null; } catch (e) { payload = null; }
              const title = payload?.title || notif.type || 'Notification';
              const desc = payload?.message || payload?.desc || (typeof notif.payload === 'string' ? notif.payload : '');
              const isRead = Boolean(notif.readAt);

              return (
                <div
                  key={notif.id}
                  className={`p-6 flex items-start gap-4 transition-colors border-b border-zinc-800/50 last:border-0 hover:bg-zinc-900 ${!isRead ? 'bg-zinc-900/30' : 'bg-transparent'}`}
                >
                  <div className="w-12 h-12 rounded-full bg-zinc-900 border border-zinc-800 flex items-center justify-center shrink-0 mt-1">
                    <span className="text-xs text-zinc-400">🔔</span>
                  </div>

                  <div className="flex flex-col gap-1 flex-grow">
                    <p className={`text-base ${isRead ? 'text-zinc-400' : 'text-zinc-200'}`}>
                      <span className={`font-semibold ${!isRead ? 'text-white' : 'text-zinc-300'}`}>{title}</span>
                    </p>
                    <p className="text-zinc-400 text-sm">{desc}</p>
                    <span className="text-xs text-zinc-500 mt-2 font-medium">{timeAgo(notif.createdAt)}</span>
                  </div>

                  {!isRead && (
                    <div className="w-3 h-3 rounded-full bg-lime-500 shrink-0 mt-2"></div>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}