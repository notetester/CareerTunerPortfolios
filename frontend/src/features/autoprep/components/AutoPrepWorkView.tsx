import { useRef, useState } from "react";
import { CheckCircle2, Loader2, RotateCcw, Paperclip, ArrowUpRight, Check, type LucideIcon } from "lucide-react";

import {
  PART_ORDER, PART_META, type PartKey, type StatusBand, type FooterHelper,
  runningSubstep, doneSummary, skippedReason, failedReason, actionFor,
  resolveStatusBand, footerHelperText,
} from "../lib/partCopy";
import type { PartState } from "../hooks/useAutoPrepRun";
import "./autoprep-workview.css";

interface Props {
  running: boolean;
  parts: PartState[];
  caseId: number | null;
  /** 상태줄 헤드라인 "{회사} 면접 준비를 하고 있어요" 조립용(없으면 회사명 생략). */
  company?: string | null;
  /** 하단 고정 CTA(지원 건 열기/면접 시작) 노출 여부 — 다른 화면이 자체 CTA/다음 스텝을 갖고 있으면 false. */
  showFooter?: boolean;
  /** 재시도 = 마지막 실행 요청 전체 재실행(부분 재실행 API 없음 — 붙으면 failedOnly 분기 추가).
   *  미전달이면 재시도 버튼 자체를 숨긴다(죽은 버튼 노출 방지). */
  onRetry?: () => void;
  /** 자소서 첨부 = 파일 업로드 후 그 첨부를 실어 재실행. onRetry 와 같은 원칙으로,
   *  미전달이면 첨부 버튼 자체를 숨긴다. reject 하면 카드에 실패 문구를 띄운다. */
  onAttachCoverLetter?: (file: File) => Promise<void>;
  onNavigate: (path: string) => void;
}

/** 백엔드 AutoPrepAttachmentLoader 가 본문을 뽑는 종류만 받는다(text/*, 텍스트 PDF, .docx).
 *  구형 .doc 은 파서가 없어 제외 — 받아봐야 조용히 건너뛴다. */
const COVER_LETTER_ACCEPT =
  ".txt,.md,.pdf,.docx,text/plain,text/markdown,application/pdf," +
  "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

/** AI 오케스트레이터 진행/결과 화면 — 6파트 병렬 실행을 "면접 봐도 되는지"에 답하는 형태로 보여준다. */
export function AutoPrepWorkView({ running, parts, caseId, company = null, showFooter = true, onRetry, onAttachCoverLetter, onNavigate }: Props) {
  if (parts.length === 0) {
    return null;
  }

  const byKey = new Map(parts.map((p) => [p.key, p]));
  const orderedParts = PART_ORDER.map((key) => byKey.get(key)).filter((p): p is PartState => Boolean(p));
  const band = resolveStatusBand(parts, running, company);
  const helper = footerHelperText(band, running);

  return (
    // @container: 그리드/상태줄이 뷰포트가 아니라 이 위젯 자체의 폭(코너 ~360 / 플로팅 ~970)을 따르게.
    <div className="@container overflow-hidden rounded-2xl border border-border bg-card">
      <StatusBandView band={band} parts={parts} onRetry={onRetry} />
      <div className="grid grid-cols-1 gap-2.5 p-3.5 pt-3 @[420px]:grid-cols-2 @[600px]:grid-cols-3">
        {orderedParts.map((part) => (
          <PartCard key={part.key} part={part} allFailed={band.allFailed} caseId={caseId} onRetry={onRetry} onAttachCoverLetter={onAttachCoverLetter} onNavigate={onNavigate} />
        ))}
      </div>
      {showFooter && <FooterBar band={band} helper={helper} caseId={caseId} onNavigate={onNavigate} />}
    </div>
  );
}

