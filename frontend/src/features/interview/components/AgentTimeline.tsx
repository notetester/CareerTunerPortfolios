import { useEffect, useState } from "react";
import {
  AlertTriangle,
  Brain,
  CheckCircle2,
  ChevronDown,
  ClipboardCheck,
  CornerDownRight,
  FileText,
  Loader2,
  Search,
  ShieldCheck,
  Sparkles,
} from "lucide-react";
import type { InterviewAgentStep } from "../types/interview";

/**
 * AI 면접관 자율 에이전트의 작업 과정을 타임라인으로 보여준다.
 * 단계를 한 번에 쏟지 않고 순차적으로 등장시켜(진행중→완료/실패), "에이전트가 지금 일하고 있다"는
 * 체감을 준다. 각 단계는 소요 시간과 상세(detail JSON)를 펼쳐 볼 수 있다.
 *
 * NOTE: 현재는 저장된 단계를 클라이언트에서 순차 재생한다. 서버 푸시(SSE) 실시간 스트리밍은 후속
 *       (EventSource 의 인증 헤더 제약으로 fetch-stream 도입이 필요 — 로드맵 6-1 참조).
 */
export function AgentTimeline({ steps }: { steps: InterviewAgentStep[] }) {
  // 순차 재생: visible 개수만 노출하고, 마지막으로 등장한 단계는 "진행중"으로 보이게 한다.
  const [visible, setVisible] = useState(0);

  useEffect(() => {
    setVisible(0);
    if (steps.length === 0) return;
    let i = 0;
    const timer = setInterval(() => {
      i += 1;
      setVisible(i);
      if (i >= steps.length) clearInterval(timer);
    }, 550);
    return () => clearInterval(timer);
  }, [steps]);

  if (steps.length === 0) return null;

  const shown = steps.slice(0, Math.max(visible, 1));
  const allShown = visible >= steps.length;

  return (
    <div className="rounded-lg border border-slate-200 bg-white p-3">
      <div className="mb-3 flex items-center gap-1.5 text-xs font-bold text-slate-500">
        <Sparkles className="size-3.5 text-indigo-500" /> AI 면접관 작업 과정
        {!allShown ? (
          <span className="ml-auto flex items-center gap-1 font-normal text-indigo-500">
            <Loader2 className="size-3 animate-spin" /> 진행 중…
          </span>
        ) : (
          <span className="ml-auto flex items-center gap-1 font-normal text-green-600">
            <CheckCircle2 className="size-3" /> 완료
          </span>
        )}
      </div>

      <ol className="relative space-y-2 pl-1">
        {shown.map((step, idx) => {
          const isLast = idx === shown.length - 1;
          const running = !allShown && isLast; // 막 등장한 단계는 진행 중으로 표현
          return (
            <TimelineRow
              key={step.id}
              step={step}
              running={running}
              connected={idx < shown.length - 1 || !allShown}
            />
          );
        })}
      </ol>
    </div>
  );
}

function TimelineRow({
  step,
  running,
  connected,
}: {
  step: InterviewAgentStep;
  running: boolean;
  connected: boolean;
}) {
  const [open, setOpen] = useState(false);
  const meta = agentMeta(step.agent);
  const failed = step.status === "FAILED";
  const detail = parseDetail(step.detail);

  return (
    <li className="relative flex gap-2.5">
      {/* 노드 + 세로 연결선 */}
      <div className="relative flex flex-col items-center">
        <span
          className={`flex size-6 shrink-0 items-center justify-center rounded-full border ${
            failed
              ? "border-red-200 bg-red-50 text-red-500"
              : running
                ? "border-indigo-200 bg-indigo-50 text-indigo-500"
                : `${meta.tone} border-transparent`
          }`}
        >
          {running ? (
            <Loader2 className="size-3.5 animate-spin" />
          ) : failed ? (
            <AlertTriangle className="size-3.5" />
          ) : (
            <meta.Icon className="size-3.5" />
          )}
        </span>
        {connected && <span className="mt-0.5 w-px flex-1 bg-slate-200" />}
      </div>

      {/* 본문 */}
      <div className="flex-1 pb-1">
        <div className="flex items-center gap-1.5">
          <span className="text-xs font-bold text-slate-700">{meta.label}</span>
          {step.elapsedMs != null && !running && (
            <span className="text-[10px] text-slate-400">{formatElapsed(step.elapsedMs)}</span>
          )}
          {detail && (
            <button
              type="button"
              onClick={() => setOpen((v) => !v)}
              className="ml-auto flex items-center gap-0.5 text-[10px] text-slate-400 hover:text-slate-600"
            >
              상세 <ChevronDown className={`size-3 transition-transform ${open ? "rotate-180" : ""}`} />
            </button>
          )}
        </div>
        <p className={`text-xs ${failed ? "text-red-500" : "text-slate-600"}`}>{step.summary}</p>

        {open && detail && (
          <dl className="mt-1.5 space-y-0.5 rounded-md bg-slate-50 p-2 text-[11px]">
            {detail.map(([k, v]) => (
              <div key={k} className="flex gap-1.5">
                <dt className="shrink-0 font-semibold text-slate-500">{detailLabel(k)}</dt>
                <dd className="text-slate-600">{v}</dd>
              </div>
            ))}
          </dl>
        )}
      </div>
    </li>
  );
}

// ───── 에이전트별 표시 메타 ─────

function agentMeta(agent: string): { label: string; tone: string; Icon: typeof Brain } {
  switch (agent) {
    case "RETRIEVER":
      return { label: "근거 검색", tone: "bg-cyan-100 text-cyan-700", Icon: Search };
    case "EVALUATOR":
      return { label: "채점", tone: "bg-blue-100 text-blue-700", Icon: ClipboardCheck };
    case "CRITIC":
      return { label: "검증", tone: "bg-amber-100 text-amber-700", Icon: ShieldCheck };
    case "PROBER":
      return { label: "추가 탐색", tone: "bg-violet-100 text-violet-700", Icon: CornerDownRight };
    case "PLANNER":
      return { label: "계획", tone: "bg-indigo-100 text-indigo-700", Icon: Brain };
    case "REPORTER":
      return { label: "리포트", tone: "bg-emerald-100 text-emerald-700", Icon: FileText };
    default:
      return { label: agent, tone: "bg-slate-100 text-slate-600", Icon: Sparkles };
  }
}

function formatElapsed(ms: number): string {
  if (ms < 1000) return `${ms}ms`;
  return `${(ms / 1000).toFixed(1)}초`;
}

const DETAIL_LABELS: Record<string, string> = {
  score: "점수",
  feedback: "피드백",
  verdict: "판정",
  reason: "근거",
  adjustedScore: "조정 점수",
  reScore: "재채점",
  reconciled: "최종 조정",
  answerLength: "답변 길이",
  action: "다음 액션",
};

function detailLabel(key: string): string {
  return DETAIL_LABELS[key] ?? key;
}

/** detail(JSON 문자열)을 [키, 값] 쌍 배열로 파싱한다. 실패하면 null. */
function parseDetail(detail: string | null): [string, string][] | null {
  if (!detail) return null;
  try {
    const obj = JSON.parse(detail) as Record<string, unknown>;
    const entries = Object.entries(obj)
      .filter(([, v]) => v !== null && v !== "" && v !== undefined)
      .map(([k, v]) => [k, String(v)] as [string, string]);
    return entries.length > 0 ? entries : null;
  } catch {
    return null;
  }
}
