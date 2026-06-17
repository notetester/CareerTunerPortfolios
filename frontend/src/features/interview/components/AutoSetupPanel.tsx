import { useMemo, useState } from "react";
import { Link } from "react-router";
import {
  AlertTriangle,
  CheckCircle2,
  Loader2,
  MessageSquare,
  Search,
  Sparkles,
  Target,
  Wand2,
} from "lucide-react";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import type { ApplicationCase } from "@/features/applications/types/applicationCase";
import { createInterviewSession, generateExpectedQuestions } from "../api/interviewApi";
import { getInterviewModeLabel, type InterviewMode, type InterviewSession } from "../types/interview";

/**
 * 마누스형 자동 셋업 패널.
 * 사용자가 홈에서 "○○ 면접 준비해줘" 한 줄을 던지면, 에이전트가 요청 분석 → 모드 선정 →
 * 세션 생성 → 질문 생성을 단계별로 자동 수행하고, 그 과정을 타임라인으로 보여준다.
 * (기존 면접 API 를 그대로 엮어 진입 UX 만 자율 에이전트처럼 묶었다.)
 */
export function AutoSetupPanel({
  cases,
  casesLoading,
  prompt,
  onReady,
  onManual,
}: {
  cases: ApplicationCase[];
  casesLoading: boolean;
  prompt: string;
  onReady: (session: InterviewSession) => void;
  onManual: () => void;
}) {
  const recommendedMode = useMemo(() => recommendMode(prompt), [prompt]);
  const matchedCaseId = useMemo(() => matchCase(prompt, cases), [prompt, cases]);
  const [caseId, setCaseId] = useState<number | null>(matchedCaseId);
  const [steps, setSteps] = useState<Step[]>(() => initialSteps());
  const [running, setRunning] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // 매칭 결과가 늦게 도착하면(목록 로딩) 선택을 한 번 맞춰준다.
  const effectiveCaseId = caseId ?? matchedCaseId;

  const update = (idx: number, state: StepState, detail?: string) =>
    setSteps((prev) => prev.map((s, i) => (i === idx ? { ...s, state, detail: detail ?? s.detail } : s)));

  const run = async () => {
    if (effectiveCaseId == null) return;
    setRunning(true);
    setError(null);
    setSteps(initialSteps());
    const modeLabel = getInterviewModeLabel(recommendedMode);
    try {
      // ① 요청 분석
      update(0, "running");
      await delay(500);
      update(0, "done", `"${truncate(prompt, 40)}" → ${modeLabel} 의도 파악`);

      // ② 면접 모드 선정
      update(1, "running");
      await delay(400);
      update(1, "done", `${modeLabel} 선정`);

      // ③ 세션 생성
      update(2, "running");
      const session = await createInterviewSession({ applicationCaseId: effectiveCaseId, mode: recommendedMode });
      update(2, "done", `세션 #${session.id} 생성`);

      // ④ 예상 질문 생성
      update(3, "running");
      await generateExpectedQuestions(session.id, { mode: recommendedMode });
      update(3, "done", "예상 질문 생성 완료");

      await delay(400);
      onReady(session);
    } catch (err) {
      setSteps((prev) => prev.map((s) => (s.state === "running" ? { ...s, state: "failed" } : s)));
      setError(err instanceof Error ? err.message : "자동 셋업에 실패했습니다.");
      setRunning(false);
    }
  };

  // 지원 건이 없으면 자동 진행 불가 — 등록 유도.
  if (!casesLoading && cases.length === 0) {
    return (
      <Panel prompt={prompt}>
        <div className="rounded-xl border border-dashed border-slate-200 bg-white p-8 text-center">
          <p className="text-sm text-slate-500">
            면접을 보려면 먼저 지원 건이 필요합니다. 지원할 공고를 등록하면 바로 자동 셋업할 수 있습니다.
          </p>
          <Link to="/applications/new">
            <Button className="mt-4 gap-1.5">지원 건 등록하러 가기</Button>
          </Link>
          <button onClick={onManual} className="mt-3 block w-full text-xs text-slate-400 hover:text-slate-600">
            직접 설정으로 전환
          </button>
        </div>
      </Panel>
    );
  }

  const selectedCase = cases.find((c) => c.id === effectiveCaseId) ?? null;

  return (
    <Panel prompt={prompt}>
      <div className="space-y-4 rounded-xl border border-indigo-100 bg-gradient-to-br from-indigo-50/60 to-white p-5">
        {/* 대상 지원 건 */}
        <div className="space-y-2">
          <div className="flex items-center gap-1.5 text-xs font-bold text-slate-500">
            <Target className="size-3.5 text-indigo-500" /> 면접 대상
          </div>
          <div className="flex flex-wrap gap-2">
            {cases.slice(0, 6).map((c) => (
              <button
                key={c.id}
                type="button"
                disabled={running}
                onClick={() => setCaseId(c.id)}
                className={`rounded-lg border px-3 py-1.5 text-left text-xs transition-colors ${
                  effectiveCaseId === c.id
                    ? "border-indigo-300 bg-indigo-50 ring-1 ring-indigo-200"
                    : "border-slate-200 bg-white hover:border-indigo-200"
                }`}
              >
                <div className="font-bold text-slate-800">{c.companyName}</div>
                <div className="text-slate-500">{c.jobTitle}</div>
              </button>
            ))}
          </div>
          {selectedCase && (
            <p className="text-xs text-slate-500">
              추천 모드 <Badge className="bg-indigo-100 text-indigo-700">{getInterviewModeLabel(recommendedMode)}</Badge>
            </p>
          )}
        </div>

        {/* 액션 */}
        {!running && steps.every((s) => s.state === "pending") ? (
          <div className="flex flex-wrap items-center gap-2">
            <Button className="gap-1.5 bg-indigo-600 text-white hover:bg-indigo-700" disabled={effectiveCaseId == null} onClick={run}>
              <Wand2 className="size-4" /> AI에게 맡기기
            </Button>
            <button onClick={onManual} className="text-xs text-slate-400 hover:text-slate-600">
              직접 설정할래요
            </button>
          </div>
        ) : (
          <AutoTimeline steps={steps} />
        )}

        {error && (
          <div className="flex items-center justify-between rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-xs text-red-600">
            <span className="flex items-center gap-1.5">
              <AlertTriangle className="size-3.5" /> {error}
            </span>
            <button onClick={run} className="font-semibold underline">
              다시 시도
            </button>
          </div>
        )}
      </div>
    </Panel>
  );
}