function StatusBandView({ band, parts, onRetry }: { band: StatusBand; parts: PartState[]; onRetry?: () => void }) {
  const settled = parts.filter((p) => p.status !== "pending" && p.status !== "running").length;
  const pct = parts.length ? Math.round((settled / parts.length) * 100) : 0;

  return (
    <div className="px-3.5 pt-3.5" style={{ background: "var(--orch-chat-bg)" }}>
      {/* 좁은 폭(코너)에서는 헤드라인 블록과 재시도 버튼을 세로로 쌓아 서로 짓누르지 않게 —
          헤드라인 텍스트는 한 덩어리로 읽히고, 버튼은 폭 관계없이 한 줄(whitespace-nowrap)을 유지. */}
      <div className="flex flex-col gap-2 @[420px]:flex-row @[420px]:items-start @[420px]:justify-between @[420px]:gap-3">
        <div className="min-w-0 flex-1" role="status" aria-live="polite">
          <div className="flex items-center gap-1.5">
            {band.allDone && (
              <span
                className="grid h-[22px] w-[22px] shrink-0 place-items-center rounded-full"
                style={{ background: "var(--success-50)" }}
                aria-hidden
              >
                <Check size={13} style={{ color: "var(--success)" }} strokeWidth={3} />
              </span>
            )}
            <span className="text-[14px] font-extrabold tracking-[-0.01em] text-foreground">{band.headline}</span>
          </div>
          {band.subtext && (
            <div className="mt-0.5 text-[11.5px] leading-[1.5] text-ink-3">{band.subtext}</div>
          )}
        </div>

        {band.retryControl && onRetry && (
          <button
            type="button"
            // 재시도 = 마지막 요청 전체 재실행. 부분 재실행 API 부재 → failedOnly 도 전체 재실행이라
            // "안 된 것만" 라벨은 거짓이 되므로 "다시 시도"로 표기(API 생기면 라벨·동작 분기 복원).
            onClick={onRetry}
            className={`inline-flex h-8 shrink-0 items-center gap-1.5 self-start whitespace-nowrap rounded-full px-3 text-[11.5px] font-bold transition-colors ${
              band.retryControl === "retryAll" ? "text-white" : ""
            }`}
            style={
              band.retryControl === "retryAll"
                ? { background: "var(--gradient-orchestrator)" }
                : { background: "var(--card)", border: "1px solid rgba(94,106,210,0.3)", color: "var(--orch-violet)" }
            }
          >
            <RotateCcw size={13} />
            {band.retryControl === "retryAll" ? "모두 다시 시도" : "다시 시도"}
          </button>
        )}
      </div>

      {band.showProgressBar && (
        <div className="mt-2.5 h-1.5 w-full overflow-hidden rounded-full" style={{ background: "var(--orch-track)" }}>
          <div
            className="h-full rounded-full transition-all duration-500 ease-out"
            style={{ width: `${pct}%`, background: "var(--gradient-orchestrator-bar)" }}
          />
        </div>
      )}
      <div className="h-3" />
    </div>
  );
}

