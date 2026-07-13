import { useCallback, useEffect, useMemo, useState } from "react";
import { Link } from "react-router";
import {
  AlertTriangle,
  BarChart3,
  Brain,
  ChevronDown,
  ChevronRight,
  Cpu,
  CheckCircle2,
  Gauge,
  Loader2,
  MessageSquarePlus,
  Pencil,
  RefreshCw,
  Search,
  StickyNote,
  Trash2,
  Users,
  X,
} from "lucide-react";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Progress } from "@/app/components/ui/progress";
import AdminShell from "../../../components/AdminShell";
import {
  createAdminCareerRunMemo,
  deleteAdminCareerRunMemo,
  getAdminAnalysisFailures,
  getAdminAnalyticsSummary,
  getAdminCareerAnalysisRuns,
  getAdminCareerRunMemos,
  getAdminQualityFlags,
  getAdminUserTimeline,
  resolveAdminQualityFlag,
  updateAdminCareerRunMemo,
} from "../api/adminAnalyticsApi";
import type {
  AdminAnalysisFailure,
  AdminAnalyticsSummary,
  AdminCareerAnalysisRun,
  AdminCareerRunMemo,
  AdminQualityFlag,
  AdminUserTimeline,
} from "../types/adminAnalytics";
import { useAdminDomainAuthorization } from "../../../auth/useAdminAuthorization";

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

const memoTypeOptions = [
  { value: "GENERAL", label: "일반" },
  { value: "QUALITY", label: "분석 품질" },
  { value: "USER_INQUIRY", label: "사용자 문의" },
  { value: "REANALYSIS", label: "재분석 필요" },
  { value: "PROMPT_ISSUE", label: "프롬프트 이슈" },
  { value: "DATA_ISSUE", label: "데이터 이슈" },
  { value: "SCORE_DISPUTE", label: "점수 이의" },
  { value: "CERT_RECOMMENDATION_ISSUE", label: "자격증 추천 이슈" },
] as const;

const memoTypeLabel: Record<string, string> = Object.fromEntries(
  memoTypeOptions.map((option) => [option.value, option.label]),
);

const memoTypeTone: Record<string, string> = {
  GENERAL: "bg-slate-100 text-slate-600",
  QUALITY: "bg-purple-100 text-purple-700",
  USER_INQUIRY: "bg-blue-100 text-blue-700",
  REANALYSIS: "bg-amber-100 text-amber-700",
  PROMPT_ISSUE: "bg-rose-100 text-rose-700",
  DATA_ISSUE: "bg-orange-100 text-orange-700",
  SCORE_DISPUTE: "bg-cyan-100 text-cyan-700",
  CERT_RECOMMENDATION_ISSUE: "bg-lime-100 text-lime-700",
};

/**
 * 실행 이력 단위 운영 메모(분석 결과 운영 메모). 적합도 운영 메모와 동일 운영 흐름으로,
 * 과도한 추천·잘못된 분석·사용자 문의 대응 내용을 장기 경향/대시보드 요약 실행 이력에 남긴다.
 * 메모 개수가 바뀌면 상위 목록의 배지를 갱신하도록 onCountChange로 알린다.
 */
