import { useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Progress } from "@/app/components/ui/progress";
import {
  AlertCircle,
  BarChart3,
  Bookmark,
  CheckCircle2,
  ClipboardList,
  Loader2,
  MessageSquareText,
  PenLine,
  Search,
  Trash2,
} from "lucide-react";
import {
  createAdminFitAnalysisMemo,
  patchAdminGateReview,
  deleteAdminFitAnalysisMemo,
  getAdminFitAnalyses,
  getAdminFitAnalysis,
  getAdminGateStats,
  updateAdminFitAnalysisMemo,
} from "../api/adminFitAnalysisApi";
import GateStatsPanel from "../components/GateStatsPanel";
import type {
  AdminFitAnalysisDetail,
  AdminFitAnalysisListItem,
  AdminFitAnalysisMemo,
  AdminGateStats,
} from "../types/adminFitAnalysis";

const memoTypeOptions = [
  { value: "GENERAL", label: "일반" },
  { value: "QUALITY", label: "품질 확인" },
  { value: "USER_INQUIRY", label: "문의 대응" },
  { value: "REANALYSIS", label: "재분석 필요" },
  { value: "PROMPT_ISSUE", label: "프롬프트 이슈" },
  { value: "DATA_ISSUE", label: "데이터 이슈" },
  { value: "SCORE_DISPUTE", label: "점수 이의" },
  { value: "CERT_RECOMMENDATION_ISSUE", label: "자격증 추천 이슈" },
];

const statusLabel: Record<string, string> = {
  DRAFT: "초안",
  ANALYZING: "분석 중",
  READY: "준비 완료",
  APPLIED: "지원 완료",
  CLOSED: "종료",
};