function PartCard({
  part, allFailed, caseId, onRetry, onAttachCoverLetter, onNavigate,
}: {
  part: PartState; allFailed: boolean; caseId: number | null; onRetry?: () => void;
  onAttachCoverLetter?: (file: File) => Promise<void>; onNavigate: (path: string) => void;
}) {
  const meta = PART_META[part.key as PartKey];
  if (!meta) return null;
  const Icon = meta.icon;
  const status = part.status === "pending" ? "running" : part.status;

  if (status === "failed" && allFailed) {
    return (
      <div className="flex h-full flex-col rounded-[13px] p-3" style={{ background: "var(--surface-2)", border: "1px solid var(--border)" }}>
        <TitleRow Icon={Icon} label={meta.label} muted>
          <StatusPill text="실패" tone="dangerMuted" />
        </TitleRow>
        <div className="flex-1 pt-2 text-[12px] leading-[1.55] text-ink-4">{failedReason(true)}</div>
      </div>
    );
  }

  if (status === "running") {
    return (
      <div
        className="flex h-full flex-col rounded-[13px] p-3 transition-opacity duration-150"
        style={{ background: "var(--orch-chat-bg)", border: "1.5px solid rgba(94,106,210,0.4)" }}
      >
        <TitleRow Icon={Icon} label={meta.label}>
          <Loader2 size={15} className="animate-spin" style={{ color: "var(--orch-point)" }} aria-label={`${meta.label} 진행 중`} />
        </TitleRow>
        <div className="flex-1 pt-2 text-[11.5px] font-semibold leading-[1.55]" style={{ color: "var(--orch-label)" }}>
          {runningSubstep(part)}
        </div>
        <div className="mt-2 h-1 w-full overflow-hidden rounded-full" style={{ background: "var(--orch-track)" }}>
          <div
            className="wv-indeterminate h-full w-[45%] rounded-full"
            style={{ background: "var(--gradient-orchestrator-bar)" }}
          />
        </div>
      </div>
    );
  }

  if (status === "skipped") {
    return (
      <div className="flex h-full flex-col rounded-[13px] p-3" style={{ background: "var(--card)", border: "1px dashed var(--border-strong)" }}>
        <TitleRow Icon={Icon} label={meta.label} muted>
          <StatusPill text="건너뜀" tone="neutral" />
        </TitleRow>
        <div className="flex-1 pt-2 text-[12px] leading-[1.55] text-ink-2">{skippedReason(part)}</div>
        {part.key === "WRITE" && onAttachCoverLetter && (
          <div className="pt-1.5">
            <CoverLetterAttachButton onAttach={onAttachCoverLetter} />
          </div>
        )}
      </div>
    );
  }

  if (status === "failed") {
    return (
      <div className="flex h-full flex-col rounded-[13px] p-3" style={{ background: "var(--card)", border: "1px solid rgba(207,34,46,0.25)" }}>
        <TitleRow Icon={Icon} label={meta.label} muted>
          <StatusPill text="실패" tone="danger" />
        </TitleRow>
        <div className="flex-1 pt-2 text-[12px] leading-[1.55] text-ink-2">{failedReason(false)}</div>
        {onRetry && (
          <div className="pt-1.5">
            {/* 파트 단위 재실행 API 가 없어 전체 재실행(이 파트 포함)으로 연결 — 라벨은 거짓이 아니게 유지. */}
            <OutlineButton icon={<RotateCcw size={12} />} label="다시 시도" onClick={onRetry} />
          </div>
        )}
      </div>
    );
  }

  // done
  const summary = doneSummary(part);
  const action = actionFor(part.key, caseId);
  return (
    <div className="flex h-full flex-col rounded-[13px] p-3 transition-opacity duration-150" style={{ background: "var(--card)", border: "1px solid var(--border)" }}>
      <TitleRow Icon={Icon} label={meta.label}>
        <CheckCircle2 size={17} style={{ color: "var(--success)" }} />
      </TitleRow>
      <div className="flex-1 pt-2 text-[12px] leading-[1.55] text-ink-2">{summary.text}</div>
      <div className="flex flex-wrap items-center gap-1.5 pt-1.5">
        {summary.score != null && (
          <span className="text-[21px] font-extrabold leading-none text-foreground">{summary.score}점</span>
        )}
        {summary.chips?.map((chip) => (
          <span key={chip} className="rounded-full px-2.5 py-1 text-[11px] font-semibold text-ink-2" style={{ background: "var(--secondary)" }}>
            {chip}
          </span>
        ))}
        {action && <TintButton icon={<ArrowUpRight size={12} />} label={action.label} iconTrailing onClick={() => onNavigate(action.path)} />}
      </div>
    </div>
  );
}

function TitleRow({ Icon, label, muted, children }: { Icon: LucideIcon; label: string; muted?: boolean; children: React.ReactNode }) {
  return (
    <div className="flex items-center gap-2">
      <span
        className="grid h-7 w-7 shrink-0 place-items-center rounded-[8px]"
        style={{ background: muted ? "var(--secondary)" : "var(--orch-surface)" }}
      >
        <Icon size={14} style={{ color: muted ? "var(--ink-4)" : "var(--orch-point)" }} />
      </span>
      <span className="min-w-0 flex-1 truncate text-[12.5px] font-bold" style={{ color: muted ? "var(--ink-3)" : "var(--foreground)" }}>
        {label}
      </span>
      <span className="shrink-0">{children}</span>
    </div>
  );
}

function StatusPill({ text, tone }: { text: string; tone: "neutral" | "danger" | "dangerMuted" }) {
  const style =
    tone === "danger"
      ? { background: "var(--danger-50)", color: "var(--destructive)" }
      : tone === "dangerMuted"
        ? { background: "var(--secondary)", color: "var(--ink-3)" }
        : { background: "var(--secondary)", color: "var(--ink-3)" };
  return (
    <span className="rounded-full px-2 py-0.5 text-[10.5px] font-bold" style={style}>
      {text}
    </span>
  );
}

