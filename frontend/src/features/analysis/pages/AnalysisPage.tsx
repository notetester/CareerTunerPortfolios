import { useEffect, useMemo, useState } from "react";
import { useNavigate, useSearchParams } from "react-router";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Progress } from "@/app/components/ui/progress";
import { Button } from "@/app/components/ui/button";
import {
  TrendingUp, Target, BarChart3, ArrowUp, ArrowDown, AlertCircle,
  Brain, BookOpen, Briefcase, Loader2, RefreshCw, CheckCircle2,
  MessageSquare, PieChart,
} from "lucide-react";
import { getAnalysisSummary, refreshAnalysisSummary } from "@/features/analysis/api/analysisSummaryApi";
import type { AnalysisSummary } from "@/features/analysis/types/analysisSummary";
import { AiResultBadge } from "@/features/analysis/components/AiResultBadge";

const questionTypeLabel: Record<string, string> = {
  EXPECTED: "예상 질문",
  TECH: "기술 질문",
  PERSONALITY: "인성 질문",
  SITUATION: "상황 질문",
  FOLLOW_UP: "꼬리 질문",
};

const analysisTabs = [
  { key: "trend", label: "내 지원 경향", icon: Briefcase },
  { key: "weakness", label: "자주 부족한 역량", icon: AlertCircle },
  { key: "readiness", label: "직무별 준비도", icon: Target },
  { key: "score", label: "적합도 점수 변화", icon: BarChart3 },
  { key: "recommendation", label: "추천 지원 방향", icon: BookOpen },
] as const;
type AnalysisTab = (typeof analysisTabs)[number]["key"];

const roadmapPhases = [
  { phase: "우선 보완", color: "border-red-200 bg-red-50", textColor: "text-red-800" },
  { phase: "지원 전략", color: "border-amber-200 bg-amber-50", textColor: "text-amber-800" },
  { phase: "다음 액션", color: "border-green-200 bg-green-50", textColor: "text-green-800" },
] as const;

function formatAnalyzedAt(value: string | null) {
  if (!value) return "분석 없음";

  return new Intl.DateTimeFormat("ko-KR", {
    month: "short",
    day: "numeric",
  }).format(new Date(value));
}

