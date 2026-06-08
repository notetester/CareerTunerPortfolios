import { ArrowRight, type LucideIcon } from "lucide-react";
import type { Notification } from "../types/notification";

interface NotificationItemProps {
  notification: Notification;
  icon?: LucideIcon;
  onRead: (id: number) => void;
}

export function NotificationItem({ notification: n, icon: Icon, onRead }: NotificationItemProps) {
  return (
    <div
      className={`ct-noti ${!n.isRead ? "unread" : ""}`}
      onClick={() => onRead(n.id)}
    >
      {!n.isRead && <span className="ct-noti__dot" />}
      <div className={`ct-noti__ic ic-${n.category}`}>
        {Icon && <Icon />}
      </div>
      <div className="ct-noti__body">
        <div className="ct-noti__t">{n.title}</div>
        {n.message && <div className="ct-noti__m">{n.message}</div>}
        {n.link && (
          <span className="ct-noti__link">{n.link} <ArrowRight /></span>
        )}
      </div>
      <div className="ct-noti__time">{n.createdAt}</div>
    </div>
  );
}
