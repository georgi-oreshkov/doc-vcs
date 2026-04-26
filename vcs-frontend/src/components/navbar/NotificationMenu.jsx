import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Popover, PopoverTrigger, PopoverContent, Button, Badge } from "@heroui/react";
import { Bell, Check, FileSignature, UserPlus, FileText, FileCheck, AlertCircle } from "lucide-react";
import { useAuth } from 'react-oidc-context';
import { getNotifications, connectNotificationsStream, markAsRead } from '../../api/notificationsApi';

function timeAgo(iso) {
  if (!iso) return '';
  const diff = Date.now() - new Date(iso).getTime();
  const m = Math.floor(diff / 60000);
  if (m < 1) return 'Just now';
  if (m < 60) return `${m}m ago`;
  const h = Math.floor(m / 60);
  if (h < 24) return `${h}h ago`;
  return `${Math.floor(h / 24)}d ago`;
}

const getNotificationStyle = (type = '', payload = {}) => {
  const t = String(type).toUpperCase();
  const msg = String(payload?.message || '').toUpperCase();

  if (t.includes('APPROV') || msg.includes('APPROV')) 
    return { icon: <FileCheck className="text-lime-500" size={20} />, bg: "bg-zinc-900 border-zinc-800" };
  if (t.includes('MEMBER') || msg.includes('ADDED')) 
    return { icon: <UserPlus className="text-lime-500" size={20} />, bg: "bg-zinc-900 border-zinc-800" };
  if (t.includes('REVIEW') || t.includes('VERSION')) 
    return { icon: <FileSignature className="text-lime-500" size={20} />, bg: "bg-zinc-900 border-zinc-800" };
  if (t.includes('ERROR') || t.includes('FAIL')) 
    return { icon: <AlertCircle className="text-red-500" size={20} />, bg: "bg-red-500/10 border-red-500/20" };
  
  return { icon: <FileText className="text-zinc-400" size={20} />, bg: "bg-zinc-900 border-zinc-800" };
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

export default function NotificationMenu() {
  const [activeTab, setActiveTab] = useState('unread');
  const [notifications, setNotifications] = useState([]);
  const esRef = useRef(null);
  const navigate = useNavigate();
  const auth = useAuth();

  const unreadCount = notifications.filter(n => !n.readAt).length;

  useEffect(() => {
    let mounted = true;
    if (!auth.isAuthenticated) return;

    const fetchNotifs = () =>
      getNotifications({ unreadOnly: activeTab === 'unread' })
        .then(data => mounted && setNotifications(Array.isArray(data) ? data : []))
        .catch(e => console.error(e));

    fetchNotifs();
    const interval = setInterval(fetchNotifs, 30000);

    if (!esRef.current && auth.user?.access_token) {
      esRef.current = connectNotificationsStream((notif) => {
        setNotifications(prev => prev.some(p => p.id === notif.id) ? prev : [notif, ...prev]);
      });
    }

    return () => {
      mounted = false;
      clearInterval(interval);
    };
  }, [auth.isAuthenticated, auth.user?.access_token, activeTab]);

  const handleAction = async (notif) => {
    const { payload } = parseNotification(notif);
    if (!notif.readAt) {
      markAsRead(notif.id).catch(console.error);
      setNotifications(prev => prev.map(n => n.id === notif.id ? { ...n, readAt: new Date().toISOString() } : n));
    }
    
    // ПОПРАВКА: Покриваме абсолютно всички възможни формати на ключове
    const orgId = payload.organizationId || payload.orgId || payload.organization_id || payload.organizationid || payload.org_id;
    const docId = payload.documentId || payload.docId || payload.document_id || payload.documentid || payload.doc_id;

    if (docId && orgId) {
      navigate(`/organizations/${orgId}/documents/${docId}`);
    } else if (docId) {
      navigate(`/documents/${docId}`);
    } else if (orgId) {
      navigate(`/organizations/${orgId}`);
    }
  };

  const handleMarkAllRead = async () => {
    const unread = notifications.filter(n => !n.readAt);
    await Promise.all(unread.map(n => markAsRead(n.id)));
    setNotifications(prev => prev.map(n => ({ ...n, readAt: n.readAt ?? new Date().toISOString() })));
  };

  return (
    <Popover placement="bottom-end" offset={15} showArrow>
      <Badge content={unreadCount} color="danger" shape="circle" isInvisible={unreadCount === 0} classNames={{ badge: "border-zinc-950" }}>
        <PopoverTrigger>
          <Button isIconOnly variant="light" className="text-zinc-300 hover:text-white rounded-full">
            <Bell size={20} />
          </Button>
        </PopoverTrigger>
      </Badge>

      <PopoverContent className="p-0 bg-zinc-950 border border-zinc-800 rounded-xl w-[90vw] sm:w-[300px] shadow-2xl flex flex-col overflow-hidden">
        
        <div className="flex gap-4 px-4 pt-4 pb-2 border-b border-zinc-800/50 shrink-0">
          {['unread', 'all'].map(tab => (
            <button key={tab} onClick={() => setActiveTab(tab)} className={`pb-2 text-sm font-bold uppercase tracking-wider transition-all ${activeTab === tab ? 'border-b-2 border-lime-500 text-lime-500' : 'border-transparent text-zinc-500 hover:text-zinc-300'}`}>
              {tab === 'unread' ? 'Unread' : 'All'}
            </button>
          ))}
        </div>

        <div onClick={handleMarkAllRead} className="px-4 py-3 flex items-center gap-2 border-b border-zinc-800/50 text-zinc-400 hover:text-white hover:bg-zinc-900 cursor-pointer transition-colors text-xs font-medium uppercase shrink-0">
          <Check size={16} className="text-lime-500" /> Mark all as read
        </div>

        <div className="max-h-[350px] overflow-y-auto custom-scrollbar flex flex-col shrink-0">
          {notifications.length === 0 ? (
            <div className="p-8 text-center text-zinc-600 text-sm">No notifications</div>
          ) : (
            notifications.slice(0, 5).map((notif) => {
              const { title, desc, style } = parseNotification(notif);
              return (
                <div key={notif.id} onClick={() => handleAction(notif)} className={`p-4 flex gap-3 cursor-pointer border-b border-zinc-800/30 last:border-b-0 hover:bg-zinc-900 transition-colors ${!notif.readAt ? 'bg-lime-500/[0.03]' : ''}`}>
                  <div className={`w-10 h-10 rounded-full border flex items-center justify-center shrink-0 ${style.bg}`}>
                    {style.icon}
                  </div>
                  <div className="flex flex-col min-w-0 flex-grow">
                    <p className={`text-sm leading-snug truncate ${!notif.readAt ? 'text-white font-semibold' : 'text-zinc-400'}`}>{title}</p>
                    <p className="text-xs text-zinc-500 line-clamp-2 mt-1">{desc}</p>
                    <span className="text-[11px] text-zinc-600 mt-1.5">{timeAgo(notif.createdAt)}</span>
                  </div>
                </div>
              );
            })
          )}
        </div>

        <button onClick={() => navigate('/notifications')} className="w-full bg-zinc-900/50 p-3 text-center text-xs font-bold text-zinc-400 hover:text-white hover:bg-zinc-900 transition-colors border-t border-zinc-800 tracking-widest uppercase shrink-0">
          VIEW ALL
        </button>
      </PopoverContent>
    </Popover>
  );
}