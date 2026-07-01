import { useEffect, useState } from "react";

import { PREP_PARTS } from "../types/autoPrep";
import type { PartState, PartUiStatus } from "../hooks/useAutoPrepRun";

interface Props {
  running: boolean;
  parts: PartState[];
  caseId: number | null;
  onNavigate: (path: string) => void;
}

/** 작업 과정 타임라인 — 전체 진행바 + 파트별 세부스텝 에너지바 + 완료 후 액션. 채팅 모달 안에 임베드된다. */
export function AutoPrepWorkView({ running, parts, caseId, onNavigate }: Props) {
  if (parts.length === 0) {
    return null;
  }
  const done = parts.filter((p) => p.status === "done").length;
  const skipped = parts.filter((p) => p.status === "skipped").length;
  const failed = parts.filter((p) => p.status === "failed").length;
  const settled = parts.filter((p) => p.status !== "pending" && p.status !== "running").length;
  const total = parts.length;
  const overallPct = Math.round((settled / total) * 100);
  const finished = !running && parts.every((p) => p.status !== "pending" && p.status !== "running");

  return (
    <div className="rounded-xl border border-border bg-card p-3">
      <div className="mb-2">
        <div className="mb-1 flex items-center justify-between text-[11px] text-muted-foreground">
          <span>✦ 작업 과정</span>
          <span className="tabular-nums font-semibold">{settled}/{total}</span>
        </div>
        <div className="h-1.5 w-full overflow-hidden rounded-full bg-secondary">
          <div
            className="h-full rounded-full bg-primary transition-all duration-300 ease-out"
            style={{ width: `${overallPct}%` }}
          />
        </div>
      </div>

      {parts.map((p) => (
        <PartGroup key={p.key} part={p} caseId={caseId} onNavigate={onNavigate} />
      ))}

      {finished && (
        <div className="mt-2 flex flex-wrap items-center gap-2 border-t border-border pt-3">
          <span className="text-xs text-muted-foreground">
            <b className="text-foreground">준비 완료</b> · 완료 {done} · 건너뜀 {skipped} · 실패 {failed}
          </span>
          <div className="ml-auto flex gap-2">
            {caseId && (
              <button
                onClick={() => onNavigate(`/applications/${caseId}`)}
                className="rounded-lg border border-border px-3 py-2 text-xs font-semibold text-foreground transition hover:bg-secondary"
              >
                지원 건 열기
              </button>
            )}
            <button
              onClick={() => onNavigate(caseId ? `/interview?caseId=${caseId}&tab=modes` : "/interview")}
              className="rounded-lg bg-primary px-3.5 py-2 text-xs font-semibold text-primary-foreground transition hover:brightness-110"
            >
              ▶ 면접 시작
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

function PartGroup({
  part,
  caseId,
  onNavigate,
}: {
  part: PartState;
  caseId: number | null;
  onNavigate: (path: string) => void;
}) {
  const meta = PREP_PARTS[part.key] ?? { label: part.key, icon: "•", part: "", estMs: 12000 };
  const action = part.status === "done" ? actionFor(part.key, caseId) : null;
  const elapsed = usePartElapsed(part.status);
  const count = Math.max(1, part.substeps.length);
  const segMs = meta.estMs / count;

  return (
    <div className="py-2">
      <div className="mb-0.5 flex items-center gap-2">
        <span className="text-[11px] font-bold tracking-wide text-foreground">{meta.label}</span>
        <span className="text-[9px] text-muted-foreground">·{meta.part}</span>
        <span className="ml-auto text-[10px] font-semibold">
          {part.status === "running" && <span className="text-primary">진행</span>}
          {part.status === "done" && <span className="text-primary">✓ 완료</span>}
          {part.status === "skipped" && <span className="text-muted-foreground">⤼ 건너뜀</span>}
          {part.status === "failed" && <span className="text-destructive">실패</span>}
        </span>
      </div>

      {part.substeps.map((s, i) => {
        const pct = substepPct(part.status, i, count, segMs, elapsed);
        const active = part.status === "running" && elapsed >= i * segMs && pct < 100;
        const isLast = i === part.substeps.length - 1;
        const showBar = part.status === "running" || part.status === "done";
        return (
          <div key={i} className="flex gap-2.5 py-1">
            <div className="flex flex-col items-center">
              <div className="grid h-6 w-6 flex-none place-items-center rounded-full bg-primary/10 text-xs text-primary">
                {active ? <Spinner /> : pct >= 100 ? "✓" : meta.icon}
              </div>
              {!isLast && <div className="mt-1 w-px flex-1 bg-border" />}
            </div>
            <div className="min-w-0 flex-1">
              <div className="text-xs font-semibold text-foreground">{s.name}</div>
              <div className="mb-1 text-[11px] text-muted-foreground">{s.desc}</div>
              {showBar && (
                <div className="h-1 w-full overflow-hidden rounded-full bg-secondary">
                  <div
                    className="h-full rounded-full bg-primary transition-all duration-150 ease-out"
                    style={{ width: `${pct}%` }}
                  />
                </div>
              )}
            </div>
          </div>
        );
      })}

      {part.result && part.status !== "running" && (
        <div className="ml-8 mt-1 flex flex-wrap items-center gap-2">
          <span className="text-[11px] text-muted-foreground">{part.result.summary}</span>
          {action && (
            <button
              onClick={() => onNavigate(action.path)}
              className="rounded-md border border-border px-2 py-0.5 text-[10px] font-semibold text-foreground transition hover:bg-secondary"
            >
              {action.label} ↗
            </button>
          )}
        </div>
      )}
    </div>
  );
}

function usePartElapsed(status: PartUiStatus): number {
  const [elapsed, setElapsed] = useState(0);
  useEffect(() => {
    if (status !== "running") {
      return;
    }
    const start = Date.now();
    const id = setInterval(() => setElapsed(Date.now() - start), 100);
    return () => clearInterval(id);
  }, [status]);
  return status === "running" ? elapsed : 0;
}

function substepPct(status: PartUiStatus, i: number, count: number, segMs: number, elapsed: number): number {
  if (status === "done") {
    return 100;
  }
  if (status !== "running") {
    return 0;
  }
  const segElapsed = elapsed - i * segMs;
  if (segElapsed < 0) {
    return 0;
  }
  const isLast = i === count - 1;
  if (!isLast && elapsed >= (i + 1) * segMs) {
    return 100;
  }
  const cap = isLast ? 95 : 100;
  return Math.min(cap, cap * (1 - Math.exp(-segElapsed / (segMs * 0.55))));
}

function actionFor(key: string, caseId: number | null): { label: string; path: string } | null {
  switch (key) {
    case "INTERVIEW":
      // caseId 를 실어 챗봇이 인계 표식을 남기고, 면접 페이지가 지원 건을 선택할 수 있게 한다.
      return { label: "면접 시작", path: caseId ? `/interview?caseId=${caseId}&tab=modes` : "/interview" };
    case "JOB":
    case "FIT":
      return caseId ? { label: "지원 건 열기", path: `/applications/${caseId}` } : null;
    case "COMMUNITY":
      return { label: "커뮤니티", path: "/community" };
    default:
      return null;
  }
}

function Spinner() {
  return (
    <span className="inline-block h-3 w-3 animate-spin rounded-full border-2 border-border border-t-primary" />
  );
}
