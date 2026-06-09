import { useEffect, useMemo, useState } from "react";
import {
  AlertTriangle,
  BarChart3,
  Brain,
  ChevronDown,
  ChevronRight,
  Gauge,
  Loader2,
  RefreshCw,
  Search,
  Users,
} from "lucide-react";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Progress } from "@/app/components/ui/progress";
import { getAdminAnalyticsSummary, getAdminCareerAnalysisRuns } from "../api/adminAnalyticsApi";
import type { AdminAnalyticsSummary, AdminCareerAnalysisRun } from "../types/adminAnalytics";

/**
 * 분석 통계 전용 화면(C 담당). `/admin` 랜딩이 요약 카드를 보여준다면, 이 화면은
 * 사용자별 장기 경향·대시보드 요약 AI 실행 이력의 입력/결과 스냅샷까지 펼쳐 운영자가
 * "어떤 입력으로 어떤 결과가 나왔는지"를 직접 확인하도록 한다.
 */

const analysisTypeLabel: Record<string, string> = {
  CAREER_TREND: "장기 취업 경향",
  DASHBOARD_SUMMARY: "대시보드 요약",
};

const statusTone: Record<string, string> = {
  SUCCESS: "bg-green-100 text-green-700",
  FALLBACK: "bg-amber-100 text-amber-700",
  FAILED: "bg-red-100 text-red-700",
};

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
}

function prettyJson(raw: string | null): string {
  if (!raw) return "(없음)";
  try {
    return JSON.stringify(JSON.parse(raw), null, 2);
  } catch {
    return raw;
  }
}

