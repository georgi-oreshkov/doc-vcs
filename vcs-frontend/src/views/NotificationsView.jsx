import { useEffect, useState } from "react";
import { useNavigate } from 'react-router-dom';
import { FileSignature, UserPlus, FileText, FileCheck, AlertCircle, Check } from "lucide-react";
import { getNotifications, markAsRead } from '../api/notificationsApi';
import { Button, Spinner } from "@heroui/react";

function timeAgo(iso) {
  if (!iso) return '';
  const diff = Date.now() - new Date(iso).getTime();
  const m = Math.floor(diff / 60000);
  if (m < 1) return 'Just now';
  if (m < 60) return `${m}m ago`;
  const h = Math.floor(m / 60);
  if (h < 24) return `${h}h ago`;
  return new Date(iso).toLocaleDateString();
}

const getNotificationStyle = (type = '', payload = {}) => {
  const t = String(type).toUpperCase();
  const msg = String(payload?.message || '').toUpperCase();

  if (t.includes('APPROV') || msg.includes('APPROV')) 
    return { icon: <FileCheck className="text-lime-500" size={24} />, bg: "bg-zinc-900 border-zinc-800" };
  if (t.includes('MEMBER') || msg.includes('ADDED')) 
    return { icon: <UserPlus className="text-lime-500" size={24} />, bg: "bg-zinc-900 border-zinc-800" };
  if (t.includes('REVIEW') || t.includes('VERSION')) 
    return { icon: <FileSignature className="text-lime-500" size={24} />, bg: "bg-zinc-900 border-zinc-800" };
  if (t.includes('ERROR') || t.includes('FAIL')) 
    return { icon: <AlertCircle className="text-red-500" size={24} />, bg: "bg-red-500/10 border-red-500/20" };
    
  return { icon: <FileText className="text-zinc-400" size={24} />, bg: "bg-zinc-900 border-zinc-800" };
};

const parseNotification = (notif) => {
  let payload = {};
  try {
    payload = typeof notif.payload === 'string' ? JSON.parse(notif.payload) : (notif.payload || {});
  } catch (e) {
    payload = { message: notif.payload };
  }

  const title = payload.documentTitle || payload.title || notif.type?.replace(/_/g, ' ') || 'Notification';
  const desc = payload.message || payload.desc || '';
  const style = getNotificationStyle(notif.type, payload);

  return { title, desc, payload, style };
};

export default function NotificationsView() {
  const [notifications, setNotifications] = useState([]);
  const [loading, setLoading] = useState(true);
  const navigate = useNavigate();

  useEffect(() => {
    getNotifications({})
      .then(data => setNotifications(Array.isArray(data) ? data : []))
      .catch(console.error)
      .finally(() => setLoading(false));
  }, []);

  const handleAction = async (notif) => {
    const { payload } = parseNotification(notif);
    if (!notif.readAt) {
      markAsRead(notif.id).catch(console.error);
      setNotifications(prev => prev.map(n => n.id === notif.id ? { ...n, readAt: new Date().toISOString() } : n));
    }
    
    const orgId = payload.organizationId || payload.orgId || payload.organization_id;
    const docId = payload.documentId || payload.docId || payload.document_id;

    if (docId && orgId) {
      navigate(`/organizations/${orgId}/documents/${docId}`);
    }
  };

  const handleMarkAllRead = async () => {
    const unread = notifications.filter(n => !n.readAt);
    await Promise.all(unread.map(n => markAsRead(n.id)));
    setNotifications(prev => prev.map(n => ({ ...n, readAt: n.readAt ?? new Date().toISOString() })));
  };

  return (
    <div className="max-w-4xl mx-auto px-6 py-12 w-full flex-grow">
      <div className="flex justify-between items-center mb-10">
        <div>
          <h1 className="text-4xl font-black text-white tracking-tight mb-2">Notifications</h1>
          <p className="text-zinc-500 text-sm">Track recent activity and document updates.</p>
        </div>
        <Button variant="flat" onPress={handleMarkAllRead} className="text-lime-500 bg-lime-500/10 hover:bg-lime-500/20 font-bold" startContent={<Check size={18} />}>
          MARK ALL AS READ
        </Button>
      </div>

      <div className="bg-zinc-950 border border-zinc-800 rounded-2xl overflow-hidden shadow-2xl">
        {loading ? (
          <div className="p-20 text-center"><Spinner color="success" /></div>
        ) : notifications.length === 0 ? (
          <div className="p-20 text-center text-zinc-600">You have no new notifications.</div>
        ) : (
          <div className="flex flex-col">
            {notifications.map((notif) => {
              const { title, desc, style } = parseNotification(notif);
              return (
                <div key={notif.id} onClick={() => handleAction(notif)} className={`p-6 flex items-start gap-5 transition-all border-b border-zinc-800/50 last:border-0 hover:bg-zinc-900 cursor-pointer ${!notif.readAt ? 'bg-lime-500/[0.02]' : ''}`}>
                  <div className={`w-12 h-12 rounded-xl border flex items-center justify-center shrink-0 mt-1 shadow-sm ${style.bg}`}>{style.icon}</div>
                  <div className="flex flex-col flex-grow min-w-0">
                    <div className="flex justify-between items-start gap-4">
                      <p className={`text-lg leading-tight truncate ${!notif.readAt ? 'text-white font-bold' : 'text-zinc-400 font-medium'}`}>{title}</p>
                      <span className="text-[12px] text-zinc-600 font-bold uppercase whitespace-nowrap mt-1">{timeAgo(notif.createdAt)}</span>
                    </div>
                    {desc && <p className="text-zinc-500 text-sm mt-1 leading-relaxed">{desc}</p>}
                  </div>
                  {!notif.readAt && <div className="w-2.5 h-2.5 rounded-full bg-lime-500 shadow-[0_0_10px_rgba(132,204,22,0.5)] shrink-0 mt-3"></div>}
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}