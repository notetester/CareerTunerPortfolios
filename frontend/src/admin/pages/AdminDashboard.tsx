import { useEffect, useMemo, useState } from "react";
import { Link, useLocation } from "react-router";
import { Badge } from "../../app/components/ui/badge";
import { Button } from "../../app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "../../app/components/ui/card";
import { Progress } from "../../app/components/ui/progress";
import {
  AlertTriangle,
  BarChart3,
  Briefcase,
  CreditCard,
  Gauge,
  Loader2,
  MessageSquare,
  ShieldCheck,
  Users,
} from "lucide-react";
import { getAdminAnalyticsSummary } from "@/admin/features/analytics/api/adminAnalyticsApi";
import type { AdminAnalyticsSummary } from "@/admin/features/analytics/types/adminAnalytics";

const routeLabels: Record<string, string> = {
  "/admin": "관리자 분석 대시보드",
  "/admin/users": "회원 분석 현황",
  "/admin/payments": "결제 관리",
  "/admin/ai-usage": "AI 사용량 관리",
  "/admin/community": "게시판/신고 관리",
  "/admin/notices": "공지사항 관리",
  "/admin/plans": "요금제 관리",
  "/admin/prompts": "프롬프트 템플릿",
  "/admin/logs": "시스템 로그",
};

const planLabel: Record<string, string> = {
  FREE: "무료",
  BASIC: "베이직",
  PRO: "프로",
  PREMIUM: "프리미엄",
};

const statusLabel: Record<string, string> = {
  DRAFT: "초안",
  ANALYZING: "분석 중",
  READY: "준비 완료",
  APPLIED: "지원 완료",
  CLOSED: "종료",
};

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
}

