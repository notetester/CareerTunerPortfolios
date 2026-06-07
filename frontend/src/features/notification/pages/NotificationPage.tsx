import { useState, useEffect } from "react";
import { CheckCheck } from "lucide-react";
import type { NotificationCategory } from "../types/notification";
import { useNotificationStore } from "../hooks/useNotificationStore";
import { NotificationFilterTabs } from "../components/NotificationFilterTabs";
import { NotificationList } from "../components/NotificationList";
import "../styles/notification.css";

const PAGE_SIZE = 6;

export default function NotificationPage() {
  const {
    unreadCount, loading,
    filter, setFilter, filtered,
    fetchNotifications, markAsRead, markAllAsRead,
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
        <button
          className="ct-act"
          onClick={markAllAsRead}
          disabled={unreadCount === 0}
          style={unreadCount === 0 ? { opacity: 0.5, cursor: "default" } : undefined}
        >
          <CheckCheck /> 모두 읽음
        </button>
      </div>

      {/* Category tabs */}
      <NotificationFilterTabs filter={filter} onFilterChange={handleFilterChange} />

      {/* List */}
      <NotificationList
        items={shown}
        loading={loading}
        hasMore={hasMore}
        remaining={list.length - limit}
        onLoadMore={() => setLimit((l) => l + PAGE_SIZE)}
        onRead={markAsRead}
      />
    </div>
  );
}
