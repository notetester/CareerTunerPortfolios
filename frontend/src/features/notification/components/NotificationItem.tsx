import { ArrowRight, X, type LucideIcon } from "lucide-react";
import { useNavigate } from "react-router";
import { Avatar, AvatarFallback } from "@/app/components/ui/avatar";
import type { Notification } from "../types/notification";
import { typeMeta, relTime } from "../types/notification";
import { ICON_MAP } from "./iconMap";
import { safeInternalAppPath } from "../lib/navigationLink";

interface NotificationItemProps {
  notification: Notification;
  size?: "sm" | "md";
  onRead: (id: number) => void;
  onDelete?: (id: number) => void;
}

/** 아이콘 사각형 또는 actor 아바타 + 타입 배지 */
function NotiIcon({ n, size }: { n: Notification; size: "sm" | "md" }) {
  const meta = typeMeta(n.type);
  const Icon: LucideIcon | undefined = ICON_MAP[meta.icon];
  const sq = size === "sm" ? "ct-belln__ic" : "ct-noti__ic";

  if (meta.actor && n.actorName) {
    const dim = size === "sm" ? "w-9 h-9" : "w-10 h-10";
    return (
      <span className="ct-naw">
        <Avatar className={dim}>
          <AvatarFallback className="text-xs bg-muted">{n.actorName[0]}</AvatarFallback>
        </Avatar>
        <span className={`ct-naw__badge ic-${meta.cat}`}>
          {Icon && <Icon />}
        </span>
      </span>
    );
  }

  return (
    <div className={`${sq} ic-${meta.cat}`}>
      {Icon && <Icon />}
    </div>
  );
}

export function NotificationItem({ notification: n, size = "md", onRead, onDelete }: NotificationItemProps) {
  const meta = typeMeta(n.type);
  const navigate = useNavigate();
  const navigationTarget = safeInternalAppPath(n.link);

  const handleClick = () => {
    onRead(n.id);
    if (navigationTarget) {
      navigate(navigationTarget);
    }
  };

  return (
    <div
      className={`ct-noti ${!n.isRead ? "unread" : ""}`}
      style={{ cursor: navigationTarget ? "pointer" : undefined }}
      onClick={handleClick}
    >
      {!n.isRead && <span className="ct-noti__dot" />}
      <NotiIcon n={n} size={size} />
      <div className="ct-noti__body">
        <div className="ct-noti__t">{n.title}</div>
        {n.message && <div className="ct-noti__m">{n.message}</div>}
        {navigationTarget && (
          <span className="ct-noti__link">{meta.cta} <ArrowRight /></span>
        )}
      </div>
      <div className="ct-noti__time">{relTime(n.createdAt)}</div>
      {onDelete && (
        <button
          type="button"
          className="ct-noti__del"
          aria-label="알림 삭제"
          onClick={(e) => {
            e.stopPropagation();
            onDelete(n.id);
          }}
        >
          <X />
        </button>
      )}
    </div>
  );
}
