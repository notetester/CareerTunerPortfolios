import { useState, useEffect, useRef } from "react";
import { Bell, BellOff } from "lucide-react";
import { useNavigate } from "react-router";
import { useNotificationStore } from "../hooks/useNotificationStore";
import { typeMeta, relTime } from "../types/notification";
import { ICON_MAP } from "./iconMap";
import { Avatar, AvatarFallback } from "@/app/components/ui/avatar";
import type { Notification } from "../types/notification";
import type { LucideIcon } from "lucide-react";

function BellNotiIcon({ n }: { n: Notification }) {
  const meta = typeMeta(n.type);
  const Icon: LucideIcon | undefined = ICON_MAP[meta.icon];

  if (meta.actor && n.actorName) {
    return (
      <span className="ct-naw">
        <Avatar className="w-9 h-9">
          <AvatarFallback className="text-xs bg-muted">{n.actorName[0]}</AvatarFallback>
        </Avatar>
        <span className={`ct-naw__badge ic-${meta.cat}`}>
          {Icon && <Icon />}
        </span>
      </span>
    );
  }

  return (
    <span className={`ct-belln__ic ic-${meta.cat}`}>
      {Icon && <Icon />}
    </span>
  );
}

export function NotificationBell() {
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLSpanElement>(null);
  const {
    notifications, unreadCount,
    fetchNotifications, markAsRead, markAllAsRead,
  } = useNotificationStore();

  useEffect(() => {
    fetchNotifications();
  }, [fetchNotifications]);

  // 바깥 클릭 / Esc 닫기
  useEffect(() => {
    if (!open) return;
    const onDoc = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    const onEsc = (e: KeyboardEvent) => {
      if (e.key === "Escape") setOpen(false);
    };
    document.addEventListener("mousedown", onDoc);
    document.addEventListener("keydown", onEsc);
    return () => {
      document.removeEventListener("mousedown", onDoc);
      document.removeEventListener("keydown", onEsc);
    };
  }, [open]);

  const recent = notifications.slice(0, 6);

  const handleItem = (n: Notification) => {
    markAsRead(n.id);
    setOpen(false);
    if (n.link) navigate(n.link);
  };

  const handleSeeAll = () => {
    setOpen(false);
    navigate("/notifications");
  };

  return (
    <span className="ct-belln" ref={ref}>
      <button
        className="relative p-2 rounded-lg hover:bg-slate-100 transition-colors"
        aria-label="알림"
        onClick={() => setOpen((o) => !o)}
      >
        <Bell className="size-5 text-slate-600" />
      </button>
      {unreadCount > 0 && (
        <span className="ct-belln__badge">{unreadCount > 9 ? "9+" : unreadCount}</span>
      )}

      {open && (
        <div className="ct-belln__panel" role="dialog" aria-label="알림">
          {/* Header */}
          <div className="ct-belln__head">
            <h4>
              <Bell className="size-4" /> 알림
              {unreadCount > 0 && <span className="ct-belln__hcount">{unreadCount}</span>}
            </h4>
            <button
              className="ct-belln__markall"
              disabled={unreadCount === 0}
              onClick={() => markAllAsRead()}
            >
              모두 읽음
            </button>
          </div>

          {/* List */}
          <div className="ct-belln__list">
            {recent.length === 0 ? (
              <div className="ct-belln__empty">
                <BellOff className="size-7" />
                <span>새로운 알림이 없습니다</span>
              </div>
            ) : (
              recent.map((n) => (
                <div
                  className={`ct-belln__row ${!n.isRead ? "unread" : ""}`}
                  key={n.id}
                  onClick={() => handleItem(n)}
                >
                  {!n.isRead && <span className="ct-belln__dot" />}
                  <BellNotiIcon n={n} />
                  <div className="ct-belln__b">
                    <div className="ct-belln__t">{n.title}</div>
                    <div className="ct-belln__m">{n.message}</div>
                    <div className="ct-belln__time">{relTime(n.createdAt)}</div>
                  </div>
                </div>
              ))
            )}
          </div>

          {/* Footer */}
          <div className="ct-belln__foot">
            <button onClick={handleSeeAll}>전체 알림 보기</button>
          </div>
        </div>
      )}
    </span>
  );
}
