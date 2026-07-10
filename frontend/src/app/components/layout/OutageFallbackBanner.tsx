import { useEffect } from "react";
import { CircleCheck, Database, RefreshCw, TriangleAlert } from "lucide-react";
import {
  probeOutageRecovery,
  startOutageFallbackMonitor,
  useOutageFallback,
} from "@/app/lib/outageFallback";

export function OutageFallbackBanner() {
  const { mode, checking } = useOutageFallback();

  useEffect(() => startOutageFallbackMonitor(), []);

  if (mode === "real") return null;

  if (mode === "restoring") {
    return (
      <div
        role="status"
        aria-live="polite"
        className="flex items-center justify-center gap-2 bg-emerald-600 px-4 py-2 text-center text-xs font-semibold text-white dark:bg-emerald-800"
      >
        <CircleCheck className="size-3.5 shrink-0" aria-hidden="true" />
        실제 서비스 연결이 복구되어 안전하게 다시 연결하고 있습니다.
      </div>
    );
  }

  if (mode === "static-demo") {
    return (
      <div
        role="status"
        aria-live="polite"
        className="flex items-center justify-center gap-2 bg-blue-700 px-4 py-2 text-center text-xs font-semibold text-white dark:bg-blue-900"
      >
        <Database className="size-3.5 shrink-0" aria-hidden="true" />
        데모 데이터로 체험 중입니다. 입력한 변경사항은 실제 계정에 저장되지 않습니다.
      </div>
    );
  }

  return (
    <div
      role="alert"
      aria-live="assertive"
      className="flex flex-wrap items-center justify-center gap-x-3 gap-y-1 bg-amber-500 px-4 py-2 text-center text-xs font-semibold text-slate-950 dark:bg-amber-600 dark:text-white"
    >
      <span className="inline-flex items-center gap-2">
        <TriangleAlert className="size-3.5 shrink-0" aria-hidden="true" />
        AWS 연결 장애로 체험 데이터를 표시합니다. 변경사항은 저장되지 않습니다.
      </span>
      <button
        type="button"
        onClick={() => void probeOutageRecovery()}
        disabled={checking}
        className="inline-flex items-center gap-1 rounded-md border border-slate-900/20 bg-white/50 px-2 py-0.5 font-bold hover:bg-white/70 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-slate-950 disabled:cursor-wait disabled:opacity-60 dark:border-white/30 dark:bg-black/15 dark:hover:bg-black/25 dark:focus-visible:ring-white"
      >
        <RefreshCw className={`size-3 ${checking ? "animate-spin" : ""}`} aria-hidden="true" />
        {checking ? "복구 확인 중" : "지금 다시 확인"}
      </button>
    </div>
  );
}