export function AdminAnalyticsPage() {
  const [summary, setSummary] = useState<AdminAnalyticsSummary | null>(null);
  const [runs, setRuns] = useState<AdminCareerAnalysisRun[]>([]);
  const [query, setQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [typeFilter, setTypeFilter] = useState("ALL");
  const [expandedId, setExpandedId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const [summaryData, runData] = await Promise.all([
        getAdminAnalyticsSummary(),
        getAdminCareerAnalysisRuns(),
      ]);
      setSummary(summaryData);
      setRuns(runData);
    } catch (err) {
      setError(err instanceof Error ? err.message : "분석 통계를 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  const statCards = useMemo(() => {
    if (!summary) return [];
    const { stats } = summary;
    return [
      { label: "전체 회원", value: stats.totalUsers, sub: `활성 ${stats.activeUsers.toLocaleString()}명` },
      { label: "분석 완료 지원 건", value: stats.analyzedApplications, sub: `전체 ${stats.totalApplications.toLocaleString()}건 중` },
      { label: "평균 적합도", value: stats.averageFitScore, sub: "AI 적합도 분석 기준" },
      { label: "이번 달 AI 크레딧", value: stats.creditsUsedThisMonth, sub: "ai_usage_log 차감 기준" },
    ];
  }, [summary]);

  const visibleRuns = useMemo(() => {
    const keyword = query.trim().toLowerCase();
    return runs.filter((run) => {
      const matchesQuery =
        !keyword || `${run.userName} ${run.userEmail} ${run.userId}`.toLowerCase().includes(keyword);
      const matchesStatus = statusFilter === "ALL" || run.status === statusFilter;
      const matchesType = typeFilter === "ALL" || run.analysisType === typeFilter;
      return matchesQuery && matchesStatus && matchesType;
    });
  }, [runs, query, statusFilter, typeFilter]);

  const runStatusCounts = useMemo(() => {
    return runs.reduce(
      (acc, run) => {
        acc.total += 1;
        if (run.status === "SUCCESS") acc.success += 1;
        else if (run.status === "FALLBACK") acc.fallback += 1;
        else acc.failed += 1;
        return acc;
      },
      { total: 0, success: 0, fallback: 0, failed: 0 },
    );
  }, [runs]);

  return (
    <div className="min-h-screen bg-slate-50">
      <div className="mx-auto w-full max-w-[1400px] space-y-6 px-4 py-8 sm:px-6">
        <section className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <Badge className="mb-2 bg-slate-900 text-white">C 관리자</Badge>
            <h1 className="flex items-center gap-2 text-2xl font-black text-slate-900">
              <BarChart3 className="size-6 text-indigo-600" />
              분석 통계
            </h1>
            <p className="mt-1 text-sm text-slate-500">
              적합도·부족 역량 통계와 장기 경향·대시보드 요약 AI 실행 이력을 입력/결과 스냅샷까지 확인합니다.
            </p>
          </div>
          <Button variant="outline" onClick={() => void load()} disabled={loading}>
            <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />
            새로고침
          </Button>
        </section>

        {loading && (
          <Card className="border border-slate-200 bg-white">
            <CardContent className="flex items-center gap-3 p-5 text-sm text-slate-600">
              <Loader2 className="size-4 animate-spin text-indigo-600" />
              분석 통계를 불러오는 중입니다.
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
              {statCards.map((card) => (
                <Card key={card.label} className="border border-slate-200 bg-white">
                  <CardContent className="p-5">
                    <div className="text-sm text-slate-500">{card.label}</div>
                    <div className="mt-1 text-3xl font-black text-slate-900">{card.value.toLocaleString()}</div>
                    <div className="mt-1 text-xs text-slate-400">{card.sub}</div>
                  </CardContent>
                </Card>
              ))}
            </section>

            <section className="grid gap-6 lg:grid-cols-2">
              <Card className="border border-slate-200 bg-white">
                <CardHeader className="pb-3">
                  <CardTitle className="flex items-center gap-2 text-base">
                    <Gauge className="size-4 text-purple-600" />
                    적합도 점수 분포
                  </CardTitle>
                </CardHeader>
                <CardContent className="space-y-4">
                  {summary.fitScoreBands.length > 0 ? (
                    summary.fitScoreBands.map((band) => (
                      <div key={band.label} className="space-y-1.5">
                        <div className="flex items-center justify-between gap-3 text-sm">
                          <span className="font-semibold text-slate-700">{band.label}</span>
                          <span className="text-xs text-slate-400">{band.count}건 · {band.percentage}%</span>
                        </div>
                        <Progress value={band.percentage} className="h-2" />
                      </div>
                    ))
                  ) : (
                    <div className="text-sm text-slate-500">적합도 분석 데이터가 아직 없습니다.</div>
                  )}
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
                    summary.skillGaps.slice(0, 8).map((gap) => (
                      <div key={gap.skill} className="rounded-lg bg-white/70 px-3 py-2">
                        <div className="flex items-center justify-between gap-3 text-sm">
                          <span className="font-semibold text-amber-900">{gap.skill}</span>
                          <span className="text-xs text-amber-700">{gap.count}/{gap.total}건 · {gap.percentage}%</span>
                        </div>
                        <Progress value={gap.percentage} className="mt-2 h-1.5" />
                      </div>
                    ))
                  ) : (
                    <div className="text-sm text-amber-800">반복 부족 역량 데이터가 없습니다.</div>
                  )}
                </CardContent>
              </Card>
            </section>

            <section>
              <Card className="border border-slate-200 bg-white">
                <CardHeader className="pb-3">
                  <CardTitle className="flex flex-col gap-3 text-base sm:flex-row sm:items-center sm:justify-between">
                    <span className="flex items-center gap-2">
                      <Brain className="size-4 text-indigo-600" />
                      장기 경향·대시보드 AI 실행 이력
                      <Badge className="bg-slate-100 text-slate-600">
                        성공 {runStatusCounts.success} · Fallback {runStatusCounts.fallback} · 실패 {runStatusCounts.failed}
                      </Badge>
                    </span>
                    <span className="flex flex-col gap-2 sm:flex-row">
                      <label className="flex items-center gap-2 rounded-md border border-slate-200 bg-white px-3">
                        <Search className="size-4 text-slate-400" />
                        <input
                          value={query}
                          onChange={(event) => setQuery(event.target.value)}
                          placeholder="사용자/이메일/ID 검색"
                          className="h-9 w-48 bg-transparent text-sm outline-none"
                        />
                      </label>
                      <select
                        value={typeFilter}
                        onChange={(event) => setTypeFilter(event.target.value)}
                        className="h-9 rounded-md border border-slate-200 px-3 text-sm"
                      >
                        <option value="ALL">전체 유형</option>
                        <option value="CAREER_TREND">장기 취업 경향</option>
                        <option value="DASHBOARD_SUMMARY">대시보드 요약</option>
                      </select>
                      <select
                        value={statusFilter}
                        onChange={(event) => setStatusFilter(event.target.value)}
                        className="h-9 rounded-md border border-slate-200 px-3 text-sm"
                      >
                        <option value="ALL">전체 상태</option>
                        <option value="SUCCESS">성공</option>
                        <option value="FALLBACK">Fallback</option>
                        <option value="FAILED">실패</option>
                      </select>
                    </span>
                  </CardTitle>
                </CardHeader>
                <CardContent className="space-y-2">
                  {visibleRuns.length > 0 ? (
                    visibleRuns.slice(0, 50).map((run) => {
                      const expanded = expandedId === run.id;
                      return (
                        <div key={run.id} className="rounded-lg border border-slate-100">
                          <button
                            type="button"
                            onClick={() => setExpandedId(expanded ? null : run.id)}
                            className="flex w-full items-start justify-between gap-3 p-3 text-left transition-colors hover:bg-slate-50"
                          >
                            <div className="min-w-0">
                              <div className="flex flex-wrap items-center gap-2 text-sm font-semibold text-slate-800">
                                {expanded ? <ChevronDown className="size-4 text-slate-400" /> : <ChevronRight className="size-4 text-slate-400" />}
                                {run.userName}
                                <span className="text-xs font-normal text-slate-400">({run.userEmail})</span>
                                <Badge className="bg-indigo-100 text-indigo-700">{analysisTypeLabel[run.analysisType] ?? run.analysisType}</Badge>
                              </div>
                              <div className="mt-1 pl-6 text-xs text-slate-500">
                                {run.model || "모델 없음"} · {run.tokenUsage.toLocaleString()} 토큰 · {formatDateTime(run.createdAt)}
                              </div>
                              {run.errorMessage && <div className="mt-1 pl-6 text-xs text-red-600">{run.errorMessage}</div>}
                            </div>
                            <Badge className={statusTone[run.status] ?? "bg-slate-100 text-slate-600"}>
                              {run.status}{run.retryable ? " · 재시도 가능" : ""}
                            </Badge>
                          </button>
                          {expanded && (
                            <div className="grid gap-3 border-t border-slate-100 p-3 lg:grid-cols-2">
                              <div>
                                <div className="mb-1 flex items-center gap-1.5 text-xs font-bold text-slate-500">
                                  <Users className="size-3.5 text-blue-600" />
                                  입력 스냅샷
                                </div>
                                <pre className="max-h-72 overflow-auto whitespace-pre-wrap rounded-lg border border-slate-200 bg-slate-50 p-3 text-[11px] leading-5 text-slate-700">
                                  {prettyJson(run.inputSnapshot)}
                                </pre>
                              </div>
                              <div>
                                <div className="mb-1 flex items-center gap-1.5 text-xs font-bold text-slate-500">
                                  <Brain className="size-3.5 text-indigo-600" />
                                  AI 결과
                                </div>
                                <pre className="max-h-72 overflow-auto whitespace-pre-wrap rounded-lg border border-slate-200 bg-slate-950 p-3 text-[11px] leading-5 text-slate-100">
                                  {prettyJson(run.result)}
                                </pre>
                              </div>
                            </div>
                          )}
                        </div>
                      );
                    })
                  ) : (
                    <div className="rounded-lg bg-slate-50 p-4 text-sm text-slate-500">조건에 맞는 실행 이력이 없습니다.</div>
                  )}
                </CardContent>
              </Card>
            </section>
          </>
        )}
      </div>
    </div>
  );
}

export default AdminAnalyticsPage;
