import { useState, useEffect, useRef } from "react";
import { Bell, BellOff, X, Trash2 } from "lucide-react";
import { useNavigate } from "react-router";
import { useNotificationStore } from "../hooks/useNotificationStore";
import { typeMeta, relTime } from "../types/notification";
import { toast } from "./toast";
import { safeInternalAppPath } from "../lib/navigationLink";
import { ICON_MAP } from "./iconMap";
import { Avatar, AvatarFallback } from "@/app/components/ui/avatar";
import type { Notification } from "../types/notification";
import type { LucideIcon } from "lucide-react";

/**
 * 미읽음 카운트 폴링 주기(ms).
 * 알림은 SSE 대신 Web Push(VAPID/FCM)로 실발송하고, 앱이 열려 있는 동안의 배지 갱신은
 * 이 폴링이 담당한다(과투자 방지를 위해 SSE 미사용).
 */
const UNREAD_POLL_INTERVAL_MS = 30_000;

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
    notifications, unreadCount, preference,
    fetchNotifications, pollNotifications, markAsRead, markAllAsRead,
    deleteNotification, deleteAll, fetchPreference, setPushEnabled,
  } = useNotificationStore();

  const pushEnabled = preference?.pushEnabled ?? true;

  useEffect(() => {
    fetchNotifications();
  }, [fetchNotifications]);

  // 알림 폴링 (SSE 대신). 새 알림 도착 시 배지 갱신 + 토스트 팝업.
  // 탭이 숨겨지면 중단하고, 복귀 시 즉시 1회 갱신 후 재개.
  useEffect(() => {
    let timer: ReturnType<typeof setInterval> | undefined;
    const start = () => {
      if (timer) return;
      timer = setInterval(() => { pollNotifications(); }, UNREAD_POLL_INTERVAL_MS);
    };
    const stop = () => {
      if (timer) { clearInterval(timer); timer = undefined; }
    };
    const onVisibility = () => {
      if (document.hidden) {
        stop();
      } else {
        pollNotifications();
        start();
      }
    };
    if (!document.hidden) start();
    document.addEventListener("visibilitychange", onVisibility);
    return () => {
      stop();
      document.removeEventListener("visibilitychange", onVisibility);
    };
  }, [pollNotifications]);

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
    const navigationTarget = safeInternalAppPath(n.link);
    if (navigationTarget) navigate(navigationTarget);
  };

  const handleSeeAll = () => {
    setOpen(false);
    navigate("/notifications");
  };

  return (
    <span className="ct-belln" ref={ref}>
      <button
        className="relative p-2 rounded-lg hover:bg-accent transition-colors"
        aria-label="알림"
        onClick={() => {
          if (!open) {
            fetchNotifications(); // 패널 열 때 최신 목록 동기화
            fetchPreference();    // 끄기 버튼 상태 동기화
          }
          setOpen((o) => !o);
        }}
      >
        <Bell className="size-5 text-muted-foreground" />
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
            <div className="ct-belln__headactions">
              <button
                className="ct-belln__markall"
                disabled={unreadCount === 0}
                onClick={() => markAllAsRead()}
              >
                모두 읽음
              </button>
              <button
                className="ct-belln__clear"
                disabled={notifications.length === 0}
                onClick={async () => {
                  if (notifications.length === 0) return;
                  if (!window.confirm("모든 알림을 삭제할까요? 되돌릴 수 없습니다.")) return;
                  try {
                    await deleteAll();
                  } catch {
                    toast.error("전체 삭제에 실패했습니다.");
                  }
                }}
              >
                <Trash2 /> 전체 삭제
              </button>
            </div>
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
                  <button
                    type="button"
                    className="ct-belln__del"
                    aria-label="알림 삭제"
                    onClick={(e) => {
                      e.stopPropagation();
                      deleteNotification(n.id).catch(() => toast.error("알림 삭제에 실패했습니다."));
                    }}
                  >
                    <X />
                  </button>
                </div>
              ))
            )}
          </div>

          {/* Footer */}
          <div className="ct-belln__foot">
            <div className="ct-belln__footrow">
              <button
                className="ct-belln__toggle"
                onClick={async () => {
                  try {
                    await setPushEnabled(!pushEnabled);
                    toast.success(pushEnabled ? "알림을 껐어요." : "알림을 켰어요.");
                  } catch {
                    toast.error("알림 설정 변경에 실패했습니다.");
                  }
                }}
              >
                {pushEnabled ? <><BellOff className="size-4" /> 알림 끄기</> : <><Bell className="size-4" /> 알림 켜기</>}
              </button>
              <button onClick={handleSeeAll}>전체 알림 보기</button>
            </div>
          </div>
        </div>
      )}
    </span>
  );
}
