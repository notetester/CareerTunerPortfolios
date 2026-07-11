import { useEffect } from "react";
import { useNotificationStore } from "../hooks/useNotificationStore";

/** 헤더가 없는 네이티브 홈에서도 계정별 알림 조회와 포그라운드 폴링을 유지한다. */
const UNREAD_POLL_INTERVAL_MS = 30_000;

export function NotificationRuntime() {
  const accountId = useNotificationStore((state) => state.accountId);
  const fetchNotifications = useNotificationStore((state) => state.fetchNotifications);
  const pollNotifications = useNotificationStore((state) => state.pollNotifications);

  useEffect(() => {
    if (accountId != null) void fetchNotifications();
  }, [accountId, fetchNotifications]);

  useEffect(() => {
    if (accountId == null) return undefined;
    let timer: ReturnType<typeof setInterval> | undefined;
    const stop = () => {
      if (!timer) return;
      clearInterval(timer);
      timer = undefined;
    };
    const start = () => {
      if (timer || document.hidden) return;
      timer = setInterval(() => { void pollNotifications(); }, UNREAD_POLL_INTERVAL_MS);
    };
    const onVisibility = () => {
      if (document.hidden) {
        stop();
      } else {
        void pollNotifications();
        start();
      }
    };
    start();
    document.addEventListener("visibilitychange", onVisibility);
    return () => {
      stop();
      document.removeEventListener("visibilitychange", onVisibility);
    };
  }, [accountId, pollNotifications]);

  return null;
}