function formatDateTime(value: string | null) {
  if (!value) return "기록 없음";
  return new Intl.DateTimeFormat("ko-KR", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
}

function scoreTone(score: number | null) {
  if ((score ?? 0) >= 70) return "text-green-600";
  if ((score ?? 0) >= 50) return "text-amber-600";
  return "text-red-500";
}

// review-first evidence gate(R3) 상태 뱃지. R3 이전 분석(gateStatus=null)은 뱃지를 그리지 않는다.
function gateBadge(status: string | null): { label: string; cls: string } | null {
  if (status === "REVIEW_REQUIRED") return { label: "검토 필요", cls: "bg-orange-100 text-orange-700" };
  if (status === "REJECTED") return { label: "반려", cls: "bg-red-100 text-red-700" };
  if (status === "PASSED") return { label: "근거 통과", cls: "bg-green-100 text-green-700" };
  return null;
}

export default function AdminFitAnalysisPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const requestedAnalysisId = Number(searchParams.get("analysisId"));
  const [items, setItems] = useState<AdminFitAnalysisListItem[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [detail, setDetail] = useState<AdminFitAnalysisDetail | null>(null);
  const [loadingList, setLoadingList] = useState(true);
  const [loadingDetail, setLoadingDetail] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [memoType, setMemoType] = useState("GENERAL");
  const [memoContent, setMemoContent] = useState("");
  const [editingMemo, setEditingMemo] = useState<AdminFitAnalysisMemo | null>(null);
  const [savingMemo, setSavingMemo] = useState(false);
  const [query, setQuery] = useState("");
  // 점수 구간/분석 상태/메모 보유 필터(클라이언트 필터링).
  const [bandFilter, setBandFilter] = useState("ALL");
  const [resultFilter, setResultFilter] = useState("ALL");
  const [memoOnly, setMemoOnly] = useState(false);
  const [reanalysisOnly, setReanalysisOnly] = useState(false);
  // review-first evidence gate 검토 필요(REVIEW_REQUIRED) 항목만 보기(클라이언트 필터).
  const [reviewOnly, setReviewOnly] = useState(false);
  // gate 통계 요약. 목록과 독립적으로 로딩하고, 실패해도 목록을 막지 않는다.
  const [gateStats, setGateStats] = useState<AdminGateStats | null>(null);
  const [loadingGateStats, setLoadingGateStats] = useState(true);
  const [gateStatsError, setGateStatsError] = useState<string | null>(null);

  useEffect(() => {
    let ignore = false;
    setLoadingList(true);
    setError(null);

    // '검토 필요만'은 서버 1차 필터(reviewRequiredOnly)로 후보군을 줄인다. 나머지 필터는 클라이언트 유지.
    getAdminFitAnalyses(reviewOnly)
      .then((data) => {
        if (ignore) return;
        setItems(data);
        setSelectedId((current) => (current != null && data.some((item) => item.id === current) ? current : data[0]?.id ?? null));
      })
      .catch((requestError) => {
        if (!ignore) setError(requestError instanceof Error ? requestError.message : "적합도 분석 목록을 불러오지 못했습니다.");
      })
      .finally(() => {
        if (!ignore) setLoadingList(false);
      });

    return () => {
      ignore = true;
    };
  }, [reviewOnly]);

  useEffect(() => {
    let ignore = false;
    setLoadingGateStats(true);
    setGateStatsError(null);

    getAdminGateStats()
      .then((data) => {
        if (!ignore) setGateStats(data);
      })
      .catch((requestError) => {
        if (!ignore) setGateStatsError(requestError instanceof Error ? requestError.message : "gate 통계를 불러오지 못했습니다.");
      })
      .finally(() => {
        if (!ignore) setLoadingGateStats(false);
      });

    return () => {
      ignore = true;
    };
  }, []);

  useEffect(() => {
    if (Number.isFinite(requestedAnalysisId) && items.some((item) => item.id === requestedAnalysisId)) {
      setSelectedId(requestedAnalysisId);
    }
  }, [items, requestedAnalysisId]);

  useEffect(() => {
    if (selectedId == null) {
      setDetail(null);
      return;
    }

    let ignore = false;
    setLoadingDetail(true);
    setError(null);

    getAdminFitAnalysis(selectedId)
      .then((data) => {
        if (!ignore) setDetail(data);
      })
      .catch((requestError) => {
        if (!ignore) setError(requestError instanceof Error ? requestError.message : "적합도 분석 상세를 불러오지 못했습니다.");
      })
      .finally(() => {
        if (!ignore) setLoadingDetail(false);
      });

    return () => {
      ignore = true;
    };
  }, [selectedId]);

  const averageScore = useMemo(() => {
    const scored = items.filter((item) => item.fitScore != null);
    if (scored.length === 0) return 0;
    return Math.round(scored.reduce((sum, item) => sum + (item.fitScore ?? 0), 0) / scored.length);
  }, [items]);

  const memoSummary = useMemo(() => {
    return items.reduce((sum, item) => sum + item.memoCount, 0);
  }, [items]);
  const detailGateReasons = detail?.gateReasons ?? [];
  const visibleItems = useMemo(() => {
    const value = query.trim().toLowerCase();
    return items.filter((item) => {
      const matchesQuery =
        !value || `${item.companyName} ${item.jobTitle} ${item.userName} ${item.userEmail}`.toLowerCase().includes(value);
      const score = item.fitScore ?? 0;
      const matchesBand =
        bandFilter === "ALL" ||
        (bandFilter === "HIGH" && score >= 80) ||
        (bandFilter === "MID_HIGH" && score >= 70 && score < 80) ||
        (bandFilter === "MID" && score >= 50 && score < 70) ||
        (bandFilter === "LOW" && score < 50);
      const matchesResult =
        resultFilter === "ALL" ||
        (resultFilter === "SUCCESS" ? item.status === "SUCCESS" : item.status !== "SUCCESS");
      const matchesMemo = !memoOnly || item.memoCount > 0;
      const matchesReanalysis = !reanalysisOnly || item.reanalysisRequested;
      // 검토 필요 필터는 서버(reviewRequiredOnly)에서 처리하므로 여기서는 클라이언트 필터를 두지 않는다.
      return matchesQuery && matchesBand && matchesResult && matchesMemo && matchesReanalysis;
    });
  }, [items, query, bandFilter, resultFilter, memoOnly, reanalysisOnly]);

  function resetMemoForm() {
    setMemoType("GENERAL");
    setMemoContent("");
    setEditingMemo(null);
  }

  function startEditMemo(memo: AdminFitAnalysisMemo) {
    setEditingMemo(memo);
    setMemoType(memo.memoType);
    setMemoContent(memo.content);
  }

  async function submitMemo() {
    if (!detail || !memoContent.trim()) return;

    setSavingMemo(true);
    setError(null);
    try {
      if (editingMemo) {
        await updateAdminFitAnalysisMemo(detail.id, editingMemo.id, { memoType, content: memoContent });
      } else {
        await createAdminFitAnalysisMemo(detail.id, { memoType, content: memoContent });
      }
      const refreshedDetail = await getAdminFitAnalysis(detail.id);
      const refreshedItems = await getAdminFitAnalyses(reviewOnly);
      setDetail(refreshedDetail);
      setItems(refreshedItems);
      resetMemoForm();
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "운영 메모를 저장하지 못했습니다.");
    } finally {
      setSavingMemo(false);
    }
  }

  // gate review workflow: 처리 상태 변경(검토 완료/재분석 요청/대기 되돌리기) 후 상세·목록 갱신.
  async function submitGateReview(reviewStatus: string) {
    if (!detail) return;
    setSavingMemo(true);
    setError(null);
    try {
      const refreshed = await patchAdminGateReview(detail.id, { reviewStatus });
      setDetail(refreshed);
      setItems(await getAdminFitAnalyses(reviewOnly));
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "gate 검토 상태를 변경하지 못했습니다.");
    } finally {
      setSavingMemo(false);
    }
  }

  async function removeMemo(memo: AdminFitAnalysisMemo) {
    if (!detail) return;

    setSavingMemo(true);
    setError(null);
    try {
      await deleteAdminFitAnalysisMemo(detail.id, memo.id);
      const refreshedDetail = await getAdminFitAnalysis(detail.id);
      const refreshedItems = await getAdminFitAnalyses(reviewOnly);
      setDetail(refreshedDetail);
      setItems(refreshedItems);
      if (editingMemo?.id === memo.id) resetMemoForm();
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "운영 메모를 삭제하지 못했습니다.");
    } finally {
      setSavingMemo(false);
    }
  }

  return (
    <div className="min-h-screen bg-slate-50">
      <div className="mx-auto w-full max-w-[1400px] space-y-6 px-4 py-8 sm:px-6">
        <section className="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <Badge className="mb-3 bg-blue-600 text-white">Fit Analysis Ops</Badge>
            <h1 className="flex items-center gap-2 text-2xl font-black text-slate-900">
              <BarChart3 className="size-6 text-blue-600" />
              적합도 분석 관리
            </h1>
            <p className="mt-1 text-sm text-slate-500">
              지원 건별 적합도, 매칭/부족 역량, 추천 학습 결과와 운영 메모를 함께 확인합니다.
            </p>
          </div>
          <div className="grid grid-cols-3 gap-2 sm:min-w-[360px]">
            {[
              { label: "분석 결과", value: `${items.length}건` },
              { label: "평균 점수", value: `${averageScore}점` },
              { label: "운영 메모", value: `${memoSummary}건` },
            ].map((stat) => (
              <Card key={stat.label} className="border border-slate-200 bg-card">
                <CardContent className="p-3">
                  <div className="text-[11px] font-semibold text-slate-400">{stat.label}</div>
                  <div className="mt-1 text-xl font-black text-slate-900">{stat.value}</div>
                </CardContent>
              </Card>
            ))}
          </div>
        </section>

        {error && (
          <Card className="border border-red-200 bg-red-50">
            <CardContent className="flex items-center gap-2 p-4 text-sm text-red-700">
              <AlertCircle className="size-4" />
              {error}
            </CardContent>
          </Card>
        )}

        <GateStatsPanel stats={gateStats} loading={loadingGateStats} error={gateStatsError} />

        <section className="grid gap-6 xl:grid-cols-[420px_minmax(0,1fr)]">
          <Card className="border border-slate-200 bg-card">
            <CardHeader className="pb-3">
              <CardTitle className="flex items-center gap-2 text-base">
                <ClipboardList className="size-4 text-blue-600" />
                분석 결과 목록
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-2">
              <label className="mb-2 flex items-center gap-2 rounded-md border border-slate-200 px-3">
                <Search className="size-4 text-slate-400" />
                <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="사용자·기업·직무 검색" className="h-10 w-full bg-transparent text-sm outline-none" />
              </label>
              <div className="mb-3 flex flex-wrap items-center gap-2">
                <select
                  value={bandFilter}
                  onChange={(event) => setBandFilter(event.target.value)}
                  className="h-9 rounded-md border border-slate-200 px-2 text-xs font-semibold text-slate-600"
                >
                  <option value="ALL">전체 점수</option>
                  <option value="HIGH">80점 이상</option>
                  <option value="MID_HIGH">70-79점</option>
                  <option value="MID">50-69점</option>
                  <option value="LOW">50점 미만</option>
                </select>
                <select
                  value={resultFilter}
                  onChange={(event) => setResultFilter(event.target.value)}
                  className="h-9 rounded-md border border-slate-200 px-2 text-xs font-semibold text-slate-600"
                >
                  <option value="ALL">전체 상태</option>
                  <option value="SUCCESS">성공</option>
                  <option value="ABNORMAL">실패·Fallback</option>
                </select>
                <label className="flex h-9 cursor-pointer items-center gap-1.5 rounded-md border border-slate-200 px-2 text-xs font-semibold text-slate-600">
                  <input type="checkbox" checked={memoOnly} onChange={(event) => setMemoOnly(event.target.checked)} />
                  메모 있는 항목만
                </label>
                <label className="flex h-9 cursor-pointer items-center gap-1.5 rounded-md border border-amber-200 bg-amber-50 px-2 text-xs font-semibold text-amber-700">
                  <input type="checkbox" checked={reanalysisOnly} onChange={(event) => setReanalysisOnly(event.target.checked)} />
                  재분석 필요만
                </label>
                <label className="flex h-9 cursor-pointer items-center gap-1.5 rounded-md border border-orange-200 bg-orange-50 px-2 text-xs font-semibold text-orange-700">
                  <input type="checkbox" checked={reviewOnly} onChange={(event) => setReviewOnly(event.target.checked)} />
                  검토 필요만
                </label>
                <span className="text-[11px] text-slate-400">{visibleItems.length}/{items.length}건</span>
              </div>
              {loadingList ? (
                <div className="flex items-center gap-2 rounded-lg bg-slate-50 p-4 text-sm text-slate-500">
                  <Loader2 className="size-4 animate-spin" />
                  목록을 불러오는 중입니다.
                </div>
              ) : visibleItems.length > 0 ? (
                visibleItems.map((item) => (
                  <button
                    key={item.id}
                    type="button"
                    onClick={() => {
                      setSelectedId(item.id);
                      setSearchParams({ analysisId: String(item.id) }, { replace: true });
                    }}
                    className={`w-full rounded-lg border p-3 text-left transition-colors ${
                      selectedId === item.id ? "border-blue-300 bg-blue-50" : "border-slate-100 bg-slate-50 hover:border-blue-200"
                    }`}
                  >
                    <div className="flex items-start justify-between gap-3">
                      <div className="min-w-0">
                        <div className="truncate text-sm font-bold text-slate-800">{item.companyName} · {item.jobTitle}</div>
                        <div className="mt-0.5 truncate text-xs text-slate-500">{item.userName} ({item.userEmail})</div>
                      </div>
                      <span className={`text-sm font-black ${scoreTone(item.fitScore)}`}>{item.fitScore ?? 0}점</span>
                    </div>
                    <div className="mt-2 flex flex-wrap items-center gap-1.5">
                      {item.favorite && <Badge className="bg-amber-100 text-amber-700">관심</Badge>}
                      <Badge className="bg-slate-100 text-slate-600">{statusLabel[item.applicationStatus] ?? item.applicationStatus}</Badge>
                      <Badge className={item.status === "SUCCESS" ? "bg-green-100 text-green-700" : "bg-red-100 text-red-700"}>{item.status}</Badge>
                      {item.memoCount > 0 && <Badge className="bg-indigo-100 text-indigo-700">메모 {item.memoCount}</Badge>}
                      {item.reanalysisRequested && <Badge className="bg-amber-100 text-amber-700">재분석 필요</Badge>}
                      {gateBadge(item.gateStatus) && (
                        <Badge className={gateBadge(item.gateStatus)!.cls}>{gateBadge(item.gateStatus)!.label}</Badge>
                      )}
                    </div>
                    <Progress value={item.fitScore ?? 0} className="mt-2 h-1.5" />
                  </button>
                ))
              ) : (
                <div className="rounded-lg bg-slate-50 p-4 text-sm text-slate-500">적합도 분석 결과가 아직 없습니다.</div>
              )}
            </CardContent>
          </Card>

          <div className="space-y-5">
            {loadingDetail ? (
              <Card className="border border-slate-200 bg-card">
                <CardContent className="flex items-center gap-2 p-5 text-sm text-slate-500">
                  <Loader2 className="size-4 animate-spin" />
                  상세를 불러오는 중입니다.
                </CardContent>
              </Card>
            ) : detail ? (
              <>
                <Card className="border border-slate-200 bg-card">
                  <CardHeader className="pb-3">
                    <CardTitle className="flex items-center justify-between gap-3 text-base">
                      <span>{detail.companyName} · {detail.jobTitle}</span>
                      <span className={`text-xl font-black ${scoreTone(detail.fitScore)}`}>{detail.fitScore ?? 0}점</span>
                    </CardTitle>
                  </CardHeader>
                  <CardContent className="space-y-4">
                    <div className="grid gap-3 md:grid-cols-3">
                      {[
                        { label: "사용자", value: `${detail.userName} (${detail.userEmail})` },
                        { label: "지원 상태", value: statusLabel[detail.applicationStatus] ?? detail.applicationStatus },
                        { label: "분석 시각", value: formatDateTime(detail.createdAt) },
                        { label: "모델/프롬프트/상태", value: `${detail.model || "mock"} · ${detail.promptVersion || "버전 미기록"} · ${detail.status}` },
                      ].map((row) => (
                        <div key={row.label} className="rounded-lg bg-slate-50 p-3">
                          <div className="text-[11px] font-semibold text-slate-400">{row.label}</div>
                          <div className="mt-1 text-sm font-semibold text-slate-700">{row.value}</div>
                        </div>
                      ))}
                    </div>

                    {detail.gateStatus && (
                      <div
                        className={`rounded-lg border p-3 text-sm ${
                          detail.gateStatus === "REVIEW_REQUIRED"
                            ? "border-orange-200 bg-orange-50 text-orange-800"
                            : detail.gateStatus === "REJECTED"
                              ? "border-red-200 bg-red-50 text-red-800"
                              : "border-green-200 bg-green-50 text-green-800"
                        }`}
                      >
                        <div className="flex flex-wrap items-center gap-2 font-bold">
                          <AlertCircle className="size-4" />
                          근거 검토(evidence gate): {gateBadge(detail.gateStatus)?.label ?? detail.gateStatus}
                          {detail.gateMaxSeverity && (
                            <Badge className="bg-white/70 text-slate-700">심각도 {detail.gateMaxSeverity}</Badge>
                          )}
                          {detail.gateReasonCount > 0 && (
                            <Badge className="bg-white/70 text-slate-700">지적 {detail.gateReasonCount}건</Badge>
                          )}
                        </div>
                        <p className="mt-1 text-xs leading-relaxed">
                          {detail.gateStatus === "REVIEW_REQUIRED"
                            ? "AI 설명이 미보유 역량을 보유로 단정했을 가능성이 있어 자동 확정 대상이 아닙니다. 점수·지원 판단은 변경되지 않습니다."
                            : detail.gateStatus === "REJECTED"
                              ? "핵심 계약 필드가 깨져 재생성 검토가 필요합니다. 점수·지원 판단은 변경되지 않습니다."
                              : "근거 검토를 통과한 분석입니다."}
                          {detail.evidenceGateVersion ? ` (정책 ${detail.evidenceGateVersion})` : ""}
                        </p>
                        {detailGateReasons.length > 0 && (
                          <ul className="mt-2 space-y-1">
                            {detailGateReasons.map((gateReason, index) => (
                              <li
                                key={`${gateReason.type}:${gateReason.claim}:${index}`}
                                className="flex items-start gap-2 rounded bg-white/60 px-2 py-1 text-xs"
                              >
                                <span
                                  className={`shrink-0 rounded px-1.5 py-0.5 font-bold ${
                                    gateReason.severity === "critical"
                                      ? "bg-red-100 text-red-700"
                                      : "bg-amber-100 text-amber-700"
                                  }`}
                                >
                                  {gateReason.severity}
                                </span>
                                <span>
                                  <strong>{gateReason.claim}</strong> · {gateReason.reason}{" "}
                                  <span className="text-slate-400">({gateReason.type})</span>
                                </span>
                              </li>
                            ))}
                          </ul>
                        )}
                        {detail.gateReviewStatus && detail.gateReviewStatus !== "PENDING" && (
                          <div className="mt-2 text-xs font-semibold">
                            처리: {detail.gateReviewStatus === "RESOLVED" ? "검토 완료" : "재분석 요청"}
                            {detail.gateReviewerName ? ` · ${detail.gateReviewerName}` : ""}
                            {detail.gateReviewedAt ? ` · ${formatDateTime(detail.gateReviewedAt)}` : ""}
                          </div>
                        )}
                        <div className="mt-2 flex flex-wrap gap-2">
                          {detail.gateReviewStatus === "PENDING" && (
                            <>
                              <Button type="button" size="sm" onClick={() => void submitGateReview("RESOLVED")} disabled={savingMemo}>
                                검토 완료
                              </Button>
                              <Button type="button" size="sm" variant="outline" onClick={() => void submitGateReview("REANALYSIS_REQUESTED")} disabled={savingMemo}>
                                재분석 요청
                              </Button>
                            </>
                          )}
                          {detail.gateReviewStatus && detail.gateReviewStatus !== "PENDING" && (
                            <Button type="button" size="sm" variant="outline" onClick={() => void submitGateReview("PENDING")} disabled={savingMemo}>
                              검토 대기로 되돌리기
                            </Button>
                          )}
                        </div>
                      </div>
                    )}

                    <div className="grid gap-4 lg:grid-cols-2">
                      <SkillBox title="매칭 역량" icon="match" items={detail.matchedSkills} />
                      <SkillBox title="부족 역량" icon="gap" items={detail.missingSkills} />
                      <SkillBox title="추천 학습" icon="study" items={detail.recommendedStudy} />
                      <SkillBox title="추천 자격증" icon="cert" items={detail.recommendedCertificates} />
                    </div>

                    <SkillBox title="점수 산정 근거" icon="study" items={detail.scoreBasis} />
                    <SkillBox title="실행 전략" icon="study" items={detail.strategyActions} />

                    <div className="grid gap-4 lg:grid-cols-2">
                      <StructuredJsonBox title="입력 스냅샷" value={detail.sourceSnapshot} />
                      <StructuredJsonBox title="부족 역량 구조화 결과" value={detail.gapRecommendations} />
                      <StructuredJsonBox title="자격증 구조화 결과" value={detail.certificateRecommendations} />
                      <StructuredJsonBox title="요구조건-스펙 비교 매트릭스" value={detail.conditionMatrix} />
                      <StructuredJsonBox title="분석 신뢰도" value={detail.analysisConfidence} />
                      <StructuredJsonBox title="지원 판단 카드" value={detail.applyDecision} />
                      <div className="rounded-lg border border-slate-100 p-4">
                        <div className="mb-3 text-sm font-bold text-slate-800">학습 체크리스트</div>
                        <div className="space-y-2">
                          {detail.learningTasks.length > 0 ? detail.learningTasks.map((task) => (
                            <div key={task.id} className="rounded bg-slate-50 p-2 text-xs text-slate-600">
                              <strong className={task.completed ? "text-green-600" : "text-slate-800"}>{task.completed ? "완료" : "진행 전"} · {task.title}</strong>
                              <div className="mt-1">{task.skill} · {task.expectedDuration} · {task.priority}</div>
                            </div>
                          )) : <div className="text-sm text-slate-400">학습 과제가 없습니다.</div>}
                        </div>
                      </div>
                    </div>

                    {detail.errorMessage && <div className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700">{detail.errorMessage}</div>}

                    <div className="rounded-lg border border-blue-100 bg-blue-50 p-4">
                      <div className="mb-2 flex items-center gap-2 text-sm font-bold text-blue-900">
                        <CheckCircle2 className="size-4" />
                        지원 전략
                      </div>
                      <p className="text-sm leading-relaxed text-blue-800">{detail.strategy || "등록된 지원 전략이 없습니다."}</p>
                    </div>
                  </CardContent>
                </Card>

                <Card className="border border-slate-200 bg-card">
                  <CardHeader className="pb-3">
                    <CardTitle className="flex items-center gap-2 text-base">
                      <MessageSquareText className="size-4 text-indigo-600" />
                      운영 메모
                    </CardTitle>
                  </CardHeader>
                  <CardContent className="space-y-4">
                    <div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
                      <div className="flex flex-col gap-2 sm:flex-row">
                        <select
                          value={memoType}
                          onChange={(event) => setMemoType(event.target.value)}
                          className="h-10 rounded-md border border-slate-200 bg-card px-3 text-sm font-semibold text-slate-700"
                        >
                          {memoTypeOptions.map((option) => (
                            <option key={option.value} value={option.value}>{option.label}</option>
                          ))}
                        </select>
                        <textarea
                          value={memoContent}
                          onChange={(event) => setMemoContent(event.target.value)}
                          placeholder="운영 메모를 입력하세요"
                          className="min-h-24 flex-1 rounded-md border border-slate-200 bg-card px-3 py-2 text-sm text-slate-700 outline-none focus:border-blue-400"
                        />
                      </div>
                      <div className="mt-2 flex justify-end gap-2">
                        {editingMemo && (
                          <Button type="button" variant="outline" onClick={resetMemoForm} disabled={savingMemo}>
                            취소
                          </Button>
                        )}
                        <Button type="button" onClick={submitMemo} disabled={savingMemo || !memoContent.trim()}>
                          {savingMemo ? <Loader2 className="mr-2 size-4 animate-spin" /> : <PenLine className="mr-2 size-4" />}
                          {editingMemo ? "메모 수정" : "메모 저장"}
                        </Button>
                      </div>
                    </div>

                    <div className="space-y-2">
                      {detail.memos.length > 0 ? (
                        detail.memos.map((memo) => (
                          <div key={memo.id} className="rounded-lg border border-slate-100 p-3">
                            <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                              <div>
                                <div className="flex items-center gap-2">
                                  <Badge className="bg-indigo-100 text-indigo-700">
                                    {memoTypeOptions.find((option) => option.value === memo.memoType)?.label ?? memo.memoType}
                                  </Badge>
                                  <span className="text-xs text-slate-400">{memo.adminName} · {formatDateTime(memo.updatedAt)}</span>
                                </div>
                                <p className="mt-2 whitespace-pre-wrap text-sm leading-relaxed text-slate-700">{memo.content}</p>
                              </div>
                              <div className="flex shrink-0 gap-1">
                                <Button type="button" size="sm" variant="outline" onClick={() => startEditMemo(memo)} disabled={savingMemo}>
                                  <PenLine className="size-3.5" />
                                </Button>
                                <Button type="button" size="sm" variant="outline" onClick={() => removeMemo(memo)} disabled={savingMemo}>
                                  <Trash2 className="size-3.5 text-red-500" />
                                </Button>
                              </div>
                            </div>
                          </div>
                        ))
                      ) : (
                        <div className="rounded-lg bg-slate-50 p-4 text-sm text-slate-500">아직 운영 메모가 없습니다.</div>
                      )}
                    </div>
                  </CardContent>
                </Card>
              </>
            ) : (
              <Card className="border border-slate-200 bg-card">
                <CardContent className="p-5 text-sm text-slate-500">선택된 적합도 분석 결과가 없습니다.</CardContent>
              </Card>
            )}
          </div>
        </section>
      </div>
    </div>
  );
}

