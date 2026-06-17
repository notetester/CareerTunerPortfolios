import { useState } from "react";
import { AlertCircle, BarChart3, FileText } from "lucide-react";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import type { ApplicationCase } from "@/features/applications/types/applicationCase";
import { createInterviewSession } from "../api/interviewApi";
import { useInterviewSessions } from "../hooks/useInterviewSessions";
import {
  INTERVIEW_MODES,
  getInterviewModeLabel,
  getScoreColor,
  type InterviewMode,
  type InterviewSession,
} from "../types/interview";

interface ModeSelectTabProps {
  cases: ApplicationCase[];
  casesLoading: boolean;
  casesError: string | null;
  selectedCaseId: number | null;
  selectedMode: InterviewMode | null;
  onSelectCase(id: number): void;
  onSelectMode(mode: InterviewMode): void;
  onSessionStarted(session: InterviewSession): void;
}

function formatDate(value: string): string {
  return new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium" }).format(new Date(value));
}

export function ModeSelectTab({
  cases,
  casesLoading,
  casesError,
  selectedCaseId,
  selectedMode,
  onSelectCase,
  onSelectMode,
  onSessionStarted,
}: ModeSelectTabProps) {
  const sessions = useInterviewSessions();
  const [starting, setStarting] = useState(false);
  const [startError, setStartError] = useState<string | null>(null);

  const canStart = selectedCaseId !== null && selectedMode !== null && !starting;

  const handleStart = async () => {
    if (selectedCaseId === null || selectedMode === null) return;
    setStarting(true);
    setStartError(null);
    try {
      const session = await createInterviewSession({
        applicationCaseId: selectedCaseId,
        mode: selectedMode,
      });
      onSessionStarted(session);
    } catch (err) {
      setStartError(err instanceof Error ? err.message : "면접을 시작하지 못했습니다.");
    } finally {
      setStarting(false);
    }
  };

  const caseLabel = (caseId: number) => {
    const c = cases.find((item) => item.id === caseId);
    return c ? `${c.companyName} · ${c.jobTitle}` : `지원 건 #${caseId}`;
  };

  return (
    <div className="space-y-6">
      {/* 지원 건 선택 */}
      <div className="rounded-2xl border border-slate-200 bg-white p-5">
        <div className="mb-3 text-sm font-bold text-slate-700">지원 건 선택</div>
        {casesLoading ? (
          <p className="text-sm text-slate-400">지원 건을 불러오는 중…</p>
        ) : casesError ? (
          <p className="text-sm text-red-500">{casesError}</p>
        ) : cases.length === 0 ? (
          <p className="text-sm text-slate-400">먼저 지원 건을 등록하면 해당 공고 기반으로 면접을 볼 수 있습니다.</p>
        ) : (
          <div className="flex flex-wrap gap-2">
            {cases.map((c) => (
              <button
                key={c.id}
                onClick={() => onSelectCase(c.id)}
                className={`rounded-full border px-3 py-1.5 text-xs font-semibold transition-colors ${
                  selectedCaseId === c.id
                    ? "border-blue-600 bg-blue-600 text-white"
                    : "border-slate-300 text-slate-600 hover:border-blue-400"
                }`}
              >
                {c.companyName} · {c.jobTitle}
              </button>
            ))}
          </div>
        )}
      </div>

      {/* 모드 그리드 */}
      <div data-tut="tut-modes-grid" className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {INTERVIEW_MODES.map((mode) => (
          <button
            key={mode.id}
            onClick={() => onSelectMode(mode.id)}
            className={`group relative rounded-2xl border-2 p-5 text-left transition-all hover:shadow-lg ${
              selectedMode === mode.id
                ? "border-blue-500 bg-blue-50 shadow-lg"
                : "border-slate-200 bg-white hover:border-blue-300"
            }`}
          >
            {mode.recommended && (
              <div className="absolute -right-2 -top-2">
                <Badge className="bg-gradient-to-r from-blue-600 to-indigo-600 px-2 py-0.5 text-[9px] text-white">
                  추천
                </Badge>
              </div>
            )}
            <div className="mb-3 text-3xl transition-transform group-hover:scale-110">{mode.icon}</div>
            <div className="mb-1 text-sm font-bold text-slate-800">{mode.title}</div>
            <div className="mb-2 text-xs leading-relaxed text-slate-500">{mode.desc}</div>
            <Badge
              className={`px-2 py-0.5 text-[10px] ${
                mode.difficulty === "상"
                  ? "bg-red-100 text-red-700"
                  : mode.difficulty === "중"
                    ? "bg-amber-100 text-amber-700"
                    : "bg-green-100 text-green-700"
              }`}
            >
              난이도 {mode.difficulty}
            </Badge>
          </button>
        ))}
      </div>

      {/* 시작 버튼 */}
      <div className="space-y-2">
        <Button
          size="lg"
          className="h-14 w-full gap-2 bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-700 hover:to-indigo-700"
          disabled={!canStart}
          onClick={handleStart}
        >
          <FileText className="size-5" />
          {starting ? "시작하는 중…" : "면접 시작하기"}
        </Button>
        {(selectedCaseId === null || selectedMode === null) && !starting && (
          <p className="flex items-center gap-1.5 text-sm text-amber-600">
            <AlertCircle className="size-4" />
            {selectedCaseId === null && selectedMode === null
              ? "지원 건과 면접 모드를 선택하면 시작할 수 있습니다."
              : selectedCaseId === null
                ? "지원 건을 선택하세요."
                : "면접 모드를 선택하세요."}
          </p>
        )}
      </div>
      {startError && (
        <p className="flex items-center gap-1.5 text-sm text-red-500">
          <AlertCircle className="size-4" /> {startError}
        </p>
      )}

      {/* 최근 면접 기록 */}
      <div>
        <h3 className="mb-3 font-bold text-slate-800">최근 면접 기록</h3>
        {sessions.loading ? (
          <p className="text-sm text-slate-400">불러오는 중…</p>
        ) : sessions.error ? (
          <p className="text-sm text-red-500">{sessions.error}</p>
        ) : sessions.sessions.length === 0 ? (
          <p className="rounded-xl border border-dashed border-slate-200 bg-white p-6 text-center text-sm text-slate-400">
            아직 면접 기록이 없습니다. 위에서 모드를 골라 첫 면접을 시작해 보세요.
          </p>
        ) : (
          <div className="space-y-2">
            {sessions.sessions.map((s) => (
              <div
                key={s.id}
                className="flex items-center gap-4 rounded-xl border border-slate-200 bg-white p-4 transition-colors hover:border-blue-300"
              >
                <div className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-purple-500 to-indigo-500 text-xs font-bold text-white">
                  {caseLabel(s.applicationCaseId).slice(0, 1)}
                </div>
                <div className="min-w-0 flex-1">
                  <div className="text-sm font-semibold text-slate-800">
                    {caseLabel(s.applicationCaseId)} · {getInterviewModeLabel(s.mode)}
                  </div>
                  <div className="mt-0.5 text-xs text-slate-500">{formatDate(s.createdAt)}</div>
                </div>
                <div className="text-center">
                  <div className={`text-lg font-black ${s.totalScore !== null ? getScoreColor(s.totalScore) : "text-slate-300"}`}>
                    {s.totalScore ?? "-"}
                  </div>
                  <div className="text-[10px] text-slate-400">점수</div>
                </div>
                <BarChart3 className="size-4 text-slate-400" />
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
