import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate } from "react-router";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Badge } from "@/app/components/ui/badge";
import { Progress } from "@/app/components/ui/progress";
import {
  Plus, Briefcase, MessageSquare, TrendingUp, Award, ArrowRight,
  FileText, BarChart3, AlertCircle, ChevronRight,
  Target, BookOpen, Bell, Calendar, Flame, Loader2, RefreshCw, Brain,
} from "lucide-react";
import { getDashboardSummary, refreshDashboardSummary } from "@/features/dashboard/api/dashboardApi";
import type { DashboardActivity, DashboardSummary, DashboardTodo } from "@/features/dashboard/types/dashboardSummary";
import { TodoChecklist } from "@/features/dashboard/components/TodoChecklist";
import { RecentInterviewCard } from "@/features/dashboard/components/RecentInterviewCard";
import { NotificationsCard } from "@/features/dashboard/components/NotificationsCard";
import { ReadinessGaugeCard } from "@/features/dashboard/components/ReadinessGaugeCard";
import { AiResultBadge } from "@/features/analysis/components/AiResultBadge";

const statusLabel: Record<string, string> = {
  DRAFT: "공고 입력",
  ANALYZING: "분석 중",
  READY: "준비중",
  APPLIED: "지원 완료",
  CLOSED: "마감",
};

const statusColor: Record<string, string> = {
  DRAFT: "bg-slate-100 text-slate-700",
  ANALYZING: "bg-amber-100 text-amber-700",
  READY: "bg-blue-100 text-blue-700",
  APPLIED: "bg-green-100 text-green-700",
  CLOSED: "bg-zinc-100 text-zinc-500",
};

function formatDate(value: string | null) {
  if (!value) return "날짜 없음";
  return new Intl.DateTimeFormat("ko-KR", {
    month: "2-digit",
    day: "2-digit",
  }).format(new Date(value));
}

