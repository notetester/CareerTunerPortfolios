import { useEffect, useState } from "react";
import { api } from "@/app/lib/api";

/**
 * 관리자 사이드바 미처리 큐 카운트 폴링.
 * 개인 알림(useNotificationStore)과 별개 — 관리자 페이지(AdminShell)에서만 마운트한다.
 * SSE 대신 30초 폴링(관리자 알림 토스트가 실시간을 담당하므로 사이드바 카운트는 폴링으로 충분).
 */
const POLL_INTERVAL_MS = 30_000;

export type PendingSeverity = "RED" | "YELLOW" | "NONE";

export interface PendingQueueBadge {
  count: number;
  severity: PendingSeverity;
}

export interface AdminPendingCounts {
  reports: PendingQueueBadge;        // 신고 PENDING
  hiddenPosts: PendingQueueBadge;    // 자동숨김 게시글 HIDDEN
  hiddenComments: PendingQueueBadge; // 자동숨김 댓글 HIDDEN
  tickets: PendingQueueBadge;        // 미응답 티켓 RECEIVED
}

const SEVERITY_RANK: Record<PendingSeverity, number> = { NONE: 0, YELLOW: 1, RED: 2 };

/** 여러 큐 배지 중 가장 높은 severity(RED>YELLOW>NONE). 사이드바 합산 배지 색 판정용. */
export function topSeverity(...badges: (PendingQueueBadge | undefined)[]): PendingSeverity {
  let top: PendingSeverity = "NONE";
  for (const b of badges) {
    if (b && SEVERITY_RANK[b.severity] > SEVERITY_RANK[top]) top = b.severity;
  }
  return top;
}

/** 여러 큐 배지의 count 합. */
export function sumCounts(...badges: (PendingQueueBadge | undefined)[]): number {
  return badges.reduce((sum, b) => sum + (b?.count ?? 0), 0);
}

export function useAdminPendingCounts(): AdminPendingCounts | null {
  const [counts, setCounts] = useState<AdminPendingCounts | null>(null);

  useEffect(() => {
    let cancelled = false;
    let timer: ReturnType<typeof setInterval> | undefined;

    const fetchOnce = async () => {
      try {
        const data = await api<AdminPendingCounts>("/admin/pending-counts", { method: "GET" });
        if (!cancelled) setCounts(data);
      } catch {
        // 폴링 실패는 조용히 무시(다음 주기 재시도)
      }
    };
    const start = () => {
      if (!timer) timer = setInterval(fetchOnce, POLL_INTERVAL_MS);
    };
    const stop = () => {
      if (timer) { clearInterval(timer); timer = undefined; }
    };
    const onVisibility = () => {
      if (document.hidden) {
        stop();
      } else {
        fetchOnce();
        start();
      }
    };

    fetchOnce();
    if (!document.hidden) start();
    document.addEventListener("visibilitychange", onVisibility);
    return () => {
      cancelled = true;
      stop();
      document.removeEventListener("visibilitychange", onVisibility);
    };
  }, []);

  return counts;
}