function StructuredJsonBox({ title, value }: { title: string; value: string | null }) {
  return (
    <div className="rounded-lg border border-slate-100 p-4">
      <div className="mb-3 text-sm font-bold text-slate-800">{title}</div>
      <pre className="max-h-52 overflow-auto whitespace-pre-wrap break-all rounded bg-[#0b0c0e] p-3 text-xs leading-5 text-[#e6e6e6]">
        {value || "저장된 결과 없음"}
      </pre>
    </div>
  );
}

function SkillBox({ title, icon, items }: { title: string; icon: "match" | "gap" | "study" | "cert"; items: string[] }) {
  const Icon = icon === "match" ? CheckCircle2 : icon === "gap" ? AlertCircle : icon === "cert" ? Bookmark : ClipboardList;
  const tone = icon === "match" ? "text-green-600" : icon === "gap" ? "text-red-500" : icon === "cert" ? "text-amber-600" : "text-blue-600";

  return (
    <div className="rounded-lg border border-slate-100 p-4">
      <div className="mb-3 flex items-center gap-2 text-sm font-bold text-slate-800">
        <Icon className={`size-4 ${tone}`} />
        {title}
      </div>
      <div className="flex flex-wrap gap-1.5">
        {items.length > 0 ? (
          items.map((item) => (
            <span key={item} className="rounded bg-slate-100 px-2 py-1 text-xs font-semibold text-slate-600">{item}</span>
          ))
        ) : (
          <span className="text-sm text-slate-400">등록된 항목이 없습니다.</span>
        )}
      </div>
    </div>
  );
}