export function AnalysisPage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const [summary, setSummary] = useState<AnalysisSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [refreshing, setRefreshing] = useState(false);
  const [refreshError, setRefreshError] = useState<string | null>(null);
  const requestedTab = searchParams.get("tab") ?? "trend";
  const activeTab: AnalysisTab = analysisTabs.some((tab) => tab.key === requestedTab) ? (requestedTab as AnalysisTab) : "trend";
  const stats = summary?.stats;
  const skillGapData = summary?.skillGaps ?? [];
  const jobReadiness = summary?.jobReadiness ?? [];
  const scoreHistory = summary?.scoreHistory ?? [];
  const applicationStats = useMemo(() => {
    return (summary?.applications ?? []).map((application) => ({
      id: application.applicationCaseId,
      company: application.companyName,
      job: application.jobTitle,
      status: application.status,
      favorite: application.favorite,
      score: application.fitScore ?? 0,
      analyzedAt: application.analyzedAt,
      trend: (application.fitScore ?? 0) >= 70 ? "up" : (application.fitScore ?? 0) >= 50 ? "neutral" : "down",
    }));
  }, [summary]);
  const recommendedDirections = summary?.recommendedDirections ?? [];
  const recommendationBuckets = useMemo(() => {
    const bucketSize = Math.max(1, Math.ceil(Math.max(recommendedDirections.length, roadmapPhases.length) / roadmapPhases.length));

    return roadmapPhases.map((phase, index) => ({
      ...phase,
      items: recommendedDirections.slice(index * bucketSize, (index + 1) * bucketSize),
    }));
  }, [recommendedDirections]);
  const firstScore = scoreHistory[0]?.score ?? 0;
  const lastScore = scoreHistory[scoreHistory.length - 1]?.score ?? 0;
  const scoreDelta = lastScore - firstScore;
  const topSkillGap = skillGapData[0]?.skill ?? "반복 부족 역량";
  const strongestJob = jobReadiness[0]?.jobTitle ?? "준비도가 높은 직무";
  const strengthTrends = summary?.strengthTrends ?? [];
  const jobDistribution = summary?.jobDistribution ?? [];
  const answerThemes = summary?.answerThemes ?? [];
  const period = summary?.period ?? null;
  const topStrength = strengthTrends[0]?.skill ?? null;
  const prioritizedGaps = skillGapData.slice(0, 3);

  useEffect(() => {
    let ignore = false;
    setLoading(true);
    setError(null);

    getAnalysisSummary()
      .then((data) => {
        if (!ignore) setSummary(data);
      })
      .catch((requestError) => {
        if (!ignore) setError(requestError instanceof Error ? requestError.message : "취업 분석 결과를 불러오지 못했습니다.");
      })
      .finally(() => {
        if (!ignore) setLoading(false);
      });

    return () => {
      ignore = true;
    };
  }, []);

  const handleRefresh = async () => {
    setRefreshing(true);
    setRefreshError(null);
    try {
      const data = await refreshAnalysisSummary();
      setSummary(data);
    } catch (requestError) {
      setRefreshError(requestError instanceof Error ? requestError.message : "재분석에 실패했습니다.");
    } finally {
      setRefreshing(false);
    }
  };

  return (
    <div className="bg-slate-50 min-h-screen">
      <div className="max-w-[1400px] mx-auto px-4 sm:px-6 py-8 space-y-6 lg:space-y-8">
        {/* Header */}
        <div>
          <h1 className="text-2xl font-black text-slate-900 flex items-center gap-2">
            <TrendingUp className="size-6 text-green-600" />
            취업 분석
          </h1>
          <p className="text-slate-500 text-sm mt-1">여러 지원 건을 종합한 AI 장기 취업 경향 분석 및 맞춤 전략</p>
          {/* 분석 대상 기간과 데이터 수(디자인 분석 §6.10) */}
          {period && period.analyzedCount > 0 && (
            <p className="mt-1.5 text-xs text-slate-400">
              분석 대상 {formatAnalyzedAt(period.from)} ~ {formatAnalyzedAt(period.to)} · 지원 {period.applicationCount}건 ·
              적합도 분석 {period.analyzedCount}건 · 모의면접 {period.interviewSessionCount}회
            </p>
          )}
        </div>

        {/* 모바일 우선 요약 카드(모바일 고려 §6.7): 준비도/가장 강한 역량/우선 보완 역량 중심 */}
        {!loading && !error && stats && (
          <Card className="border border-slate-200 bg-white lg:hidden">
            <CardContent className="space-y-3 p-4">
              <div className="flex items-center justify-between">
                <div>
                  <div className="text-xs font-semibold text-slate-500">현재 준비도(평균 적합도)</div>
                  <div className="text-3xl font-black text-slate-900">{stats.averageFitScore}점</div>
                </div>
                <div className="text-right text-xs text-slate-500">
                  <div>분석 {stats.analyzedApplications}건 기준</div>
                  {scoreHistory.length >= 2 && (
                    <div className={`mt-1 inline-flex items-center gap-0.5 font-semibold ${scoreDelta >= 0 ? "text-green-600" : "text-red-500"}`}>
                      {scoreDelta >= 0 ? <ArrowUp className="size-3" /> : <ArrowDown className="size-3" />}
                      {scoreDelta >= 0 ? "+" : ""}{scoreDelta}점 변화
                    </div>
                  )}
                </div>
              </div>
              {topStrength && (
                <div className="flex items-center gap-2 rounded-lg bg-green-50 px-3 py-2 text-sm text-green-800">
                  <CheckCircle2 className="size-4 shrink-0 text-green-600" />
                  가장 강한 역량: <strong>{topStrength}</strong>
                </div>
              )}
              {prioritizedGaps.length > 0 && (
                <div className="rounded-lg bg-red-50 px-3 py-2">
                  <div className="text-xs font-semibold text-red-700">우선 보완 역량</div>
                  <div className="mt-1 flex flex-wrap gap-1.5">
                    {prioritizedGaps.map((gap) => (
                      <span key={gap.skill} className="rounded-full bg-white px-2 py-0.5 text-xs font-medium text-red-600">
                        {gap.skill} ({gap.count}건)
                      </span>
                    ))}
                  </div>
                </div>
              )}
            </CardContent>
          </Card>
        )}

        <div className="flex overflow-x-auto rounded-xl border border-slate-200 bg-white p-1">
          {analysisTabs.map((tab) => (
            <button
              key={tab.key}
              onClick={() => setSearchParams(tab.key === "trend" ? {} : { tab: tab.key })}
              className={`flex shrink-0 items-center gap-1.5 rounded-lg px-3 py-2 text-xs font-semibold transition-colors ${
                activeTab === tab.key ? "bg-green-600 text-white" : "text-slate-600 hover:bg-slate-50 hover:text-green-600"
              }`}
            >
              <tab.icon className="size-3.5" />
              {tab.label}
            </button>
          ))}
        </div>

        {loading && (
          <Card className="border border-slate-200 bg-white">
            <CardContent className="flex items-center gap-3 p-5 text-sm text-slate-600">
              <Loader2 className="size-4 animate-spin text-green-600" />
              취업 분석 데이터를 불러오는 중입니다.
            </CardContent>
          </Card>
        )}

        {!loading && error && (
          <Card className="border border-red-200 bg-red-50">
            <CardContent className="flex items-center gap-3 p-5 text-sm text-red-700">
              <AlertCircle className="size-4" />
              {error}
            </CardContent>
          </Card>
        )}

        {!loading && !error && stats && (
          <div className="grid gap-3 md:grid-cols-5">
            {[
              { label: "전체 지원 건", value: `${stats.totalApplications}건`, helper: "등록된 지원 건" },
              { label: "분석 완료", value: `${stats.analyzedApplications}건`, helper: "AI 적합도 분석 기준" },
              { label: "평균 적합도", value: `${stats.averageFitScore}점`, helper: `${stats.highFitApplications}건은 70점 이상` },
              { label: "준비 완료", value: `${stats.readyApplications}건`, helper: "READY 또는 APPLIED 상태" },
              {
                label: "모의면접",
                value: `${summary?.interviewTrend.totalSessions ?? 0}회`,
                helper: `세션 평균 ${summary?.interviewTrend.averageSessionScore ?? 0}점 · 답변 평균 ${summary?.interviewTrend.averageAnswerScore ?? 0}점`,
              },
            ].map((item) => (
              <Card key={item.label} className="border border-slate-200 bg-white">
                <CardContent className="p-4">
                  <div className="text-xs font-semibold text-slate-500">{item.label}</div>
                  <div className="mt-1 text-2xl font-black text-slate-900">{item.value}</div>
                  <div className="mt-1 text-[11px] text-slate-400">{item.helper}</div>
                </CardContent>
              </Card>
            ))}
          </div>
        )}

        {/* AI Strategy banner */}
        <Card className={`border-2 border-blue-200 bg-gradient-to-r from-blue-50 to-indigo-50 ${activeTab !== "recommendation" ? "hidden" : ""}`}>
          <CardContent className="p-6">
            <div className="flex items-start gap-4">
              <div className="size-12 rounded-xl bg-gradient-to-br from-blue-600 to-indigo-600 flex items-center justify-center flex-shrink-0">
                <Brain className="size-6 text-white" />
              </div>
              <div className="flex-1">
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <div className="font-bold text-blue-900 mb-2">
                    AI 장기 취업 전략 리포트 <AiResultBadge status={summary?.analysisRun.status} />
                  </div>
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={handleRefresh}
                    disabled={refreshing}
                    className="border-blue-300 bg-white/70 text-blue-700 hover:bg-white"
                    title="AI를 다시 실행해 최신 데이터로 요약을 재생성합니다. 크레딧 1이 차감됩니다."
                  >
                    <RefreshCw className={`size-3.5 ${refreshing ? "animate-spin" : ""}`} />
                    {refreshing ? "재분석 중..." : "재분석 (크레딧 1)"}
                  </Button>
                </div>
                {summary?.analysisRun && (
                  <div className="mb-2 text-xs text-blue-600">
                    {summary.analysisRun.model || "mock"} · {summary.analysisRun.status} · {new Date(summary.analysisRun.createdAt).toLocaleString("ko-KR")}
                  </div>
                )}
                {refreshError && <div className="mb-2 text-xs text-red-600">{refreshError}</div>}
                <p className="text-sm text-blue-700 mb-3">
                  {summary?.trendSummary
                    ? summary.trendSummary
                    : `현재 ${stats?.analyzedApplications ?? 0}개 분석 결과 기준 평균 적합도는 ${stats?.averageFitScore ?? 0}점입니다. 가장 먼저 볼 신호는 ${strongestJob} 준비도와 ${topSkillGap} 보완입니다.`}
                </p>
                <div className="space-y-1.5">
                  {(recommendedDirections.length > 0 ? recommendedDirections : [
                    "지원 건과 적합도 분석 결과가 쌓이면 맞춤 지원 방향이 자동으로 정리됩니다.",
                    "먼저 관심 공고를 등록하고 적합도 분석을 실행해 반복 부족 역량을 확인하세요.",
                  ]).map((direction, index) => (
                    <div key={`${direction}-${index}`} className="flex items-start gap-2 text-sm text-blue-800">
                      <span className="font-black text-blue-500 flex-shrink-0">{index + 1}.</span> {direction}
                    </div>
                  ))}
                </div>
              </div>
            </div>
          </CardContent>
        </Card>

        <div className="grid lg:grid-cols-2 gap-6">
          {/* Skill gaps */}
          <Card className={`border border-slate-200 bg-white ${activeTab !== "weakness" ? "hidden" : ""}`}>
            <CardHeader>
              <CardTitle className="text-base flex items-center gap-2">
                <AlertCircle className="size-4 text-red-500" />
                자주 부족한 역량 ({stats?.analyzedApplications ?? 0}개 분석 기준)
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
              {skillGapData.length > 0 ? (
                skillGapData.map((s) => (
                  <div key={s.skill} className="space-y-1.5">
                    <div className="flex items-center justify-between text-sm">
                      <span className="font-medium text-slate-700">{s.skill}</span>
                      <span className="text-slate-500 text-xs">{s.count}/{s.total}건 부족</span>
                    </div>
                    <Progress value={s.percentage} className="h-2" />
                  </div>
                ))
              ) : (
                <div className="rounded-lg bg-slate-50 p-4 text-sm text-slate-500">
                  아직 반복적으로 부족한 역량이 없습니다. 적합도 분석을 더 실행하면 공고별 요구 역량 차이가 이곳에 누적됩니다.
                </div>
              )}
            </CardContent>
          </Card>

          {/* 반복 강점(자주 활용되는 강점 경험) — 기획 §8.9, 디자인 분석 §6.10 */}
          <Card className={`border border-slate-200 bg-white ${activeTab !== "weakness" ? "hidden" : ""}`}>
            <CardHeader>
              <CardTitle className="text-base flex items-center gap-2">
                <CheckCircle2 className="size-4 text-green-600" />
                반복 강점 ({stats?.analyzedApplications ?? 0}개 분석 기준)
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
              {strengthTrends.length > 0 ? (
                strengthTrends.map((strength) => (
                  <div key={strength.skill} className="space-y-1.5">
                    <div className="flex items-center justify-between text-sm">
                      <span className="font-medium text-slate-700">{strength.skill}</span>
                      <span className="text-slate-500 text-xs">{strength.count}/{strength.total}건 매칭</span>
                    </div>
                    <Progress value={strength.percentage} className="h-2" />
                  </div>
                ))
              ) : (
                <div className="rounded-lg bg-slate-50 p-4 text-sm text-slate-500">
                  아직 반복 강점을 집계할 분석 결과가 없습니다. 적합도 분석이 쌓이면 자주 매칭되는 역량이
                  이곳에 표시되어 면접에서 강조할 경험을 고르는 기준이 됩니다.
                </div>
              )}
            </CardContent>
          </Card>

          {/* 자주 지원하는 직무 분포 — 기획 §8.9, 디자인 분석 §6.10 */}
          <Card className={`border border-slate-200 bg-white ${activeTab !== "trend" ? "hidden" : ""}`}>
            <CardHeader>
              <CardTitle className="text-base flex items-center gap-2">
                <PieChart className="size-4 text-indigo-600" />
                자주 지원하는 직무
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
              {jobDistribution.length > 0 ? (
                jobDistribution.map((job) => (
                  <div key={job.jobTitle} className="space-y-1.5">
                    <div className="flex items-center justify-between gap-2 text-sm">
                      <span className="min-w-0 truncate font-medium text-slate-700">{job.jobTitle}</span>
                      <span className="shrink-0 text-xs text-slate-500">
                        {job.count}건 ({job.percentage}%)
                        {job.averageFitScore != null && ` · 평균 ${job.averageFitScore}점`}
                      </span>
                    </div>
                    <Progress value={job.percentage} className="h-2" />
                  </div>
                ))
              ) : (
                <div className="rounded-lg bg-slate-50 p-4 text-sm text-slate-500">
                  지원 건을 등록하면 직무별 지원 비중과 평균 적합도가 이곳에 정리됩니다.
                </div>
              )}
            </CardContent>
          </Card>

          {/* 자주 개선이 필요한 답변 요소 — 기획 §8.9(답변의 공통 약점) */}
          <Card className={`border border-slate-200 bg-white ${activeTab !== "score" ? "hidden" : ""}`}>
            <CardHeader>
              <CardTitle className="text-base flex items-center gap-2">
                <MessageSquare className="size-4 text-purple-600" />
                자주 개선이 필요한 답변 요소
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-2.5">
              {answerThemes.length > 0 ? (
                answerThemes.map((theme) => (
                  <div key={theme.questionType} className="rounded-lg border border-slate-100 p-3">
                    <div className="flex items-center justify-between gap-2">
                      <span className="text-sm font-semibold text-slate-800">
                        {questionTypeLabel[theme.questionType] ?? theme.questionType}
                      </span>
                      <span className={`text-sm font-black ${theme.averageScore >= 70 ? "text-green-600" : theme.averageScore >= 50 ? "text-amber-600" : "text-red-500"}`}>
                        평균 {theme.averageScore}점
                      </span>
                    </div>
                    <div className="mt-0.5 text-xs text-slate-400">답변 {theme.answerCount}개 기준</div>
                    {theme.sampleFeedback && (
                      <p className="mt-1.5 line-clamp-2 text-xs leading-5 text-slate-600">{theme.sampleFeedback}</p>
                    )}
                  </div>
                ))
              ) : (
                <div className="rounded-lg bg-slate-50 p-4 text-sm text-slate-500">
                  아직 면접 기록이 없어 답변의 공통 약점은 분석할 수 없습니다. 모의면접을 완료하면
                  질문 유형별 평균 점수와 개선 포인트가 이곳에 표시됩니다.
                </div>
              )}
            </CardContent>
          </Card>

          {/* Job readiness */}
          <Card className={`border border-slate-200 bg-white ${activeTab !== "readiness" ? "hidden" : ""}`}>
            <CardHeader>
              <CardTitle className="text-base flex items-center gap-2">
                <Target className="size-4 text-blue-600" />
                직무별 준비도
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              {jobReadiness.length > 0 ? (
                jobReadiness.map((j) => (
                  <div key={j.jobTitle} className="space-y-1.5">
                    <div className="flex items-center justify-between">
                      <div>
                        <span className="font-semibold text-slate-800 text-sm">{j.jobTitle}</span>
                        <span className="text-xs text-slate-400 ml-2">지원 {j.applicationCount}건</span>
                      </div>
                      <div className="flex items-center gap-1">
                        {j.trend === "up" ? (
                          <ArrowUp className="size-3.5 text-green-600" />
                        ) : j.trend === "down" ? (
                          <ArrowDown className="size-3.5 text-red-500" />
                        ) : (
                          <span className="size-3.5 rounded-full border border-slate-300" />
                        )}
                        <span className={`font-black text-sm ${j.readiness >= 70 ? "text-green-600" : j.readiness >= 50 ? "text-amber-600" : "text-red-500"}`}>
                          {j.readiness}점
                        </span>
                      </div>
                    </div>
                    <Progress value={j.readiness} className="h-2" />
                  </div>
                ))
              ) : (
                <div className="rounded-lg bg-slate-50 p-4 text-sm text-slate-500">
                  직무별 준비도를 계산할 분석 결과가 아직 없습니다. 같은 직무군 지원 건이 쌓이면 평균 적합도와 추세가 표시됩니다.
                </div>
              )}
            </CardContent>
          </Card>

          {/* Score history bar chart (visual) */}
          <Card className={`border border-slate-200 bg-white ${activeTab !== "score" ? "hidden" : ""}`}>
            <CardHeader>
              <CardTitle className="text-base flex items-center gap-2">
                <BarChart3 className="size-4 text-purple-600" />
                적합도 점수 변화
              </CardTitle>
            </CardHeader>
            <CardContent>
              {scoreHistory.length > 0 ? (
                <>
                  <div className="flex items-end gap-3 h-32 pt-2">
                    {scoreHistory.map((s, i) => (
                      <div key={`${s.label}-${i}`} className="flex-1 flex flex-col items-center gap-1">
                        <div className="text-xs font-black text-slate-700">{s.score}</div>
                        <div
                          className={`w-full rounded-t-lg transition-all ${i === scoreHistory.length - 1 ? "bg-blue-600" : "bg-blue-200"}`}
                          style={{ height: `${Math.max(8, s.score)}px` }}
                        />
                        <div className="text-[9px] text-slate-400 text-center whitespace-nowrap">{s.label}</div>
                      </div>
                    ))}
                  </div>
                  {scoreHistory.length >= 2 && (
                    <div className={`mt-3 flex items-center gap-2 text-xs ${scoreDelta >= 0 ? "text-green-600" : "text-red-500"}`}>
                      {scoreDelta >= 0 ? <ArrowUp className="size-3.5" /> : <ArrowDown className="size-3.5" />}
                      <span>
                        {scoreHistory.length}회 분석 기준 {scoreDelta >= 0 ? "+" : ""}{scoreDelta}점 변화 ({firstScore}점 → {lastScore}점)
                      </span>
                    </div>
                  )}
                </>
              ) : (
                <div className="rounded-lg bg-slate-50 p-4 text-sm text-slate-500">
                  점수 변화 그래프를 만들 분석 이력이 아직 없습니다. 지원 건별 적합도 분석을 실행하면 시간순 변화가 표시됩니다.
                </div>
              )}
            </CardContent>
          </Card>

          {/* Application trends */}
          <Card className={`border border-slate-200 bg-white ${activeTab !== "trend" ? "hidden" : ""}`}>
            <CardHeader>
              <CardTitle className="text-base flex items-center gap-2">
                <Briefcase className="size-4 text-orange-600" />
                내 지원 경향
              </CardTitle>
            </CardHeader>
            <CardContent>
              <div className="space-y-2">
                {applicationStats.length > 0 ? (
                  applicationStats.map((a) => (
                    <button
                      key={a.id}
                      type="button"
                      onClick={() => navigate(`/applications/${a.id}`)}
                      className="flex w-full items-center gap-3 rounded-lg bg-slate-50 p-2.5 text-left text-sm transition-colors hover:bg-slate-100"
                    >
                      <div className="size-7 rounded-lg bg-gradient-to-br from-blue-600 to-indigo-600 text-white text-xs font-bold flex items-center justify-center flex-shrink-0">
                        {a.company[0]}
                      </div>
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-1.5">
                          <div className="truncate text-xs font-semibold text-slate-800">{a.company}</div>
                          {a.favorite && <span className="rounded bg-amber-100 px-1.5 py-0.5 text-[9px] font-black text-amber-700">관심</span>}
                        </div>
                        <div className="truncate text-[11px] text-slate-500">
                          {a.job} · {formatAnalyzedAt(a.analyzedAt)} · {a.status}
                        </div>
                      </div>
                      <div className="flex items-center gap-1">
                        {a.trend === "up" ? <ArrowUp className="size-3 text-green-500" /> : a.trend === "down" ? <ArrowDown className="size-3 text-red-500" /> : null}
                        <span className={`font-black text-xs ${a.score >= 70 ? "text-green-600" : a.score >= 50 ? "text-amber-600" : "text-red-500"}`}>{a.score}점</span>
                      </div>
                    </button>
                  ))
                ) : (
                  <div className="rounded-lg bg-slate-50 p-4 text-sm text-slate-500">
                    아직 표시할 지원 경향이 없습니다. 지원 건을 등록하고 적합도 분석을 실행하면 공고별 점수와 상태가 누적됩니다.
                  </div>
                )}
              </div>
            </CardContent>
          </Card>
        </div>

        {/* Recommended direction */}
        <Card className={`border border-slate-200 bg-white ${activeTab !== "recommendation" ? "hidden" : ""}`}>
          <CardHeader>
            <CardTitle className="text-base flex items-center gap-2">
              <BookOpen className="size-4 text-teal-600" />
              AI 추천 학습 로드맵
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="grid md:grid-cols-3 gap-4">
              {recommendationBuckets.map((phase) => (
                <div key={phase.phase} className={`border ${phase.color} rounded-xl p-4`}>
                  <div className={`font-bold text-sm mb-3 ${phase.textColor}`}>{phase.phase}</div>
                  <ul className="space-y-2">
                    {(phase.items.length > 0 ? phase.items : ["추가 분석 결과가 쌓이면 이 단계의 추천 액션이 자동으로 채워집니다."]).map((item) => (
                      <li key={item} className="flex items-start gap-2 text-xs text-slate-700">
                        <div className="size-3.5 rounded-sm border-2 border-slate-300 flex-shrink-0 mt-0.5" />
                        {item}
                      </li>
                    ))}
                  </ul>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