export function AdminDashboardPage() {
  const location = useLocation();
  const [summary, setSummary] = useState<AdminAnalyticsSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const currentLabel = routeLabels[location.pathname] ?? "관리자 분석 대시보드";

  useEffect(() => {
    let ignore = false;
    setLoading(true);
    setError(null);

    getAdminAnalyticsSummary()
      .then((data) => {
        if (!ignore) setSummary(data);
      })
      .catch((requestError) => {
        if (!ignore) setError(requestError instanceof Error ? requestError.message : "관리자 분석 통계를 불러오지 못했습니다.");
      })
      .finally(() => {
        if (!ignore) setLoading(false);
      });

    return () => {
      ignore = true;
    };
  }, []);

  const statusCards = useMemo(() => {
    if (!summary) return [];
    const { stats } = summary;
    return [
      {
        label: "전체 회원",
        value: stats.totalUsers.toLocaleString(),
        sub: `활성 회원 ${stats.activeUsers.toLocaleString()}명`,
        icon: Users,
        tone: "text-blue-600",
        bg: "bg-blue-50",
      },
      {
        label: "전체 지원 건",
        value: stats.totalApplications.toLocaleString(),
        sub: `면접 세션 ${stats.totalInterviews.toLocaleString()}회`,
        icon: Briefcase,
        tone: "text-emerald-600",
        bg: "bg-emerald-50",
      },
      {
        label: "적합도 분석",
        value: stats.analyzedApplications.toLocaleString(),
        sub: `평균 적합도 ${stats.averageFitScore}점`,
        icon: BarChart3,
        tone: "text-purple-600",
        bg: "bg-purple-50",
      },
      {
        label: "이번 달 AI 크레딧",
        value: stats.creditsUsedThisMonth.toLocaleString(),
        sub: "AI 사용 로그 차감 기준",
        icon: CreditCard,
        tone: "text-amber-600",
        bg: "bg-amber-50",
      },
    ];
  }, [summary]);

  const maxDailyTokens = Math.max(1, ...(summary?.dailyUsage.map((usage) => usage.tokenUsage) ?? [1]));

  return (
    <div className="min-h-screen bg-slate-50">
      <div className="mx-auto w-full max-w-[1400px] space-y-6 px-4 py-8 sm:px-6">
        <section className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <Badge className="mb-3 bg-slate-900 text-white">Admin Analytics</Badge>
            <h1 className="flex items-center gap-2 text-2xl font-black text-slate-900">
              <ShieldCheck className="size-6 text-blue-600" />
              {currentLabel}
            </h1>
            <p className="mt-1 text-sm text-slate-500">
              회원, 지원 건, 적합도 분석, 면접, AI 사용량을 실제 운영 데이터 기준으로 점검합니다.
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            <Button asChild variant="outline">
              <Link to="/admin/ai-usage">AI 사용량 보기</Link>
            </Button>
            <Button asChild className="bg-gradient-to-r from-blue-600 to-indigo-600">
              <Link to="/admin">통계 새로 보기</Link>
            </Button>
          </div>
        </section>

        {loading && (
          <Card className="border border-slate-200 bg-white">
            <CardContent className="flex items-center gap-3 p-5 text-sm text-slate-600">
              <Loader2 className="size-4 animate-spin text-blue-600" />
              관리자 분석 통계를 불러오는 중입니다.
            </CardContent>
          </Card>
        )}

        {!loading && error && (
          <Card className="border border-red-200 bg-red-50">
            <CardContent className="flex items-center gap-3 p-5 text-sm text-red-700">
              <AlertTriangle className="size-4" />
              {error}
            </CardContent>
          </Card>
        )}

        {!loading && !error && summary && (
          <>
            <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
              {statusCards.map((card) => (
                <Card key={card.label} className="border border-slate-200 bg-white">
                  <CardContent className="p-5">
                    <div className="flex items-start justify-between gap-3">
                      <div>
                        <div className="text-sm text-slate-500">{card.label}</div>
                        <div className="mt-1 text-3xl font-black text-slate-900">{card.value}</div>
                        <div className="mt-1 text-xs text-slate-400">{card.sub}</div>
                      </div>
                      <div className={`flex size-10 items-center justify-center rounded-xl ${card.bg}`}>
                        <card.icon className={`size-5 ${card.tone}`} />
                      </div>
                    </div>
                  </CardContent>
                </Card>
              ))}
            </section>

            <section className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_380px]">
              <div className="space-y-6">
                <div className="grid gap-4 md:grid-cols-2">
                  <Card className="border border-slate-200 bg-white">
                    <CardHeader className="pb-3">
                      <CardTitle className="flex items-center gap-2 text-base">
                        <Users className="size-4 text-blue-600" />
                        플랜별 회원 분포
                      </CardTitle>
                    </CardHeader>
                    <CardContent className="space-y-3">
                      {summary.planDistribution.length > 0 ? (
                        summary.planDistribution.map((plan) => (
                          <div key={plan.label} className="flex items-center justify-between rounded-lg bg-slate-50 px-3 py-2 text-sm">
                            <span className="font-semibold text-slate-700">{planLabel[plan.label] ?? plan.label}</span>
                            <span className="text-slate-500">{plan.count.toLocaleString()}명</span>
                          </div>
                        ))
                      ) : (
                        <div className="text-sm text-slate-500">플랜 분포 데이터가 없습니다.</div>
                      )}
                    </CardContent>
                  </Card>

                  <Card className="border border-slate-200 bg-white">
                    <CardHeader className="pb-3">
                      <CardTitle className="flex items-center gap-2 text-base">
                        <Briefcase className="size-4 text-emerald-600" />
                        지원 상태 분포
                      </CardTitle>
                    </CardHeader>
                    <CardContent className="space-y-3">
                      {summary.applicationStatusDistribution.length > 0 ? (
                        summary.applicationStatusDistribution.map((status) => (
                          <div key={status.label} className="flex items-center justify-between rounded-lg bg-slate-50 px-3 py-2 text-sm">
                            <span className="font-semibold text-slate-700">{statusLabel[status.label] ?? status.label}</span>
                            <span className="text-slate-500">{status.count.toLocaleString()}건</span>
                          </div>
                        ))
                      ) : (
                        <div className="text-sm text-slate-500">지원 상태 데이터가 없습니다.</div>
                      )}
                    </CardContent>
                  </Card>
                </div>

                <Card className="border border-slate-200 bg-white">
                  <CardHeader className="pb-3">
                    <CardTitle className="flex items-center gap-2 text-base">
                      <BarChart3 className="size-4 text-purple-600" />
                      최근 적합도 분석
                    </CardTitle>
                  </CardHeader>
                  <CardContent className="space-y-2">
                    {summary.recentAnalyses.length > 0 ? (
                      summary.recentAnalyses.map((analysis) => (
                        <div key={analysis.fitAnalysisId} className="flex flex-col gap-2 rounded-lg border border-slate-100 p-3 sm:flex-row sm:items-center">
                          <div className="min-w-0 flex-1">
                            <div className="truncate text-sm font-semibold text-slate-800">
                              {analysis.companyName} · {analysis.jobTitle}
                            </div>
                            <div className="truncate text-xs text-slate-400">
                              {analysis.userName} ({analysis.userEmail}) · {formatDateTime(analysis.analyzedAt)}
                            </div>
                          </div>
                          <Badge className={`${(analysis.fitScore ?? 0) >= 70 ? "bg-green-100 text-green-700" : "bg-amber-100 text-amber-700"}`}>
                            {analysis.fitScore ?? 0}점
                          </Badge>
                        </div>
                      ))
                    ) : (
                      <div className="text-sm text-slate-500">최근 적합도 분석 데이터가 없습니다.</div>
                    )}
                  </CardContent>
                </Card>
              </div>

              <aside className="space-y-4">
                <Card className="border border-slate-200 bg-white">
                  <CardHeader className="pb-3">
                    <CardTitle className="flex items-center gap-2 text-base">
                      <Gauge className="size-4 text-purple-600" />
                      적합도 점수 분포
                    </CardTitle>
                  </CardHeader>
                  <CardContent className="space-y-4">
                    {summary.fitScoreBands.map((band) => (
                      <div key={band.label} className="space-y-1.5">
                        <div className="flex items-center justify-between gap-3 text-sm">
                          <span className="font-semibold text-slate-700">{band.label}</span>
                          <span className="text-xs text-slate-400">{band.count}건 · {band.percentage}%</span>
                        </div>
                        <Progress value={band.percentage} className="h-2" />
                      </div>
                    ))}
                  </CardContent>
                </Card>

                <Card className="border border-amber-200 bg-amber-50">
                  <CardHeader className="pb-3">
                    <CardTitle className="flex items-center gap-2 text-base text-amber-900">
                      <AlertTriangle className="size-4 text-amber-600" />
                      반복 부족 역량
                    </CardTitle>
                  </CardHeader>
                  <CardContent className="space-y-3">
                    {summary.skillGaps.length > 0 ? (
                      summary.skillGaps.slice(0, 6).map((gap) => (
                        <div key={gap.skill} className="rounded-lg bg-white/70 px-3 py-2">
                          <div className="flex items-center justify-between gap-3 text-sm">
                            <span className="font-semibold text-amber-900">{gap.skill}</span>
                            <span className="text-xs text-amber-700">{gap.count}/{gap.total}건</span>
                          </div>
                          <Progress value={gap.percentage} className="mt-2 h-1.5" />
                        </div>
                      ))
                    ) : (
                      <div className="text-sm text-amber-800">반복 부족 역량 데이터가 없습니다.</div>
                    )}
                  </CardContent>
                </Card>

                <Card className="border border-slate-200 bg-white">
                  <CardHeader className="pb-3">
                    <CardTitle className="flex items-center gap-2 text-base">
                      <MessageSquare className="size-4 text-blue-600" />
                      최근 7일 AI 사용량
                    </CardTitle>
                  </CardHeader>
                  <CardContent className="space-y-3">
                    {summary.dailyUsage.length > 0 ? (
                      summary.dailyUsage.map((usage) => (
                        <div key={usage.date} className="space-y-1.5">
                          <div className="flex items-center justify-between text-xs">
                            <span className="font-semibold text-slate-600">{formatDateTime(`${usage.date}T00:00:00`)}</span>
                            <span className="text-slate-400">{usage.tokenUsage.toLocaleString()} 토큰 · {usage.creditUsed} 크레딧</span>
                          </div>
                          <Progress value={(usage.tokenUsage / maxDailyTokens) * 100} className="h-1.5" />
                        </div>
                      ))
                    ) : (
                      <div className="text-sm text-slate-500">최근 7일 AI 사용 기록이 없습니다.</div>
                    )}
                  </CardContent>
                </Card>
              </aside>
            </section>
          </>
        )}
      </div>
    </div>
  );
}