function RunMemoPanel({
  runId,
  onCountChange,
  canCreate,
  canUpdate,
  canDelete,
}: {
  runId: number;
  onCountChange: (runId: number, count: number) => void;
  canCreate: boolean;
  canUpdate: boolean;
  canDelete: boolean;
}) {
  const [memos, setMemos] = useState<AdminCareerRunMemo[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [memoType, setMemoType] = useState<string>("GENERAL");
  const [content, setContent] = useState("");
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editType, setEditType] = useState<string>("GENERAL");
  const [editContent, setEditContent] = useState("");

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(null);
    getAdminCareerRunMemos(runId)
      .then((data) => {
        if (!active) return;
        setMemos(data);
        onCountChange(runId, data.length);
      })
      .catch((err) => {
        if (active) setError(err instanceof Error ? err.message : "메모를 불러오지 못했습니다.");
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
    // onCountChange는 부모에서 useCallback으로 안정화됨.
  }, [runId, onCountChange]);

  const submit = async () => {
    if (!canCreate || !content.trim() || saving) return;
    setSaving(true);
    setError(null);
    try {
      const created = await createAdminCareerRunMemo(runId, { memoType, content: content.trim() });
      const next = [created, ...memos];
      setMemos(next);
      onCountChange(runId, next.length);
      setContent("");
      setMemoType("GENERAL");
    } catch (err) {
      setError(err instanceof Error ? err.message : "메모를 저장하지 못했습니다.");
    } finally {
      setSaving(false);
    }
  };

  const startEdit = (memo: AdminCareerRunMemo) => {
    if (!canUpdate) return;
    setEditingId(memo.id);
    setEditType(memo.memoType);
    setEditContent(memo.content);
  };

  const saveEdit = async (memoId: number) => {
    if (!canUpdate || !editContent.trim() || saving) return;
    setSaving(true);
    setError(null);
    try {
      const updated = await updateAdminCareerRunMemo(runId, memoId, {
        memoType: editType,
        content: editContent.trim(),
      });
      setMemos((current) => current.map((memo) => (memo.id === memoId ? updated : memo)));
      setEditingId(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : "메모를 수정하지 못했습니다.");
    } finally {
      setSaving(false);
    }
  };

  const remove = async (memoId: number) => {
    if (!canDelete || saving) return;
    setSaving(true);
    setError(null);
    try {
      await deleteAdminCareerRunMemo(runId, memoId);
      const next = memos.filter((memo) => memo.id !== memoId);
      setMemos(next);
      onCountChange(runId, next.length);
    } catch (err) {
      setError(err instanceof Error ? err.message : "메모를 삭제하지 못했습니다.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="border-t border-slate-100 p-3">
      <div className="mb-2 flex items-center gap-1.5 text-xs font-bold text-slate-500">
        <StickyNote className="size-3.5 text-emerald-600" />
        운영 메모 {memos.length > 0 && <span className="text-emerald-700">({memos.length})</span>}
      </div>

      {error && <div className="mb-2 text-xs text-red-600">{error}</div>}

      {/* 메모 작성 */}
      {canCreate && <div className="mb-3 flex flex-col gap-2 rounded-lg border border-slate-200 bg-slate-50 p-2 sm:flex-row sm:items-start">
        <select
          value={memoType}
          onChange={(event) => setMemoType(event.target.value)}
          className="h-9 rounded-md border border-slate-200 px-2 text-sm"
        >
          {memoTypeOptions.map((option) => (
            <option key={option.value} value={option.value}>{option.label}</option>
          ))}
        </select>
        <textarea
          value={content}
          onChange={(event) => setContent(event.target.value)}
          placeholder="과도한 추천/잘못된 분석/사용자 문의 대응 등 운영 메모를 남깁니다."
          rows={2}
          className="min-h-9 flex-1 resize-y rounded-md border border-slate-200 px-3 py-2 text-sm outline-none focus:border-indigo-400"
        />
        <Button onClick={() => void submit()} disabled={saving || !content.trim()} className="shrink-0">
          {saving ? <Loader2 className="size-4 animate-spin" /> : <MessageSquarePlus className="size-4" />}
          추가
        </Button>
      </div>}

      {/* 메모 목록 */}
      {loading ? (
        <div className="flex items-center gap-2 text-xs text-slate-500">
          <Loader2 className="size-3.5 animate-spin" /> 메모를 불러오는 중...
        </div>
      ) : memos.length === 0 ? (
        <div className="text-xs text-slate-400">아직 등록된 운영 메모가 없습니다.</div>
      ) : (
        <ul className="space-y-2">
          {memos.map((memo) => (
            <li key={memo.id} className="rounded-lg border border-slate-100 bg-card p-2.5">
              {editingId === memo.id && canUpdate ? (
                <div className="flex flex-col gap-2">
                  <div className="flex items-center gap-2">
                    <select
                      value={editType}
                      onChange={(event) => setEditType(event.target.value)}
                      className="h-8 rounded-md border border-slate-200 px-2 text-xs"
                    >
                      {memoTypeOptions.map((option) => (
                        <option key={option.value} value={option.value}>{option.label}</option>
                      ))}
                    </select>
                  </div>
                  <textarea
                    value={editContent}
                    onChange={(event) => setEditContent(event.target.value)}
                    rows={2}
                    className="resize-y rounded-md border border-slate-200 px-3 py-2 text-sm outline-none focus:border-indigo-400"
                  />
                  <div className="flex gap-2">
                    <Button onClick={() => void saveEdit(memo.id)} disabled={saving || !editContent.trim()} className="h-8">
                      저장
                    </Button>
                    <Button variant="outline" onClick={() => setEditingId(null)} disabled={saving} className="h-8">
                      <X className="size-3.5" /> 취소
                    </Button>
                  </div>
                </div>
              ) : (
                <>
                  <div className="flex items-start justify-between gap-2">
                    <div className="flex flex-wrap items-center gap-2">
                      <Badge className={memoTypeTone[memo.memoType] ?? "bg-slate-100 text-slate-600"}>
                        {memoTypeLabel[memo.memoType] ?? memo.memoType}
                      </Badge>
                      <span className="text-xs text-slate-400">
                        {memo.adminName} · {formatDateTime(memo.updatedAt)}
                      </span>
                    </div>
                    {(canUpdate || canDelete) && <div className="flex shrink-0 gap-1">
                      {canUpdate && <button
                        type="button"
                        onClick={() => startEdit(memo)}
                        className="rounded p-1 text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-700"
                        title="수정"
                      >
                        <Pencil className="size-3.5" />
                      </button>}
                      {canDelete && <button
                        type="button"
                        onClick={() => void remove(memo.id)}
                        className="rounded p-1 text-slate-400 transition-colors hover:bg-red-50 hover:text-red-600"
                        title="삭제"
                      >
                        <Trash2 className="size-3.5" />
                      </button>}
                    </div>}
                  </div>
                  <p className="mt-1.5 whitespace-pre-wrap text-sm text-slate-700">{memo.content}</p>
                </>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

const failureSourceLabel: Record<string, string> = {
  FIT_ANALYSIS: "적합도 분석",
  CAREER_TREND: "장기 취업 경향",
  DASHBOARD_SUMMARY: "대시보드 요약",
};

const flagTypeLabel: Record<string, string> = {
  SCORE_GAP_MISMATCH: "점수-근거 상충",
  LOW_SCORE_NO_GAPS: "낮은 점수·근거 없음",
  EXCESSIVE_CERTS: "자격증 과다 추천",
  EMPTY_STRATEGY: "전략 누락",
  LOW_CONFIDENCE: "분석 신뢰도 낮음",
  REQUIRED_GAP_APPLY: "필수 미충족·지원 가능 모순",
  EMPTY_CONDITION_MATRIX: "비교 매트릭스 누락",
  DEGRADED_RESULT: "강등 결과 노출",
};

const severityTone: Record<string, string> = {
  HIGH: "bg-red-100 text-red-700",
  MEDIUM: "bg-amber-100 text-amber-700",
  LOW: "bg-slate-100 text-slate-600",
};

export function AdminAnalyticsPage() {
  const { canCreate, canUpdate, canDelete } = useAdminDomainAuthorization("AI");
  const [summary, setSummary] = useState<AdminAnalyticsSummary | null>(null);
  const [runs, setRuns] = useState<AdminCareerAnalysisRun[]>([]);
  const [failures, setFailures] = useState<AdminAnalysisFailure[]>([]);
  const [qualityFlags, setQualityFlags] = useState<AdminQualityFlag[]>([]);
  const [query, setQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [typeFilter, setTypeFilter] = useState("ALL");
  const [expandedId, setExpandedId] = useState<number | null>(null);
  const [timelineUserId, setTimelineUserId] = useState<number | null>(null);
  const [timeline, setTimeline] = useState<AdminUserTimeline[]>([]);
  const [timelineLoading, setTimelineLoading] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const [summaryData, runData, failureData, flagData] = await Promise.all([
        getAdminAnalyticsSummary(),
        getAdminCareerAnalysisRuns(),
        getAdminAnalysisFailures(),
        getAdminQualityFlags(),
      ]);
      setSummary(summaryData);
      setRuns(runData);
      setFailures(failureData);
      setQualityFlags(flagData);
    } catch (err) {
      setError(err instanceof Error ? err.message : "분석 통계를 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  // 메모 패널이 개수를 알리면 목록 배지를 즉시 반영(전체 재조회 없이).
  const handleMemoCountChange = useCallback((runId: number, count: number) => {
    setRuns((current) => current.map((run) => (run.id === runId ? { ...run, memoCount: count } : run)));
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

  const modelStats = useMemo(() => {
    const grouped = new Map<string, { analysisType: string; promptVersion: string; model: string; total: number; success: number; fallback: number; failed: number; tokens: number }>();
    runs.forEach((run) => {
      const model = run.model?.trim() || "미지정 모델";
      const promptVersion = run.promptVersion?.trim() || "버전 미기록";
      const key = `${run.analysisType}:${promptVersion}:${model}`;
      const current = grouped.get(key) ?? { analysisType: run.analysisType, promptVersion, model, total: 0, success: 0, fallback: 0, failed: 0, tokens: 0 };
      current.total += 1;
      current.tokens += run.tokenUsage;
      if (run.status === "SUCCESS") current.success += 1;
      else if (run.status === "FALLBACK") current.fallback += 1;
      else current.failed += 1;
      grouped.set(key, current);
    });

    return Array.from(grouped, ([key, stat]) => ({
      key,
      ...stat,
      successRate: Math.round((stat.success * 100) / stat.total),
      averageTokens: Math.round(stat.tokens / stat.total),
    })).sort((a, b) => b.total - a.total || b.successRate - a.successRate || a.model.localeCompare(b.model));
  }, [runs]);

  const resolveFlag = async (flag: AdminQualityFlag) => {
    if (!canUpdate) return;
    await resolveAdminQualityFlag(flag.fitAnalysisId, flag.flagType);
    setQualityFlags((current) => current.filter((item) => !(item.fitAnalysisId === flag.fitAnalysisId && item.flagType === flag.flagType)));
  };

  const loadTimeline = async (userId: number) => {
    setTimelineUserId(userId);
    setTimelineLoading(true);
    try {
      setTimeline(await getAdminUserTimeline(userId));
    } finally {
      setTimelineLoading(false);
    }
  };

  return (
    <AdminShell
      active="analytics"
      breadcrumb="분석 통계"
      title="분석 통계"
      icon={BarChart3}
      desc="적합도·부족 역량 통계와 장기 경향·대시보드 요약 AI 실행 이력을 입력/결과 스냅샷까지 확인합니다."
      actions={(
        <Button variant="outline" onClick={() => void load()} disabled={loading}>
          <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />
          새로고침
        </Button>
      )}
    >
      <div className="space-y-6">
        {loading && (
          <Card className="border border-slate-200 bg-card">
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
                <Card key={card.label} className="border border-slate-200 bg-card">
                  <CardContent className="p-5">
                    <div className="text-sm text-slate-500">{card.label}</div>
                    <div className="mt-1 text-3xl font-black text-slate-900">{card.value.toLocaleString()}</div>
                    <div className="mt-1 text-xs text-slate-400">{card.sub}</div>
                  </CardContent>
                </Card>
              ))}
            </section>

            <section className="grid gap-6 lg:grid-cols-2">
              <Card className="border border-slate-200 bg-card">
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
                      <div key={gap.skill} className="rounded-lg bg-card/70 px-3 py-2">
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

            {/* 분석 실패 큐 + 품질 검수 큐: 운영자가 우선 처리할 항목을 모아 보여준다. */}
            <section className="grid gap-6 lg:grid-cols-2">
              <Card className="border border-red-200 bg-card">
                <CardHeader className="pb-3">
                  <CardTitle className="flex items-center gap-2 text-base text-red-800">
                    <AlertTriangle className="size-4 text-red-600" />
                    분석 실패 큐
                    <Badge className="bg-red-100 text-red-700">{failures.length}건</Badge>
                  </CardTitle>
                </CardHeader>
                <CardContent className="max-h-96 space-y-2 overflow-y-auto">
                  {failures.length > 0 ? (
                    failures.map((failure) => (
                      <div key={`${failure.source}-${failure.refId}`} className="rounded-lg border border-slate-100 p-3">
                        <div className="flex flex-wrap items-center justify-between gap-2">
                          <div className="flex flex-wrap items-center gap-2 text-sm font-semibold text-slate-800">
                            {failure.userName}
                            <span className="text-xs font-normal text-slate-400">({failure.userEmail})</span>
                            <Badge className="bg-slate-100 text-slate-600">
                              {failureSourceLabel[failure.source] ?? failure.source}
                            </Badge>
                          </div>
                          <Badge className={statusTone[failure.status] ?? "bg-slate-100 text-slate-600"}>
                            {failure.status}{failure.retryable ? " · 재시도 가능" : ""}
                          </Badge>
                        </div>
                        {(failure.companyName || failure.jobTitle) && (
                          <div className="mt-1 text-xs text-slate-500">
                            {failure.companyName} {failure.jobTitle && `· ${failure.jobTitle}`}
                          </div>
                        )}
                        <div className="mt-1 text-xs text-red-600">{failure.errorMessage || "오류 메시지 없음"}</div>
                        <div className="mt-1 text-[11px] text-slate-400">
                          {failure.model || "모델 없음"} · {formatDateTime(failure.createdAt)}
                        </div>
                      </div>
                    ))
                  ) : (
                    <div className="rounded-lg bg-slate-50 p-4 text-sm text-slate-500">
                      비정상(FAILED/FALLBACK) 분석 결과가 없습니다.
                    </div>
                  )}
                </CardContent>
              </Card>

              <Card className="border border-purple-200 bg-card">
                <CardHeader className="pb-3">
                  <CardTitle className="flex items-center gap-2 text-base text-purple-900">
                    <Gauge className="size-4 text-purple-600" />
                    품질 검수 큐
                    <Badge className="bg-purple-100 text-purple-700">{qualityFlags.length}건</Badge>
                  </CardTitle>
                </CardHeader>
                <CardContent className="max-h-96 space-y-2 overflow-y-auto">
                  {qualityFlags.length > 0 ? (
                    qualityFlags.map((flag) => (
                      <div key={`${flag.fitAnalysisId}-${flag.flagType}`} className="rounded-lg border border-slate-100 p-3">
                        <div className="flex flex-wrap items-center justify-between gap-2">
                          <div className="flex flex-wrap items-center gap-2 text-sm font-semibold text-slate-800">
                            {flag.userName}
                            <span className="text-xs font-normal text-slate-400">({flag.userEmail})</span>
                            <Badge className="bg-purple-100 text-purple-700">
                              {flagTypeLabel[flag.flagType] ?? flag.flagType}
                            </Badge>
                          </div>
                          <Badge className={severityTone[flag.severity] ?? "bg-slate-100 text-slate-600"}>
                            {flag.severity === "HIGH" ? "심각" : flag.severity === "MEDIUM" ? "주의" : "낮음"}
                          </Badge>
                        </div>
                        <div className="mt-1 text-xs text-slate-500">
                          {flag.companyName} · {flag.jobTitle} · 적합도 {flag.fitScore ?? 0}점
                        </div>
                        <div className="mt-1 text-xs leading-5 text-slate-600">{flag.detail}</div>
                        <div className="mt-1 text-[11px] text-slate-400">
                          분석 #{flag.fitAnalysisId} · {formatDateTime(flag.analyzedAt)}
                        </div>
                        <Link
                          to={`/admin/fit-analysis?analysisId=${flag.fitAnalysisId}`}
                          className="mt-2 inline-flex text-xs font-semibold text-purple-700 hover:text-purple-900"
                        >
                          적합도 분석 검수 화면 열기
                        </Link>
                        {canUpdate && <button type="button" onClick={() => void resolveFlag(flag)} className="ml-3 inline-flex items-center gap-1 text-xs font-semibold text-green-700 hover:text-green-900">
                          <CheckCircle2 className="size-3.5" />검수 완료
                        </button>}
                      </div>
                    ))
                  ) : (
                    <div className="rounded-lg bg-slate-50 p-4 text-sm text-slate-500">
                      품질 점검이 필요한 분석 결과가 없습니다.
                    </div>
                  )}
                </CardContent>
              </Card>
            </section>

            <section>
              <Card className="border border-slate-200 bg-card">
                <CardHeader className="pb-3">
                  <CardTitle className="flex items-center gap-2 text-base">
                    <Cpu className="size-4 text-cyan-600" />
                    모델별 분석 운영 신뢰도
                    <Badge className="bg-cyan-100 text-cyan-700">{modelStats.length}개 모델</Badge>
                  </CardTitle>
                </CardHeader>
                <CardContent>
                  {modelStats.length > 0 ? (
                    <div className="grid gap-3 lg:grid-cols-2">
                      {modelStats.map((stat) => (
                        <div key={stat.key} className="rounded-lg border border-slate-100 bg-slate-50 p-3">
                          <div className="flex flex-wrap items-center justify-between gap-2">
                            <span className="text-sm font-bold text-slate-800">{analysisTypeLabel[stat.analysisType] ?? stat.analysisType} · {stat.promptVersion} · {stat.model}</span>
                            <Badge className={stat.successRate >= 90 ? "bg-green-100 text-green-700" : stat.successRate >= 70 ? "bg-amber-100 text-amber-700" : "bg-red-100 text-red-700"}>
                              성공률 {stat.successRate}%
                            </Badge>
                          </div>
                          <Progress value={stat.successRate} className="mt-2 h-2" />
                          <div className="mt-2 flex flex-wrap gap-x-3 gap-y-1 text-xs text-slate-500">
                            <span>총 {stat.total}건</span>
                            <span className="text-green-700">성공 {stat.success}</span>
                            <span className="text-amber-700">Fallback {stat.fallback}</span>
                            <span className="text-red-700">실패 {stat.failed}</span>
                            <span>평균 {stat.averageTokens.toLocaleString()} 토큰</span>
                          </div>
                        </div>
                      ))}
                    </div>
                  ) : (
                    <div className="rounded-lg bg-slate-50 p-4 text-sm text-slate-500">
                      모델별 비교에 사용할 분석 실행 이력이 없습니다.
                    </div>
                  )}
                </CardContent>
              </Card>
            </section>

            <section>
              <Card className="border border-indigo-200 bg-card">
                <CardHeader className="pb-3">
                  <CardTitle className="flex items-center gap-2 text-base">
                    <Brain className="size-4 text-indigo-600" />
                    프롬프트 버전별 성능
                    <Badge className="bg-indigo-100 text-indigo-700">{summary.promptPerformance.length}개 버전</Badge>
                  </CardTitle>
                </CardHeader>
                <CardContent>
                  {summary.promptPerformance.length > 0 ? (
                    <div className="grid gap-3 lg:grid-cols-2">
                      {summary.promptPerformance.map((prompt) => (
                        <div key={`${prompt.promptKey}-${prompt.promptVersion}`} className="rounded-lg border border-slate-100 p-3">
                          <div className="flex items-center justify-between gap-2">
                            <strong className="text-sm text-slate-800">{prompt.promptKey} · {prompt.promptVersion}</strong>
                            <Badge className={prompt.successRate >= 90 ? "bg-green-100 text-green-700" : prompt.successRate >= 70 ? "bg-amber-100 text-amber-700" : "bg-red-100 text-red-700"}>
                              성공률 {prompt.successRate}%
                            </Badge>
                          </div>
                          <Progress value={prompt.successRate} className="mt-2 h-2" />
                          <div className="mt-2 flex flex-wrap gap-x-3 gap-y-1 text-xs text-slate-500">
                            <span>총 {prompt.totalCount}건</span>
                            <span className="text-green-700">성공 {prompt.successCount}</span>
                            <span className="text-amber-700">Fallback {prompt.fallbackCount}</span>
                            <span className="text-red-700">실패 {prompt.failedCount}</span>
                            <span>평균 {prompt.averageTokenUsage.toLocaleString()} 토큰</span>
                          </div>
                        </div>
                      ))}
                    </div>
                  ) : (
                    <div className="rounded-lg bg-slate-50 p-4 text-sm text-slate-500">프롬프트 버전별 비교 데이터가 없습니다.</div>
                  )}
                </CardContent>
              </Card>
            </section>

            <section>
              <Card className="border border-slate-200 bg-card">
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
                      <label className="flex items-center gap-2 rounded-md border border-slate-200 bg-card px-3">
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
                                {run.memoCount > 0 && (
                                  <Badge className="bg-emerald-100 text-emerald-700">
                                    <StickyNote className="mr-1 size-3" />메모 {run.memoCount}
                                  </Badge>
                                )}
                              </div>
                              <div className="mt-1 pl-6 text-xs text-slate-500">
                                {run.promptVersion || "버전 미기록"} · {run.model || "모델 없음"} · {run.tokenUsage.toLocaleString()} 토큰 · {formatDateTime(run.createdAt)}
                              </div>
                              <button type="button" onClick={(event) => { event.stopPropagation(); void loadTimeline(run.userId); }} className="mt-1 pl-6 text-xs font-semibold text-blue-600 hover:text-blue-800">사용자 전체 분석 타임라인 보기</button>
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
                                <pre className="max-h-72 overflow-auto whitespace-pre-wrap rounded-lg border border-slate-200 bg-[#0b0c0e] p-3 text-[11px] leading-5 text-[#e6e6e6]">
                                  {prettyJson(run.result)}
                                </pre>
                              </div>
                            </div>
                          )}
                          {expanded && (
                            <RunMemoPanel
                              runId={run.id}
                              onCountChange={handleMemoCountChange}
                              canCreate={canCreate}
                              canUpdate={canUpdate}
                              canDelete={canDelete}
                            />
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

            {timelineUserId != null && (
              <section>
                <Card className="border border-blue-200 bg-card">
                  <CardHeader><CardTitle className="text-base">사용자 #{timelineUserId} 분석 타임라인</CardTitle></CardHeader>
                  <CardContent className="space-y-2">
                    {timelineLoading ? <Loader2 className="size-4 animate-spin text-blue-600" /> : timeline.map((item) => (
                      <div key={`${item.eventType}-${item.refId}`} className="rounded-lg border border-slate-100 p-3 text-sm">
                        <div className="flex justify-between gap-2"><strong>{item.summary}</strong><Badge className={statusTone[item.status] ?? "bg-slate-100 text-slate-600"}>{item.status}</Badge></div>
                        <div className="mt-1 text-xs text-slate-400">{item.eventType} · {item.score == null ? "점수 없음" : `${item.score}점`} · {formatDateTime(item.createdAt)}</div>
                      </div>
                    ))}
                  </CardContent>
                </Card>
              </section>
            )}
          </>
        )}
      </div>
    </AdminShell>
  );
}

export default AdminAnalyticsPage;