function Panel({ prompt, children }: { prompt: string; children: React.ReactNode }) {
  return (
    <div className="space-y-4">
      <div className="flex items-start gap-2 rounded-xl border border-slate-200 bg-white p-4">
        <Sparkles className="mt-0.5 size-5 shrink-0 text-indigo-500" />
        <div>
          <div className="text-xs font-bold text-slate-400">당신의 요청</div>
          <p className="text-sm font-semibold text-slate-800">{prompt}</p>
        </div>
      </div>
      {children}
    </div>
  );
}

function AutoTimeline({ steps }: { steps: Step[] }) {
  return (
    <ol className="space-y-2.5">
      {steps.map((step, idx) => {
        const Icon = step.icon;
        return (
          <li key={step.key} className="flex items-start gap-2.5">
            <span
              className={`flex size-7 shrink-0 items-center justify-center rounded-full border ${
                step.state === "failed"
                  ? "border-red-200 bg-red-50 text-red-500"
                  : step.state === "done"
                    ? "border-green-200 bg-green-50 text-green-600"
                    : step.state === "running"
                      ? "border-indigo-200 bg-indigo-50 text-indigo-500"
                      : "border-slate-200 bg-white text-slate-300"
              }`}
            >
              {step.state === "running" ? (
                <Loader2 className="size-4 animate-spin" />
              ) : step.state === "done" ? (
                <CheckCircle2 className="size-4" />
              ) : step.state === "failed" ? (
                <AlertTriangle className="size-4" />
              ) : (
                <Icon className="size-4" />
              )}
            </span>
            <div className="flex-1 pt-1">
              <div className="text-sm font-semibold text-slate-700">{step.label}</div>
              {step.detail && <div className="text-xs text-slate-500">{step.detail}</div>}
            </div>
            <span className="pt-1 text-[10px] font-bold uppercase text-slate-400">
              {idx + 1}/{steps.length}
            </span>
          </li>
        );
      })}
    </ol>
  );
}

// ───── 로직 ─────

type StepState = "pending" | "running" | "done" | "failed";

interface Step {
  key: string;
  label: string;
  icon: typeof Search;
  state: StepState;
  detail?: string;
}

function initialSteps(): Step[] {
  return [
    { key: "analyze", label: "요청 분석", icon: Search, state: "pending" },
    { key: "mode", label: "면접 모드 선정", icon: Target, state: "pending" },
    { key: "session", label: "면접 세션 생성", icon: Sparkles, state: "pending" },
    { key: "questions", label: "예상 질문 생성", icon: MessageSquare, state: "pending" },
  ];
}

/** 요청 문장에서 면접 모드를 추천한다(간단 키워드 규칙). */
function recommendMode(prompt: string): InterviewMode {
  const p = prompt ?? "";
  if (/압박|꼬리|반박|당황|몰아/.test(p)) return "PRESSURE";
  if (/인성|성격|가치관|협업|갈등|태도/.test(p)) return "PERSONALITY";
  if (/자소서|자기소개서/.test(p)) return "RESUME";
  if (/기업\s*맞춤|컬처|컬쳐|회사\s*맞춤/.test(p)) return "COMPANY";
  if (/기술|직무|개발|백엔드|프론트|코딩|엔지니어|데이터|서버/.test(p)) return "JOB";
  return "JOB";
}

/** 요청 문장에 회사/직무명이 있으면 해당 지원 건을, 없으면 첫 지원 건을 고른다. */
function matchCase(prompt: string, cases: ApplicationCase[]): number | null {
  if (cases.length === 0) return null;
  const found = cases.find(
    (c) => (c.companyName && prompt.includes(c.companyName)) || (c.jobTitle && prompt.includes(c.jobTitle)),
  );
  return (found ?? cases[0]).id;
}

function truncate(value: string, max: number): string {
  return value.length <= max ? value : value.slice(0, max) + "…";
}

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