function formatRelativeTime(value: string) {
  const diffMs = Date.now() - new Date(value).getTime();
  const minutes = Math.floor(diffMs / 60000);
  if (minutes < 1) return "방금 전";
  if (minutes < 60) return `${minutes}분 전`;

  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}시간 전`;

  const days = Math.floor(hours / 24);
  if (days < 7) return `${days}일 전`;

  return formatDate(value);
}

function activityMeta(type: DashboardActivity["type"]) {
  if (type === "INTERVIEW") return { icon: MessageSquare, color: "text-purple-600" };
  if (type === "APPLICATION") return { icon: Target, color: "text-orange-600" };
  return { icon: FileText, color: "text-blue-600" };
}

export function DashboardPage() {
  const navigate = useNavigate();
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [refreshing, setRefreshing] = useState(false);
  const [refreshError, setRefreshError] = useState<string | null>(null);

  useEffect(() => {
    let ignore = false;
    setLoading(true);
    setError(null);

    getDashboardSummary()
      .then((data) => {
        if (!ignore) setSummary(data);
      })
      .catch((requestError) => {
        if (!ignore) setError(requestError instanceof Error ? requestError.message : "대시보드 데이터를 불러오지 못했습니다.");
      })
      .finally(() => {
        if (!ignore) setLoading(false);
      });

    return () => {
      ignore = true;
    };
  }, []);

  const handleRefreshSummary = async () => {
    setRefreshing(true);
    setRefreshError(null);
    try {
      setSummary(await refreshDashboardSummary());
    } catch (requestError) {
      setRefreshError(requestError instanceof Error ? requestError.message : "요약 재생성에 실패했습니다.");
    } finally {
      setRefreshing(false);
    }
  };

  const handleTodosChange = (todos: DashboardTodo[]) => {
    setSummary((previous) => (previous ? { ...previous, todos } : previous));
  };

  const stats = summary?.stats;
  const pendingTodos = summary?.todos.filter((todo) => !todo.done).length ?? 0;
  const creditPercent = stats ? Math.min(100, Math.round((stats.credit / Math.max(1, stats.creditLimit)) * 100)) : 0;
  const highFitCount = summary?.recentApplications.filter((application) => (application.fitScore ?? 0) >= 70).length ?? 0;
  const promisingApplication = useMemo(() => {
    return summary?.promisingApplication ?? [...(summary?.recentApplications ?? [])]
      .filter((application) => application.fitScore != null && application.status !== "CLOSED")
      .sort((a, b) => (b.fitScore ?? 0) - (a.fitScore ?? 0))[0] ?? null;
  }, [summary?.promisingApplication, summary?.recentApplications]);
  const urgentGap = summary?.skillGaps[0] ?? null;
  const statCards = useMemo(() => {
    if (!stats) return [];

    return [
      {
        icon: Briefcase,
        label: "활성 지원 건",
        value: `${stats.activeApplications}`,
        sub: `이번 달 ${stats.newApplicationsThisMonth}건 추가`,
        color: "from-blue-500 to-cyan-500",
      },
      {
        icon: MessageSquare,
        label: "총 모의면접",
        value: `${stats.totalInterviews}`,
        sub: `이번 주 ${stats.interviewsThisWeek}회`,
        color: "from-purple-500 to-violet-500",
      },
      {
        icon: Award,
        label: "보유 크레딧",
        value: `${stats.credit}`,
        sub: `이번 달 ${stats.creditsUsedThisMonth}크레딧 사용`,
        color: "from-amber-500 to-orange-500",
      },
      {
        icon: TrendingUp,
        label: "평균 적합도",
        value: `${stats.averageFitScore}점`,
        sub: `${highFitCount}건은 우선 지원 후보`,
        color: "from-green-500 to-emerald-500",
      },
    ];
  }, [highFitCount, stats]);

  return (
    <div className="bg-slate-50 min-h-screen">
      {/* 모바일: 하단 고정 CTA 공간 확보(pb-24). 데스크톱은 기존 여백 유지. */}
      <div className="max-w-[1400px] mx-auto px-4 sm:px-6 py-8 space-y-8 pb-24 lg:pb-8">
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div>
            <div className="flex items-center gap-2 text-sm text-slate-500 mb-1">
              <Bell className="size-4" />
              <span>{pendingTodos > 0 ? `오늘 확인할 항목 ${pendingTodos}건이 있습니다.` : "오늘 확인할 항목이 정리되어 있습니다."}</span>
            </div>
            <h1 className="text-2xl font-black text-slate-900">안녕하세요, {summary?.user.name ?? "지원자"} 님</h1>
            <p className="text-slate-500 mt-1 text-sm">
              {summary?.focus.readiness != null ? (
                <>
                  {summary.focus.headline} <strong className="text-blue-600">{summary.focus.readiness}%</strong> 기준으로 다음 행동을 정리했습니다.
                </>
              ) : (
                summary?.focus.description ?? "지원 건을 등록하면 오늘의 준비 현황이 자동으로 정리됩니다."
              )}
            </p>
            {summary?.aiSummary && (
              <div className="mt-3 rounded-lg bg-blue-50 px-3 py-2 text-sm text-blue-800">
                <div className="flex items-start justify-between gap-3">
                  <p>
                    <strong className="font-semibold">AI 요약</strong> <AiResultBadge status={summary.analysisRun.status} /> · {summary.aiSummary}
                  </p>
                  <button
                    type="button"
                    onClick={handleRefreshSummary}
                    disabled={refreshing}
                    title="AI를 다시 실행해 최신 데이터로 요약을 재생성합니다. 크레딧 1이 차감됩니다."
                    className="flex shrink-0 items-center gap-1 rounded-md border border-blue-200 bg-white/70 px-2 py-1 text-xs font-semibold text-blue-700 transition-colors hover:bg-white disabled:opacity-60"
                  >
                    <RefreshCw className={`size-3 ${refreshing ? "animate-spin" : ""}`} />
                    {refreshing ? "재생성 중" : "재생성 (크레딧 1)"}
                  </button>
                </div>
                <p className="mt-1 text-xs text-blue-600">
                  {summary.analysisRun.model || "mock"} · {summary.analysisRun.status} · {new Date(summary.analysisRun.createdAt).toLocaleString("ko-KR")}
                </p>
                {refreshError && <p className="mt-1 text-xs text-red-600">{refreshError}</p>}
              </div>
            )}
          </div>
          <Button
            className="bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-700 hover:to-indigo-700 gap-2"
            onClick={() => navigate("/applications")}
          >
            <Plus className="size-4" />
            새 지원 건 만들기
          </Button>
        </div>

        {loading && (
          <Card className="border border-slate-200 bg-white">
            <CardContent className="flex items-center gap-3 p-5 text-sm text-slate-600">
              <Loader2 className="size-4 animate-spin text-blue-600" />
              대시보드 데이터를 불러오는 중입니다.
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

        {!loading && !error && summary && (
          <>
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
              {statCards.map((s) => (
                <Card key={s.label} className="border border-slate-200 bg-white hover:shadow-md transition-shadow">
                  <CardContent className="p-5">
                    <div className="flex items-start justify-between gap-3">
                      <div>
                        <div className="text-sm text-slate-500 mb-1">{s.label}</div>
                        <div className="text-3xl font-black text-slate-900">{s.value}</div>
                        <div className="text-xs text-slate-400 mt-1">{s.sub}</div>
                      </div>
                      <div className={`size-10 rounded-xl bg-gradient-to-br ${s.color} flex items-center justify-center`}>
                        <s.icon className="size-5 text-white" />
                      </div>
                    </div>
                  </CardContent>
                </Card>
              ))}
            </div>

            <div className="grid lg:grid-cols-3 gap-6">
              <div className="lg:col-span-2 space-y-4">
                <div className="flex items-center justify-between">
                  <h2 className="font-bold text-slate-900 text-lg">내 지원 건</h2>
                  <Link to="/applications" className="text-sm text-blue-600 hover:text-blue-700 flex items-center gap-1">
                    전체 보기 <ArrowRight className="size-3.5" />
                  </Link>
                </div>
                {/* 지원 상태별 분포 한눈 요약. */}
                {(summary.statusCounts?.length ?? 0) > 0 && (
                  <div className="flex flex-wrap gap-1.5">
                    {summary.statusCounts.map((item) => (
                      <span
                        key={item.status}
                        className={`rounded-full px-2.5 py-1 text-xs font-semibold ${statusColor[item.status] ?? "bg-slate-100 text-slate-700"}`}
                      >
                        {statusLabel[item.status] ?? item.status} {item.count}건
                      </span>
                    ))}
                  </div>
                )}
                <div className="space-y-3">
                  {summary.recentApplications.length > 0 ? (
                    summary.recentApplications.map((app) => (
                      <Link to={`/applications/${app.id}`} key={app.id}>
                        <Card className="border border-slate-200 bg-white hover:border-blue-300 hover:shadow-md transition-all cursor-pointer">
                          <CardContent className="p-4">
                            <div className="flex items-center gap-4">
                              <div className="size-10 rounded-xl bg-gradient-to-br from-blue-600 to-indigo-600 flex items-center justify-center text-white font-bold text-sm flex-shrink-0">
                                {app.companyName[0]}
                              </div>
                              <div className="flex-1 min-w-0">
                                <div className="flex items-center gap-2 flex-wrap">
                                  <span className="font-semibold text-slate-800 text-sm">{app.companyName}</span>
                                  <span className="text-slate-400 text-xs">·</span>
                                  <span className="text-slate-600 text-sm">{app.jobTitle}</span>
                                  {app.favorite && <Badge className="bg-amber-100 text-amber-700 text-xs">관심</Badge>}
                                  <Badge className={`text-xs ${statusColor[app.status] ?? "bg-slate-100 text-slate-700"}`}>
                                    {statusLabel[app.status] ?? app.status}
                                  </Badge>
                                </div>
                                <div className="flex flex-wrap items-center gap-3 mt-1.5">
                                  <div className="flex items-center gap-1.5 flex-1 min-w-36">
                                    <div className="text-xs text-slate-400">적합도</div>
                                    <Progress value={app.fitScore ?? 0} className="h-1.5 flex-1 max-w-24" />
                                    <span className="text-xs font-semibold text-blue-600">{app.fitScore != null ? `${app.fitScore}점` : "미분석"}</span>
                                  </div>
                                  <div className="flex gap-1">
                                    {app.tags.map((tag) => (
                                      <span key={tag} className="text-[10px] bg-slate-100 text-slate-600 px-1.5 py-0.5 rounded">{tag}</span>
                                    ))}
                                  </div>
                                  <div className="text-xs text-slate-400 flex items-center gap-1 flex-shrink-0">
                                    <Calendar className="size-3" />
                                    {formatDate(app.postingDate ?? app.updatedAt)}
                                  </div>
                                </div>
                              </div>
                              <ChevronRight className="size-4 text-slate-400 flex-shrink-0" />
                            </div>
                          </CardContent>
                        </Card>
                      </Link>
                    ))
                  ) : (
                    <Card className="border border-slate-200 bg-white">
                      <CardContent className="p-5 text-sm text-slate-500">
                        아직 등록된 지원 건이 없습니다. 첫 공고를 등록하면 적합도와 다음 행동이 이곳에 표시됩니다.
                      </CardContent>
                    </Card>
                  )}
                </div>

                <div className="mt-6">
                  <h2 className="font-bold text-slate-900 text-lg mb-4">최근 활동</h2>
                  <Card className="border border-slate-200 bg-white">
                    <CardContent className="p-5 space-y-4">
                      {summary.activities.length > 0 ? (
                        summary.activities.map((activity, index) => {
                          const meta = activityMeta(activity.type);
                          return (
                            <div key={`${activity.type}-${activity.occurredAt}-${index}`} className="flex items-start gap-3">
                              <div className="size-8 rounded-lg bg-slate-100 flex items-center justify-center flex-shrink-0 mt-0.5">
                                <meta.icon className={`size-4 ${meta.color}`} />
                              </div>
                              <div className="flex-1">
                                <div className="text-sm text-slate-700">{activity.content}</div>
                                <div className="text-xs text-slate-400 mt-0.5">{formatRelativeTime(activity.occurredAt)}</div>
                              </div>
                            </div>
                          );
                        })
                      ) : (
                        <div className="text-sm text-slate-500">아직 기록된 활동이 없습니다.</div>
                      )}
                    </CardContent>
                  </Card>
                </div>
              </div>

              <div className="space-y-5">
                {(promisingApplication || urgentGap) && (
                  <Card className="border border-blue-200 bg-gradient-to-br from-blue-50 to-indigo-50">
                    <CardHeader className="pb-3">
                      <CardTitle className="text-base flex items-center gap-2 text-blue-900">
                        <Target className="size-4 text-blue-600" />
                        이번 주 우선순위
                      </CardTitle>
                    </CardHeader>
                    <CardContent className="space-y-3">
                      {promisingApplication && (
                        <button
                          type="button"
                          onClick={() => navigate(`/applications/${promisingApplication.id}`)}
                          className="w-full rounded-lg border border-blue-100 bg-white/80 p-3 text-left transition-colors hover:bg-white"
                        >
                          <div className="text-[11px] font-semibold text-blue-500">가장 유망한 지원 건</div>
                          <div className="mt-1 text-sm font-bold text-slate-800">
                            {promisingApplication.companyName} · {promisingApplication.jobTitle}
                          </div>
                          <div className="mt-1 text-xs text-slate-500">
                            적합도 <strong className="text-blue-700">{promisingApplication.fitScore}점</strong> · 지금 준비 흐름을 먼저 점검하세요.
                          </div>
                        </button>
                      )}
                      {urgentGap && (
                        <Link
                          to="/analysis?tab=weakness"
                          className="block rounded-lg border border-amber-100 bg-amber-50/90 p-3 transition-colors hover:bg-amber-50"
                        >
                          <div className="text-[11px] font-semibold text-amber-600">가장 시급한 보완 역량</div>
                          <div className="mt-1 text-sm font-bold text-slate-800">{urgentGap.skill}</div>
                          <div className="mt-1 text-xs text-slate-500">
                            최근 분석 {urgentGap.total}건 중 {urgentGap.count}건({urgentGap.percentage}%)에서 반복 부족
                          </div>
                        </Link>
                      )}
                    </CardContent>
                  </Card>
                )}

                {/* C 담당: 전체 취업 준비도 게이지 + 최근 변화 요약(결정적 집계). */}
                {summary.readiness && summary.recentChange && (
                  <ReadinessGaugeCard readiness={summary.readiness} recentChange={summary.recentChange} />
                )}

                {(summary.aiHistory?.length ?? 0) > 0 && (
                  <Card className="border border-indigo-200 bg-white">
                    <CardHeader className="pb-3"><CardTitle className="flex items-center gap-2 text-base"><Brain className="size-4 text-indigo-600" />AI 요약 재생성 이력</CardTitle></CardHeader>
                    <CardContent className="space-y-2">
                      {summary.aiHistory?.map((run) => (
                        <div key={run.id} className="rounded-lg border border-slate-100 p-2.5 text-xs">
                          <div className="flex items-center justify-between gap-2"><strong className="text-slate-700">{run.promptVersion ?? "버전 미기록"} · {run.model ?? "mock"}</strong><AiResultBadge status={run.status} /></div>
                          <div className="mt-1 text-slate-400">{new Date(run.createdAt).toLocaleString("ko-KR")} · {run.tokenUsage.toLocaleString()} 토큰</div>
                        </div>
                      ))}
                    </CardContent>
                  </Card>
                )}

                <Card className="border border-slate-200 bg-white">
                  <CardHeader className="pb-3">
                    <CardTitle className="text-base flex items-center gap-2">
                      <Flame className="size-4 text-orange-500" />
                      오늘의 할 일
                    </CardTitle>
                  </CardHeader>
                  <CardContent>
                    <TodoChecklist todos={summary.todos} onTodosChange={handleTodosChange} />
                  </CardContent>
                </Card>

                <RecentInterviewCard interview={summary.recentInterview} />

                <Card className="border border-amber-200 bg-amber-50">
                  <CardHeader className="pb-3">
                    <CardTitle className="text-base flex items-center gap-2 text-amber-800">
                      <Award className="size-4 text-amber-600" />
                      크레딧 현황
                    </CardTitle>
                  </CardHeader>
                  <CardContent className="space-y-3">
                    <div className="text-center py-2">
                      <div className="text-4xl font-black text-amber-700">{summary.stats.credit}</div>
                      <div className="text-sm text-amber-600">/ {summary.stats.creditLimit} 크레딧 잔여</div>
                      <Progress value={creditPercent} className="mt-2 h-2" />
                    </div>
                    <div className="space-y-1.5 text-xs text-amber-700">
                      <div className="flex justify-between">
                        <span>이번 달 사용</span>
                        <span className="font-semibold">{summary.stats.creditsUsedThisMonth} 크레딧</span>
                      </div>
                      <div className="flex justify-between">
                        <span>현재 플랜</span>
                        <span className="font-semibold">{summary.user.plan}</span>
                      </div>
                    </div>
                    <Button size="sm" variant="outline" className="w-full border-amber-400 text-amber-700 hover:bg-amber-100" onClick={() => navigate("/pricing")}>
                      크레딧 충전하기
                    </Button>
                  </CardContent>
                </Card>

                <Card className="border border-slate-200 bg-white">
                  <CardHeader className="pb-3">
                    <CardTitle className="text-base">빠른 메뉴</CardTitle>
                  </CardHeader>
                  <CardContent className="grid grid-cols-2 gap-2">
                    {[
                      { label: "새 지원 건", icon: Plus, href: "/applications", color: "text-blue-600" },
                      { label: "가상 면접", icon: MessageSquare, href: "/interview", color: "text-purple-600" },
                      { label: "취업 분석", icon: BarChart3, href: "/analysis", color: "text-green-600" },
                      { label: "커뮤니티", icon: BookOpen, href: "/community", color: "text-orange-600" },
                    ].map((menu) => (
                      <Link key={menu.label} to={menu.href}>
                        <div className="flex flex-col items-center gap-1.5 p-3 rounded-xl bg-slate-50 hover:bg-blue-50 transition-colors cursor-pointer">
                          <menu.icon className={`size-5 ${menu.color}`} />
                          <span className="text-xs font-medium text-slate-700">{menu.label}</span>
                        </div>
                      </Link>
                    ))}
                  </CardContent>
                </Card>

                {/* 위험 알림 카드: 반복 부족 역량이 절반 이상 분석에서 나타나면 대시보드 안에서 경고한다(푸시 알림은 F 도메인). */}
                {summary.skillGaps.some((gap) => gap.percentage >= 50 && gap.total >= 2) && (
                  <Card className="border border-red-200 bg-red-50">
                    <CardContent className="p-4">
                      {(() => {
                        const critical = summary.skillGaps.find((gap) => gap.percentage >= 50 && gap.total >= 2)!;
                        return (
                          <div className="flex items-start gap-2.5">
                            <AlertCircle className="mt-0.5 size-4 shrink-0 text-red-600" />
                            <div>
                              <div className="text-sm font-bold text-red-800">주의: {critical.skill} 반복 부족</div>
                              <p className="mt-1 text-xs leading-5 text-red-700">
                                최근 분석 {critical.total}건 중 {critical.count}건에서 부족 역량으로 나타났습니다.
                                학습 로드맵에서 {critical.skill} 과제를 우선 진행하는 것이 좋습니다.
                              </p>
                            </div>
                          </div>
                        );
                      })()}
                    </CardContent>
                  </Card>
                )}

                <Card className="border border-slate-200 bg-white">
                  <CardHeader className="pb-3">
                    <CardTitle className="text-base flex items-center gap-2">
                      <AlertCircle className="size-4 text-red-500" />
                      자주 부족한 역량
                    </CardTitle>
                  </CardHeader>
                  <CardContent className="space-y-2">
                    {summary.skillGaps.length > 0 ? (
                      summary.skillGaps.map((skill) => (
                        <div key={skill.skill} className="space-y-1">
                          <div className="flex justify-between text-xs">
                            <span className="text-slate-700 font-medium">{skill.skill}</span>
                            <span className="text-slate-400">{skill.count}/{skill.total}건</span>
                          </div>
                          <Progress value={skill.percentage} className="h-1.5" />
                        </div>
                      ))
                    ) : (
                      <div className="text-sm text-slate-500">
                        반복 부족 역량이 아직 없습니다. 적합도 분석 결과가 쌓이면 자동으로 정리됩니다.
                      </div>
                    )}
                  </CardContent>
                </Card>

                <NotificationsCard notifications={summary.recentNotifications} />
              </div>
            </div>
          </>
        )}
      </div>

      {/* 모바일 하단 고정 주요 CTA 1개(디자인 분석 §7.1, 모바일 고려 §6.1). 데스크톱에서는 상단 버튼 사용. */}
      <div
        className="fixed inset-x-4 z-40 lg:hidden"
        style={{ bottom: "calc(1rem + env(safe-area-inset-bottom, 0px))" }}
      >
        <Button
          className="h-12 w-full bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-700 hover:to-indigo-700 gap-2 shadow-lg"
          onClick={() => navigate("/applications")}
        >
          <Plus className="size-4" />
          새 지원 건 만들기
        </Button>
      </div>
    </div>
  );
}
