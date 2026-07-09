import { useState, useEffect } from "react";
import { CheckCheck, Trash2 } from "lucide-react";
import type { NotificationCategory } from "../types/notification";
import { useNotificationStore } from "../hooks/useNotificationStore";
import { NotificationFilterTabs } from "../components/NotificationFilterTabs";
import { NotificationList } from "../components/NotificationList";
import { toast } from "../components/toast";
import "../styles/notification.css";

const PAGE_SIZE = 6;

export default function NotificationPage() {
  const {
    notifications, unreadCount, loading, error,
    filter, setFilter, filtered,
    fetchNotifications, markAsRead, markAllAsRead,
    deleteNotification, deleteAll,
  } = useNotificationStore();

  const [limit, setLimit] = useState(PAGE_SIZE);

  useEffect(() => {
    fetchNotifications();
  }, [fetchNotifications]);

  const list = filtered();
  const shown = list.slice(0, limit);
  const hasMore = limit < list.length;

  const handleFilterChange = (value: NotificationCategory) => {
    setFilter(value);
    setLimit(PAGE_SIZE);
  };

  return (
    <div className="ct-page">
      {/* Header */}
      <div className="ct-noti__head">
        <div className="ct-noti__title">
          <h1>알림</h1>
          {unreadCount > 0 && <span className="ct-noti__count">{unreadCount}</span>}
        </div>
        <div className="ct-noti__actions">
          <button
            className="ct-act"
            onClick={async () => {
              try {
                await markAllAsRead();
              } catch {
                toast.error("전체 읽음 처리에 실패했습니다.");
              }
            }}
            disabled={unreadCount === 0}
            style={unreadCount === 0 ? { opacity: 0.5, cursor: "default" } : undefined}
          >
            <CheckCheck /> 모두 읽음
          </button>
          <button
            className="ct-act ct-act--danger"
            onClick={async () => {
              if (notifications.length === 0) return;
              if (!window.confirm("모든 알림을 삭제할까요? 되돌릴 수 없습니다.")) return;
              try {
                await deleteAll();
              } catch {
                toast.error("전체 삭제에 실패했습니다.");
              }
            }}
            disabled={notifications.length === 0}
            style={notifications.length === 0 ? { opacity: 0.5, cursor: "default" } : undefined}
          >
            <Trash2 /> 전체 삭제
          </button>
        </div>
      </div>

      {/* Category tabs */}
      <NotificationFilterTabs filter={filter} onFilterChange={handleFilterChange} />

      {/* List */}
      {error && !loading && (
        <p style={{ textAlign: "center", color: "var(--destructive)", padding: "24px 0", fontSize: 14 }}>
          알림을 불러오지 못했습니다. 잠시 후 다시 시도해주세요.
        </p>
      )}
      <NotificationList
        items={shown}
        loading={loading}
        hasMore={hasMore}
        remaining={list.length - limit}
        onLoadMore={() => setLimit((l) => l + PAGE_SIZE)}
        onRead={async (id) => {
          try {
            await markAsRead(id);
          } catch {
            toast.error("읽음 처리에 실패했습니다.");
          }
        }}
        onDelete={async (id) => {
          try {
            await deleteNotification(id);
          } catch {
            toast.error("알림 삭제에 실패했습니다.");
          }
        }}
      />
    </div>
  );
}
