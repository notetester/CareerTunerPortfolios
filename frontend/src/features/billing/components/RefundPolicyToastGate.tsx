import { useEffect, useRef } from "react";
import { toast } from "@/features/notification/components/toast";
import { acknowledgeRefundPolicy, getCurrentRefundPolicy } from "../api/refundPolicyApi";

const POLICY_POLL_INTERVAL_MS = 30_000;

export function RefundPolicyToastGate({ enabled }: { enabled: boolean }) {
  const shownPolicyIds = useRef(new Set<number>());

  useEffect(() => {
    if (!enabled) return;
    let disposed = false;
    let timer: ReturnType<typeof setInterval> | undefined;

    const check = async () => {
      try {
        const policy = await getCurrentRefundPolicy();
        if (disposed
          || policy.acknowledgedTriggers.includes("NOTICE")
          || shownPolicyIds.current.has(policy.id)) {
          return;
        }
        shownPolicyIds.current.add(policy.id);
        toast.notify({
          type: "NOTICE",
          category: "notice",
          title: `${policy.title}이 변경되었습니다.`,
          message: `${formatEffectiveDate(policy.effectiveAt)}부터 적용 · ${shortSummary(policy.summary)}`,
          link: "/support/notices",
          duration: 10_000,
          showProgress: true,
        });
        await acknowledgeRefundPolicy(policy.id, "NOTICE");
      } catch {
        // 정책 알림 실패가 앱 사용을 막아서는 안 된다. 다음 폴링/재접속에서 다시 시도한다.
      }
    };

    void check();
    timer = setInterval(() => { void check(); }, POLICY_POLL_INTERVAL_MS);
    return () => {
      disposed = true;
      if (timer) clearInterval(timer);
    };
  }, [enabled]);

  return null;
}

function formatEffectiveDate(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value.replace("T", " ");
  return new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium", timeStyle: "short" }).format(date);
}

function shortSummary(value: string | null) {
  const summary = value?.trim() || "자세히 보기";
  return summary.length > 70 ? `${summary.slice(0, 70)}…` : summary;
}
