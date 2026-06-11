import { BellOff, ChevronDown } from "lucide-react";
import type { Notification } from "../types/notification";
import { NotificationItem } from "./NotificationItem";

interface NotificationListProps {
  items: Notification[];
  loading: boolean;
  hasMore: boolean;
  remaining: number;
  onLoadMore: () => void;
  onRead: (id: number) => void;
}

export function NotificationList({ items, loading, hasMore, remaining, onLoadMore, onRead }: NotificationListProps) {
  if (loading) {
    return (
      <p style={{ textAlign: "center", color: "var(--muted-foreground)", padding: "48px 0" }}>
        불러오는 중...
      </p>
    );
  }

  if (items.length === 0) {
    return (
      <div className="ct-noti__empty">
        <div className="ct-noti__emptyic"><BellOff /></div>
        <h3>새로운 알림이 없습니다</h3>
        <p>활동이 생기면 여기에서 가장 먼저 알려드릴게요.</p>
      </div>
    );
  }

  return (
    <>
      <div className="ct-notilist">
        {items.map((n) => (
          <NotificationItem
            key={n.id}
            notification={n}
            onRead={onRead}
          />
        ))}
      </div>

      {hasMore && (
        <div className="ct-noti__more">
          <button className="ct-act" onClick={onLoadMore}>
            더보기 ({remaining}) <ChevronDown />
          </button>
        </div>
      )}
    </>
  );
}
