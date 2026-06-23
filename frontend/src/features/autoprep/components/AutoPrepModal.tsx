import { InterviewProgressBar } from "@/features/interview/components/InterviewProgressBar";

import { PREP_PARTS } from "../types/autoPrep";
import type { PrepPlan } from "../types/autoPrep";
import type { PartState } from "../hooks/useAutoPrepRun";

interface Props {
  open: boolean;
  onClose: () => void;
  running: boolean;
  plan: PrepPlan | null;
  parts: PartState[];
  message: string | null;
  error: string | null;
  onNavigate: (path: string) => void;
}

/** 작업 과정 팝업. 전체 진행바 + 파트별 에너지바(시간 기반) + 세부스텝, 완료 후 다음 액션까지. */
export function AutoPrepModal({ open, onClose, running, plan, parts, error, onNavigate }: Props) {
  if (!open) return null;

  const caseId = plan?.slots.applicationCaseId ?? null;
  const done = parts.filter((p) => p.status === "done").length;
  const skipped = parts.filter((p) => p.status === "skipped").length;
  const failed = parts.filter((p) => p.status === "failed").length;
  const settled = parts.filter((p) => p.status !== "pending" && p.status !== "running").length;
  const total = parts.length || 6;
  const overallPct = total ? Math.round((settled / total) * 100) : 0;
  const finished =
    !running && parts.length > 0 && parts.every((p) => p.status !== "pending" && p.status !== "running");

  return (
    <div
      className="fixed inset-0 z-50 flex items-start justify-center bg-black/60 p-4 pt-[6vh] backdrop-blur-sm"
      onClick={onClose}
    >
      <div
        className="flex max-h-[88vh] w-full max-w-[760px] flex-col overflow-hidden rounded-2xl border border-border bg-card shadow-2xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between border-b border-border px-5 py-4">
          <div className="flex items-center gap-2 text-sm font-semibold text-foreground">
            <span aria-hidden>✦</span> AI 오케스트레이터 작업 과정
          </div>
          <div className="flex items-center gap-3">
            {running ? (
              <span className="flex items-center gap-2 text-xs text-muted-foreground">
                <Spinner /> 진행 중
              </span>
            ) : (
              <span className="text-xs font-medium text-primary">✓ 완료</span>
            )}
            <button
              onClick={onClose}
              className="text-lg text-muted-foreground transition-colors hover:text-foreground"
              aria-label="닫기"
            >
              ✕
            </button>
          </div>
        </div>

        {/* 전체 진행바 */}
        {parts.length > 0 && (
          <div className="px-5 pt-3">
            <div className="mb-1 flex items-center justify-between text-xs text-muted-foreground">
              <span>전체 진행</span>
              <span className="tabular-nums font-semibold">{settled}/{total}</span>
            </div>
            <div className="h-1.5 w-full overflow-hidden rounded-full bg-secondary">
              <div
                className="h-full rounded-full bg-primary transition-all duration-300 ease-out"
                style={{ width: `${overallPct}%` }}
              />
            </div>
          </div>
        )}

        <div className="flex-1 overflow-y-auto px-5 py-3">
          {error && <div className="py-2 text-sm text-destructive">{error}</div>}
          {parts.length === 0 && running && (
            <div className="flex items-center gap-2 py-6 text-sm text-muted-foreground">
              <Spinner /> 요청을 해석하는 중…
            </div>
          )}
          {parts.map((p) => (
            <PartGroup key={p.key} part={p} caseId={caseId} onNavigate={onNavigate} />
          ))}
        </div>

        {finished && (
          <div className="flex flex-wrap items-center justify-between gap-3 border-t border-border px-5 py-4">
            <div className="text-sm text-muted-foreground">
              <b className="text-foreground">준비 완료</b> · 완료 {done} · 건너뜀 {skipped} · 실패 {failed}
            </div>
            <div className="flex flex-wrap gap-2">
              {caseId && (
                <button
                  onClick={() => onNavigate(`/applications/${caseId}`)}
                  className="rounded-lg border border-border px-3.5 py-2.5 text-sm font-semibold text-foreground transition hover:bg-secondary"
                >
                  지원 건 열기
                </button>
              )}
              <button
                onClick={() => onNavigate("/interview")}
                className="rounded-lg bg-primary px-4 py-2.5 text-sm font-semibold text-primary-foreground transition hover:brightness-110"
              >
                ▶ 면접 시작하기
              </button>
            </div>
          </div>
        )}
      </div>
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

  return (
    <div className="py-3">
      <div className="mb-1 flex items-center gap-2">
        <span className="text-xs font-bold tracking-wide text-foreground">{meta.label}</span>
        <span className="text-[10px] text-muted-foreground">·{meta.part}</span>
        <span className="ml-auto text-xs font-semibold">
          {part.status === "running" && <span className="text-primary">진행</span>}
          {part.status === "done" && <span className="text-primary">✓ 완료</span>}
          {part.status === "skipped" && <span className="text-muted-foreground">⤼ 건너뜀</span>}
          {part.status === "failed" && <span className="text-destructive">실패</span>}
        </span>
      </div>

      {part.substeps.map((s, i) => {
        const isLast = i === part.substeps.length - 1;
        const subRunning = part.status === "running" && isLast;
        return (
          <div key={i} className="flex gap-3 py-1.5">
            <div className="flex flex-col items-center">
              <div className="grid h-7 w-7 flex-none place-items-center rounded-full bg-primary/10 text-sm text-primary">
                {subRunning ? <Spinner /> : meta.icon}
              </div>
              {i < part.substeps.length - 1 && <div className="mt-1 w-px flex-1 bg-border" />}
            </div>
            <div className="flex-1 pt-0.5">
              <div className="text-[13px] font-semibold text-foreground">{s.name}</div>
              <div className="mt-0.5 text-xs text-muted-foreground">{s.desc}</div>
            </div>
          </div>
        );
      })}

      {/* 파트 진행 중 — LLM 블랙박스라 시간 기반 에너지바로 진행감을 준다 */}
      {part.status === "running" && (
        <div className="ml-10 mt-1.5">
          <InterviewProgressBar active estimatedMs={meta.estMs} label="AI가 처리 중이에요" />
        </div>
      )}

      {part.result && part.status !== "running" && (
        <div className="ml-10 mt-1 flex flex-wrap items-center gap-2">
          <span className="text-xs text-muted-foreground">{part.result.summary}</span>
          {action && (
            <button
              onClick={() => onNavigate(action.path)}
              className="rounded-md border border-border px-2 py-1 text-[11px] font-semibold text-foreground transition hover:bg-secondary"
            >
              {action.label} ↗
            </button>
          )}
        </div>
      )}
    </div>
  );
}

function actionFor(key: string, caseId: number | null): { label: string; path: string } | null {
  switch (key) {
    case "INTERVIEW":
      return { label: "면접 시작", path: "/interview" };
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
