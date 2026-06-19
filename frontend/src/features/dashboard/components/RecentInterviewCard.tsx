import { Link } from "react-router";
import { ArrowDown, ArrowUp, MessageSquare, Minus } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import type { DashboardRecentInterview } from "../types/dashboardSummary";

const interviewModeLabel: Record<string, string> = {
  BASIC: "기본 면접",
  JOB: "직무/기술 면접",
  PERSONALITY: "인성 면접",
  PRESSURE: "압박 면접",
  REAL: "실전 종합 면접",
  RESUME: "자소서 기반 면접",
  PORTFOLIO: "포트폴리오 기반 면접",
  COMPANY: "기업 맞춤 면접",
};

interface RecentInterviewCardProps {
  interview: DashboardRecentInterview | null;
}

/**
 * 최근 면접 카드(디자인 분석 §6.4: 점수 변화, 핵심 개선점, 리포트 보기).
 * 면접 원본(D 소유)은 읽기 전용 요약만 보여주고, 상세는 면접 리포트로 연결한다.
 */
export function RecentInterviewCard({ interview }: RecentInterviewCardProps) {
  return (
    <Card className="border border-slate-200 bg-card">
      <CardHeader className="pb-3">
        <CardTitle className="text-base flex items-center gap-2">
          <MessageSquare className="size-4 text-purple-600" />
          최근 면접
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        {interview ? (
          <>
            <div className="flex items-start justify-between gap-3">
              <div className="min-w-0">
                <div className="truncate text-sm font-bold text-slate-900">
                  {interview.companyName} · {interview.jobTitle}
                </div>
                <div className="mt-0.5 text-xs text-slate-400">
                  {interviewModeLabel[interview.mode] ?? interview.mode} ·{" "}
                  {new Intl.DateTimeFormat("ko-KR", { month: "2-digit", day: "2-digit" }).format(new Date(interview.occurredAt))}
                </div>
              </div>
              <div className="shrink-0 text-right">
                <div className="text-2xl font-black text-purple-700">{interview.totalScore ?? "-"}점</div>
                {interview.scoreDelta != null ? (
                  <div
                    className={`flex items-center justify-end gap-0.5 text-xs font-semibold ${
                      interview.scoreDelta > 0 ? "text-green-600" : interview.scoreDelta < 0 ? "text-red-500" : "text-slate-400"
                    }`}
                  >
                    {interview.scoreDelta > 0 ? (
                      <ArrowUp className="size-3" />
                    ) : interview.scoreDelta < 0 ? (
                      <ArrowDown className="size-3" />
                    ) : (
                      <Minus className="size-3" />
                    )}
                    직전 대비 {interview.scoreDelta > 0 ? "+" : ""}
                    {interview.scoreDelta}점
                  </div>
                ) : (
                  <div className="text-xs text-slate-400">첫 면접 기록</div>
                )}
              </div>
            </div>

            {interview.keyImprovement && (
              <div className="rounded-lg bg-purple-50 px-3 py-2">
                <div className="text-xs font-semibold text-purple-700">핵심 개선점</div>
                <p className="mt-0.5 line-clamp-2 text-xs leading-5 text-purple-900">{interview.keyImprovement}</p>
              </div>
            )}

            <div className="flex items-center gap-3 text-sm font-semibold">
              <Link to="/interview?tab=report" className="text-purple-600 hover:text-purple-700">
                리포트 보기
              </Link>
              <Link to={`/applications/${interview.applicationCaseId}`} className="text-slate-500 hover:text-slate-700">
                지원 건 보기
              </Link>
            </div>
          </>
        ) : (
          <div className="space-y-2">
            <p className="text-sm text-slate-500">
              아직 점수가 기록된 모의면접이 없습니다. 면접을 완료하면 점수 변화와 핵심 개선점이 이곳에 표시됩니다.
            </p>
            <Link to="/interview" className="inline-flex text-sm font-semibold text-purple-600 hover:text-purple-700">
              모의면접 시작하기
            </Link>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
