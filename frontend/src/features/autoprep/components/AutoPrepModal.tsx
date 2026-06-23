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
  onStartInterview?: () => void;
}

/** 작업 과정 팝업. SSE 진행을 6파트 × 세부 서브스텝 타임라인으로 보여준다. */
export function AutoPrepModal({ open, onClose, running, parts, error, onStartInterview }: Props) {
  if (!open) return null;

  const done = parts.filter((p) => p.status === "done").length;
  const skipped = parts.filter((p) => p.status === "skipped").length;
  const failed = parts.filter((p) => p.status === "failed").length;
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

        <div className="flex-1 overflow-y-auto px-5 py-3">
          {error && <div className="py-2 text-sm text-destructive">{error}</div>}
          {parts.length === 0 && running && (
            <div className="flex items-center gap-2 py-6 text-sm text-muted-foreground">
              <Spinner /> 요청을 해석하는 중…
            </div>
          )}
          {parts.map((p) => (
            <PartGroup key={p.key} part={p} />
          ))}
        </div>

        {finished && (
          <div className="flex flex-wrap items-center justify-between gap-4 border-t border-border px-5 py-4">
            <div className="text-sm text-muted-foreground">
              <b className="text-foreground">준비 완료</b> · 완료 {done} · 건너뜀 {skipped} · 실패 {failed}
            </div>
            {onStartInterview && (
              <button
                onClick={onStartInterview}
                className="rounded-lg bg-primary px-4 py-2.5 text-sm font-semibold text-primary-foreground transition hover:brightness-110"
              >
                ▶ 면접 시작하기
              </button>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

function PartGroup({ part }: { part: PartState }) {
  const meta = PREP_PARTS[part.key] ?? { label: part.key, icon: "•", part: "" };
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

      {part.substeps.map((s, i) => (
        <div key={i} className="flex gap-3 py-2">
          <div className="flex flex-col items-center">
            <div className="grid h-7 w-7 flex-none place-items-center rounded-full bg-primary/10 text-sm text-primary">
              {meta.icon}
            </div>
            {i < part.substeps.length - 1 && <div className="mt-1 w-px flex-1 bg-border" />}
          </div>
          <div className="flex-1 pt-0.5">
            <div className="text-[13px] font-semibold text-foreground">{s.name}</div>
            <div className="mt-0.5 text-xs text-muted-foreground">{s.desc}</div>
          </div>
        </div>
      ))}

      {part.result && part.status !== "running" && (
        <div className="ml-10 text-xs text-muted-foreground">{part.result.summary}</div>
      )}
    </div>
  );
}

function Spinner() {
  return (
    <span className="inline-block h-3 w-3 animate-spin rounded-full border-2 border-border border-t-primary" />
  );
}