function TintButton({
  icon, label, onClick, iconTrailing, disabled,
}: {
  icon: React.ReactNode; label: string; onClick: () => void; iconTrailing?: boolean; disabled?: boolean;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className="inline-flex h-[30px] shrink-0 items-center gap-1 whitespace-nowrap rounded-lg px-2.5 text-[11.5px] font-bold transition-colors hover:brightness-95 disabled:cursor-not-allowed disabled:opacity-60"
      style={{ background: "var(--orch-surface)", border: "1px solid rgba(94,106,210,0.3)", color: "var(--orch-violet)" }}
    >
      {!iconTrailing && icon}
      {label}
      {iconTrailing && icon}
    </button>
  );
}

/**
 * SKIPPED 된 WRITE 카드의 "자소서 첨부" — 파일을 고르면 부모가 업로드 후 첨부를 실어 재실행한다.
 * 재실행이 시작되면 이 카드는 running 으로 바뀌며 사라지므로, busy 는 업로드 구간만 덮는다.
 */
function CoverLetterAttachButton({ onAttach }: { onAttach: (file: File) => Promise<void> }) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const pick = (file: File | undefined) => {
    if (!file || busy) return;
    setError(null);
    setBusy(true);
    onAttach(file)
      .catch(() => setError("첨부에 실패했어요. 파일을 확인하고 다시 시도해 주세요."))
      .finally(() => setBusy(false));
  };

  return (
    <>
      <input
        ref={inputRef}
        type="file"
        accept={COVER_LETTER_ACCEPT}
        className="hidden"
        aria-label="자소서 파일 선택"
        // 같은 파일을 다시 고를 수 있게 값을 비운다(change 는 값이 바뀔 때만 발화).
        onChange={(e) => { pick(e.target.files?.[0]); e.target.value = ""; }}
      />
      <TintButton
        icon={busy ? <Loader2 size={12} className="animate-spin" /> : <Paperclip size={12} />}
        label={busy ? "첨부하는 중" : "자소서 첨부"}
        disabled={busy}
        onClick={() => inputRef.current?.click()}
      />
      {error && <p className="pt-1 text-[11px] leading-[1.5] text-red-600">{error}</p>}
    </>
  );
}

function OutlineButton({ icon, label, onClick }: { icon: React.ReactNode; label: string; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="inline-flex h-[30px] shrink-0 items-center gap-1 whitespace-nowrap rounded-lg px-2.5 text-[11.5px] font-bold text-ink-2 transition-colors hover:bg-secondary"
      style={{ background: "var(--card)", border: "1px solid var(--border-strong)" }}
    >
      {icon}
      {label}
    </button>
  );
}

function FooterBar({
  band, helper, caseId, onNavigate,
}: {
  band: StatusBand; helper: FooterHelper; caseId: number | null; onNavigate: (path: string) => void;
}) {
  return (
    <div className="flex items-center gap-2 border-t border-border bg-card px-3.5 py-2.5">
      <span className="min-w-0 flex-1 truncate text-[11.5px] text-ink-4">
        {helper.before}
        {helper.contactLabel && (
          <button type="button" onClick={() => onNavigate("/support/contact")} className="font-semibold underline-offset-2 hover:underline" style={{ color: "var(--orch-point)" }}>
            {helper.contactLabel}
          </button>
        )}
        {helper.after}
      </span>
      <div className="flex shrink-0 gap-2">
        {caseId != null && (
          <button
            type="button"
            onClick={() => onNavigate(`/applications/${caseId}`)}
            className="inline-flex h-[34px] shrink-0 items-center gap-1 whitespace-nowrap rounded-[10px] px-3 text-[12px] font-bold text-ink-2 transition-colors hover:bg-secondary"
            style={{ background: "var(--card)", border: "1px solid var(--border-strong)" }}
          >
            지원 건 열기
            <ArrowUpRight size={13} />
          </button>
        )}
        <button
          type="button"
          disabled={!band.interviewReady}
          onClick={() => onNavigate(caseId ? `/interview?caseId=${caseId}&tab=modes` : "/interview")}
          className={`inline-flex h-[34px] shrink-0 items-center gap-1.5 whitespace-nowrap rounded-[10px] px-3.5 text-[12px] font-bold transition-transform ${
            band.interviewReady ? "text-white hover:brightness-110" : "cursor-default text-ink-4"
          }`}
          style={
            band.interviewReady
              ? { background: "var(--gradient-orchestrator)", boxShadow: "0 8px 20px rgba(80,70,200,0.28)" }
              : { background: "var(--secondary)" }
          }
        >
          면접 시작
        </button>
      </div>
    </div>
  );
}
