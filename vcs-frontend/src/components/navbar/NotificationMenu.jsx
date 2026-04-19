import { useEffect, useRef, useState } from 'react';
import { Popover, PopoverTrigger, PopoverContent, Button, Badge } from "@heroui/react";
import { Bell, Check } from "lucide-react";
import { getNotifications, connectNotificationsStream, markAsRead } from '../../api/notificationsApi';

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

export default function NotificationMenu() {
  const [activeTab, setActiveTab] = useState('unread');
  const [notifications, setNotifications] = useState([]);
  const [loading, setLoading] = useState(false);
  const esRef = useRef(null);

  const unreadCount = notifications.filter(n => !n.readAt).length;

  useEffect(() => {
    let mounted = true;
    setLoading(true);
    getNotifications({ unreadOnly: true })
      .then((data) => {
        if (!mounted) return;
        setNotifications(Array.isArray(data) ? data : []);
      })
      .catch((e) => console.error('fetch notifications', e))
      .finally(() => mounted && setLoading(false));

    // SSE connection for live updates
    try {
      esRef.current = connectNotificationsStream((notif) => {
        setNotifications((prev) => {
          // prepend new notification (avoid duplicates)
          if (prev.some((p) => p.id === notif.id)) return prev;
          return [notif, ...prev];
        });
      });
    } catch (e) {
      console.error('sse connect', e);
    }

    return () => {
      mounted = false;
      if (esRef.current) esRef.current.close();
    };
  }, []);

  useEffect(() => {
    setLoading(true);
    getNotifications({ unreadOnly: activeTab === 'unread' })
      .then((data) => setNotifications(Array.isArray(data) ? data : []))
      .catch((e) => console.error('fetch notifications', e))
      .finally(() => setLoading(false));
  }, [activeTab]);

  const handleMarkAllRead = async () => {
    try {
      await Promise.all(
        notifications.filter(n => !n.readAt).map(n => markAsRead(n.id))
      );
      setNotifications((prev) => prev.map(n => ({ ...n, readAt: n.readAt ?? new Date().toISOString() })));
    } catch (e) {
      console.error('mark all read', e);
    }
  };

  const handleClickNotification = async (notif) => {
    if (!notif.readAt) {
      try {
        await markAsRead(notif.id);
        setNotifications((prev) => prev.map(n => n.id === notif.id ? { ...n, readAt: new Date().toISOString() } : n));
      } catch (e) {
        console.error('mark read', e);
      }
    }
    // TODO: navigate to related resource if payload contains link
  };

  return (
    <Popover placement="bottom-end" offset={12}>
      <Badge
        content={unreadCount}
        color="danger"
        shape="circle"
        isInvisible={unreadCount === 0}
        classNames={{ badge: "border-zinc-950" }}
      >
        <PopoverTrigger>
          <Button isIconOnly variant="light" className="text-zinc-300 hover:text-white rounded-full">
            <Bell size={20} />
          </Button>
        </PopoverTrigger>
      </Badge>

      <PopoverContent className="p-0 bg-zinc-950 border border-zinc-800 rounded-xl w-[360px] overflow-hidden shadow-2xl">
        <div className="flex gap-4 px-4 pt-4 pb-2 border-b border-zinc-800/50">
          <button
            onClick={() => setActiveTab('unread')}
            className={`pb-2 text-sm font-medium border-b-2 transition-all ${
              activeTab === 'unread'
                ? 'border-lime-500 text-lime-500'
                : 'border-transparent text-zinc-400 hover:text-zinc-200'
            }`}
          >
            Unread
          </button>
          <button
            onClick={() => setActiveTab('all')}
            className={`pb-2 text-sm font-medium border-b-2 transition-all ${
              activeTab === 'all'
                ? 'border-lime-500 text-lime-500'
                : 'border-transparent text-zinc-400 hover:text-zinc-200'
            }`}
          >
            All
          </button>
        </div>

        <div onClick={handleMarkAllRead} className="px-4 py-3 flex items-center gap-2 border-b border-zinc-800/50 text-zinc-400 hover:text-white hover:bg-zinc-900 cursor-pointer transition-colors text-sm">
          <Check size={16} className="text-lime-500" />
          <span>Mark all as read</span>
        </div>

        <div className="max-h-[400px] overflow-y-auto custom-scrollbar bg-zinc-950">
          {loading && <div className="p-4 text-zinc-400">Loading…</div>}
          {!loading && notifications.length === 0 && (
            <div className="p-4 text-zinc-500">No notifications</div>
          )}
          {notifications.map((notif) => {
            // try to parse payload if JSON
            let payload = null;
            try { payload = notif.payload ? JSON.parse(notif.payload) : null; } catch (e) { payload = null; }
            const title = payload?.title || notif.type || 'Notification';
            const desc = payload?.message || payload?.desc || (typeof notif.payload === 'string' ? notif.payload : '') ;
            return (
              <div
                key={notif.id}
                onClick={() => handleClickNotification(notif)}
                className={`p-4 flex gap-4 cursor-pointer transition-colors border-b border-zinc-800/30 hover:bg-zinc-900 ${
                  !notif.readAt ? 'bg-zinc-900/30' : 'bg-transparent'
                }`}
              >
                <div className="w-10 h-10 rounded-full bg-zinc-900 border border-zinc-800 flex items-center justify-center shrink-0">
                  <span className="text-xs text-zinc-400">🔔</span>
                </div>

                <div className="flex flex-col gap-1">
                  <p className={`text-sm leading-tight ${notif.readAt ? 'text-zinc-400' : 'text-zinc-200'}`}>
                    <span className={`font-semibold ${!notif.readAt ? 'text-white' : 'text-zinc-300'}`}>
                      {title}
                    </span> – {desc}
                  </p>
                  <span className="text-xs text-zinc-500">{timeAgo(notif.createdAt)}</span>
                </div>
              </div>
            );
          })}
        </div>

        <div className="bg-zinc-950 p-3 text-center border-t border-zinc-800 hover:bg-zinc-900 cursor-pointer transition-colors">
          <span className="text-sm font-medium text-zinc-400 hover:text-white transition-colors">View all</span>
        </div>
      </PopoverContent>
    </Popover>
  );
}