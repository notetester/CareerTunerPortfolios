import type { NotificationCategory } from "../types/notification";
import { NOTIFICATION_CATEGORIES } from "../types/notification";

interface NotificationFilterTabsProps {
  filter: NotificationCategory;
  onFilterChange: (value: NotificationCategory) => void;
}

export function NotificationFilterTabs({ filter, onFilterChange }: NotificationFilterTabsProps) {
  return (
    <div className="ct-noti__tabs" style={{ display: "flex", flexWrap: "wrap", gap: 6 }}>
      {NOTIFICATION_CATEGORIES.map((c) => (
        <button
          key={c.value}
          onClick={() => onFilterChange(c.value as NotificationCategory)}
          style={
            filter === c.value
              ? { background: "var(--gradient-brand)", color: "#fff", border: "1px solid transparent", borderRadius: 9999, padding: "7px 15px", fontSize: 14, fontWeight: 600, cursor: "pointer" }
              : { background: "var(--card)", color: "var(--muted-foreground)", border: "1px solid var(--border)", borderRadius: 9999, padding: "7px 15px", fontSize: 14, fontWeight: 500, cursor: "pointer" }
          }
        >
          {c.label}
        </button>
      ))}
    </div>
  );
}
