import { ArrowRight, FileText, Loader2, PlayCircle, RotateCcw } from "lucide-react";
import { Link } from "react-router";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import type { ApplicationCase } from "@/features/applications/types/applicationCase";
import { useInterviewSessions } from "../hooks/useInterviewSessions";
import {
  INTERVIEW_SECTION_META,
  type InterviewSection,
} from "../lib/interviewSections";
import { getInterviewModeLabel, getScoreColor, type InterviewSession } from "../types/interview";

interface SessionEntryGateProps {
  section: Exclude<InterviewSection, "modes">;
  applicationCases: ApplicationCase[];
  startHref: string;
  onSelect: (session: InterviewSession) => void;
}

function formatDate(value: string | null | undefined) {
  if (!value) return "날짜 없음";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("ko-KR", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
}

export function SessionEntryGate({
  section,
  applicationCases,
  startHref,
  onSelect,
}: SessionEntryGateProps) {
  const sessions = useInterviewSessions();
  const meta = INTERVIEW_SECTION_META[section];
  const recentSessions = sessions.sessions.slice(0, 6);
  const caseById = new Map(applicationCases.map((item) => [item.id, item]));

  return (
    <Card className="border border-indigo-200 bg-indigo-50/40">
      <CardHeader className="space-y-2">
        <Badge className="w-fit bg-indigo-100 text-indigo-700">면접 세션 선택</Badge>
        <CardTitle className="text-lg text-slate-900">{meta.label}에 사용할 세션을 선택하세요</CardTitle>
        <p className="text-sm leading-6 text-slate-600">
          이 기능은 지원 건과 면접 모드가 연결된 세션이 필요합니다. 최근 세션을 이어보거나 새 면접을 시작할 수 있습니다.
        </p>
      </CardHeader>
      <CardContent className="space-y-4">
        {sessions.loading ? (
          <div className="flex min-h-28 items-center justify-center gap-2 rounded-lg border border-indigo-100 bg-white text-sm text-slate-500">
            <Loader2 className="size-4 animate-spin" /> 최근 면접 세션을 불러오는 중…
          </div>
        ) : sessions.error ? (
          <div className="space-y-3 rounded-lg border border-red-200 bg-white p-4">
            <p className="text-sm text-red-600">{sessions.error}</p>
            <Button type="button" size="sm" variant="outline" onClick={() => void sessions.refresh()}>
              <RotateCcw className="size-4" /> 다시 불러오기
            </Button>
          </div>
        ) : recentSessions.length === 0 ? (
          <div className="rounded-lg border border-dashed border-indigo-200 bg-white p-6 text-center text-sm text-slate-500">
            아직 이어볼 면접 세션이 없습니다. 지원 건과 면접 모드를 먼저 선택해 주세요.
          </div>
        ) : (
          <div className="space-y-2">
            <div className="text-xs font-bold uppercase tracking-wide text-slate-500">최근 면접</div>
            {recentSessions.map((session) => {
              const applicationCase = caseById.get(session.applicationCaseId);
              const score = session.totalScore ?? session.avgScore;
              return (
                <button
                  key={session.id}
                  type="button"
                  onClick={() => onSelect(session)}
                  className="flex w-full flex-col gap-2 rounded-lg border border-slate-200 bg-white p-3 text-left transition-colors hover:border-indigo-300 hover:bg-indigo-50 sm:flex-row sm:items-center sm:justify-between"
                >
                  <div className="min-w-0">
                    <div className="truncate text-sm font-bold text-slate-900">
                      {applicationCase
                        ? `${applicationCase.companyName} · ${applicationCase.jobTitle}`
                        : `지원 건 #${session.applicationCaseId}`}
                    </div>
                    <div className="mt-1 flex flex-wrap items-center gap-2 text-xs text-slate-500">
                      <span>{getInterviewModeLabel(session.mode)}</span>
                      <span>·</span>
                      <span>{formatDate(session.startedAt ?? session.createdAt)}</span>
                      <span>·</span>
                      <span>{session.answeredQuestions}/{session.totalQuestions} 답변</span>
                    </div>
                  </div>
                  <div className="flex shrink-0 items-center gap-2">
                    {score != null && (
                      <span className={`text-sm font-black ${getScoreColor(score)}`}>{score}점</span>
                    )}
                    <span className="inline-flex items-center gap-1 text-xs font-bold text-indigo-600">
                      {section === "report" ? <FileText className="size-3.5" /> : <PlayCircle className="size-3.5" />}
                      선택
                    </span>
                  </div>
                </button>
              );
            })}
          </div>
        )}

        <div className="flex flex-wrap items-center justify-between gap-3 border-t border-indigo-100 pt-4">
          <p className="text-xs text-slate-500">원하는 세션이 없다면 새 면접을 만들어 시작하세요.</p>
          <Button asChild>
            <Link to={startHref}>
              새 면접 시작 <ArrowRight className="size-4" />
            </Link>
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}
