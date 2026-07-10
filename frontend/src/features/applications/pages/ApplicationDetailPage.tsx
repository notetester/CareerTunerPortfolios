import { useEffect, useMemo, useRef, useState } from "react";
import { Link, useNavigate, useParams } from "react-router";
import {
  ArrowLeft,
  BarChart3,
  Briefcase,
  Building2,
  FileText,
  Info,
  MessageSquare,
  PenLine,
  Plus,
  RefreshCw,
  Target,
} from "lucide-react";
import { useAuth } from "@/app/auth/AuthContext";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent } from "@/app/components/ui/card";
import { AiChargeCostBadge } from "@/features/billing/components/AiChargeCostBadge";
import { deleteApplicationCase, updateApplicationCase } from "../api/applicationCasesApi";
import { getApplicationCaseAnalysisOverview } from "../api/analysisApi";
import { ApplicationOverviewPanel } from "../components/ApplicationOverviewPanel";
import { ApplicationExtractionBadge } from "../components/ApplicationExtractionBadge";
import { ApplicationStatusBadge } from "../components/ApplicationStatusBadge";
import { CompanyAnalysisPanel } from "../components/CompanyAnalysisPanel";
import { FitAnalysisHistoryPanel } from "../components/FitAnalysisHistoryPanel";
import { FitAnalysisPanel } from "../components/FitAnalysisPanel";
import { JobAnalysisPanel } from "../components/JobAnalysisPanel";
import { JobPostingPanel } from "../components/JobPostingPanel";
import { LearningRecommendationPanel } from "../components/LearningRecommendationPanel";
import { LoginRequiredState } from "../components/LoginRequiredState";
import { StrategyPanel } from "../components/StrategyPanel";
import { useApplicationCase } from "../hooks/useApplicationCase";
import { useApplicationCaseExtraction } from "../hooks/useApplicationCaseExtraction";
import { useApplicationCases } from "../hooks/useApplicationCases";
import { useBAnalysisFailureLogs } from "../hooks/useBAnalysisFailureLogs";
import { useCompanyAnalysis } from "../hooks/useCompanyAnalysis";
import { useJobAnalysis } from "../hooks/useJobAnalysis";
import { useJobPosting } from "../hooks/useJobPosting";
import type { ApplicationSourceType, UpdateApplicationCaseRequest } from "../types/applicationCase";
import type { JobPosting, JobPostingRequest } from "../types/jobPosting";
import { registerApplicationCaseExtraction } from "../utils/applicationExtractionTracker";
import {
  currentPostingText,
  hasPostingSourceChange,
  isConfirmableTextCorrection,
  requestPostingText,
} from "../utils/jobPostingConfirm";
import { useApplicationFitAnalysis } from "@/features/analysis/hooks/useApplicationFitAnalysis";
import { toast } from "@/features/notification/components/toast";
import { useNotificationStore } from "@/features/notification/hooks/useNotificationStore";

type DetailTab = "overview" | "posting" | "jobAnalysis" | "companyAnalysis" | "fit";
type DetailMode = "view" | "edit";

const detailTabs: { key: DetailTab; label: string; icon: typeof Info }[] = [
  { key: "overview", label: "개요", icon: Info },
  { key: "posting", label: "공고문", icon: FileText },
  { key: "jobAnalysis", label: "공고 분석", icon: BarChart3 },
  { key: "companyAnalysis", label: "기업 분석", icon: Building2 },
  { key: "fit", label: "적합도", icon: Target },
];

const tabSlugs: Record<DetailTab, string> = {
  overview: "overview",
  posting: "posting",
  jobAnalysis: "job-analysis",
  companyAnalysis: "company-analysis",
  fit: "fit",
};

const tabKeysBySlug = Object.fromEntries(
  Object.entries(tabSlugs).map(([key, slug]) => [slug, key]),
) as Record<string, DetailTab>;

function isBAnalysisTab(tab: DetailTab): tab is "jobAnalysis" | "companyAnalysis" {
  return tab === "jobAnalysis" || tab === "companyAnalysis";
}

function detailPath(applicationCaseId: number, tab: DetailTab, mode: DetailMode = "view") {
  const basePath = `/applications/${applicationCaseId}/${tabSlugs[tab]}`;
  return isBAnalysisTab(tab) && mode === "edit" ? `${basePath}/edit` : basePath;
}

export function ApplicationDetailPage() {
  const navigate = useNavigate();
  const params = useParams();
  const id = useMemo(() => {
    const value = Number(params.id);
    return Number.isFinite(value) && value > 0 ? value : null;
  }, [params.id]);
  const activeTab = params.section ? tabKeysBySlug[params.section] ?? "overview" : "overview";
  const activeMode: DetailMode = isBAnalysisTab(activeTab) && params.mode === "edit" ? "edit" : "view";
  const { loading: authLoading, isAuthenticated } = useAuth();
  const {
    applicationCase,
    setApplicationCase,
    loading,
    error,
    refresh,
  } = useApplicationCase(id, isAuthenticated);
  const detailDataEnabled = isAuthenticated && Boolean(applicationCase);
  const needsExtraction = detailDataEnabled && (activeTab === "overview" || activeTab === "posting");
  const needsJobPosting = detailDataEnabled && (
    activeTab === "posting" ||
    activeTab === "jobAnalysis" ||
    activeTab === "companyAnalysis"
  );
  const needsJobPostingRevisions = activeTab === "posting";
  const needsJobAnalysis = detailDataEnabled && activeTab === "jobAnalysis";
  const needsCompanyAnalysis = detailDataEnabled && activeTab === "companyAnalysis";
  const needsBFailureLogs = needsJobAnalysis || needsCompanyAnalysis;
  const needsFitAnalysis = detailDataEnabled && activeTab === "fit";
  const {
    applicationCases,
    loading: sidebarLoading,
  } = useApplicationCases(isAuthenticated);
  const fetchNotifications = useNotificationStore((state) => state.fetchNotifications);
  const {
    jobPosting,
    loading: postingLoading,
    saving: postingSaving,
    uploading: postingUploading,
    error: postingError,
    revisions: postingRevisions,
    refresh: refreshPosting,
    save: savePosting,
    upload: uploadPosting,
  } = useJobPosting(id, needsJobPosting, { loadRevisions: needsJobPostingRevisions });
  const {
    extraction,
    retrying: retryingExtraction,
    reviewing: reviewingExtraction,
    confirming: confirmingExtraction,
    error: extractionError,
    reviewError: extractionReviewError,
    confirmError: extractionConfirmError,
    refresh: refreshExtraction,
    retry: retryExtraction,
    review: reviewExtraction,
    confirm: confirmExtraction,
  } = useApplicationCaseExtraction(id, needsExtraction);
  const {
    jobAnalysis,
    loading: jobAnalysisLoading,
    generating: jobAnalysisGenerating,
    reviewSaving: jobAnalysisReviewSaving,
    error: jobAnalysisError,
    reviewError: jobAnalysisReviewError,
    history: jobAnalysisHistory,
    refresh: refreshJobAnalysis,
    generate: generateJobAnalysis,
    review: reviewJobAnalysis,
  } = useJobAnalysis(id, needsJobAnalysis);
  const {
    companyAnalysis,
    loading: companyAnalysisLoading,
    generating: companyAnalysisGenerating,
    reviewSaving: companyAnalysisReviewSaving,
    error: companyAnalysisError,
    reviewError: companyAnalysisReviewError,
    history: companyAnalysisHistory,
    refresh: refreshCompanyAnalysis,
    generate: generateCompanyAnalysis,
    review: reviewCompanyAnalysis,
  } = useCompanyAnalysis(id, needsCompanyAnalysis);
  const {
    failureLogs: bFailureLogs,
    refresh: refreshBFailureLogs,
  } = useBAnalysisFailureLogs(id, needsBFailureLogs);
  const [sourceTypeSyncError, setSourceTypeSyncError] = useState<string | null>(null);
  const {
    analyses: fitAnalyses,
    loading: fitAnalysisLoading,
    generating: fitGenerating,
    error: fitAnalysisError,
    generate: generateFit,
  } = useApplicationFitAnalysis(id, needsFitAnalysis);
  const refreshedExtractionIdRef = useRef<number | null>(null);

  useEffect(() => {
    if (id && params.section && !tabKeysBySlug[params.section]) {
      navigate(`/applications/${id}/overview`, { replace: true });
      return;
    }

    if (id && params.mode && activeMode === "view") {
      navigate(detailPath(id, activeTab), { replace: true });
    }
  }, [activeMode, activeTab, id, navigate, params.mode, params.section]);

  useEffect(() => {
    if (!extraction || extraction.status !== "SUCCEEDED" || refreshedExtractionIdRef.current === extraction.id) {
      return;
    }

    refreshedExtractionIdRef.current = extraction.id;
    void refresh();
    if (needsJobPosting) {
      void refreshPosting();
    }
  }, [extraction, needsJobPosting, refresh, refreshPosting]);

  const handleUpdate = async (request: UpdateApplicationCaseRequest) => {
    if (!id) return;
    const updated = await updateApplicationCase(id, request);
    setApplicationCase(updated);
  };

  const syncCaseSourceType = async (posting: JobPosting | null): Promise<JobPosting | null> => {
    if (posting) {
      setSourceTypeSyncError(null);
    }

    if (!id || !applicationCase || !posting || applicationCase.sourceType === posting.sourceType) {
      return posting;
    }

    try {
      const updated = await updateApplicationCase(id, { sourceType: posting.sourceType });
      setApplicationCase(updated);
    } catch {
      setSourceTypeSyncError("공고는 저장됐지만 지원 건 유형 동기화에 실패했습니다. 새로고침 후 다시 확인해 주세요.");
    }
    return posting;
  };

  const refreshAndTrackExtraction = async (previousExtractionId: number | null) => {
    const latest = await refreshExtraction();
    if (latest && latest.id !== previousExtractionId) {
      registerApplicationCaseExtraction(latest);
    }
  };

  const handleSavePosting = async (request: JobPostingRequest): Promise<JobPosting | null> => {
    // 소스/URL·본문 모두 변경 없음: 새 revision·재추출을 만들지 않고 그대로 둔다.
    if (
      jobPosting &&
      !hasPostingSourceChange(request, jobPosting) &&
      requestPostingText(request) === currentPostingText(jobPosting)
    ) {
      return jobPosting;
    }

    // PASS 공고의 본문만 수정한 경우: OCR/추출을 다시 돌리지 않고 confirm(분석만 갱신)로 보낸다.
    if (jobPosting && isConfirmableTextCorrection({ request, jobPosting, extraction })) {
      const confirmed = await confirmExtraction(requestPostingText(request));
      if (!confirmed) {
        return jobPosting;
      }
      const refreshed = await refreshPosting();
      await fetchNotifications();
      await refreshBFailureLogs();
      toast.success("수정한 공고문 기준으로 분석을 갱신했습니다.");
      return refreshed ?? jobPosting;
    }

    // 소스/URL 변경 등 재추출이 필요한 경우: 기존 추출 큐잉 경로.
    const previousExtractionId = extraction?.id ?? null;
    const posting = await savePosting(request);
    const syncedPosting = await syncCaseSourceType(posting);
    await refreshAndTrackExtraction(previousExtractionId);
    return syncedPosting;
  };

  const handleUploadPosting = async (
    sourceType: Extract<ApplicationSourceType, "PDF" | "IMAGE">,
    file: File,
  ): Promise<JobPosting | null> => {
    const previousExtractionId = extraction?.id ?? null;
    const posting = await uploadPosting(sourceType, file);
    const syncedPosting = await syncCaseSourceType(posting);
    await refreshAndTrackExtraction(previousExtractionId);
    return syncedPosting;
  };

  const handleReviewExtraction = async (extractedText: string) => {
    const reviewedExtraction = await reviewExtraction(extractedText);
    if (reviewedExtraction) {
      await refreshPosting();
      await fetchNotifications();
      toast.success("공고문 검수가 완료됐습니다.");
    }
    return reviewedExtraction;
  };

  const handleDelete = async () => {
    if (!id) return;
    await deleteApplicationCase(id);
    navigate("/applications");
  };

  const handleGenerateJobAnalysis = async () => {
    const analysis = await generateJobAnalysis();
    await refreshBFailureLogs();
    return analysis;
  };

  const handleGenerateCompanyAnalysis = async () => {
    const analysis = await generateCompanyAnalysis();
    await refreshBFailureLogs();
    return analysis;
  };

  const handleRefreshCurrentTab = async () => {
    await refresh();
    const tasks: Promise<unknown>[] = [];
    if (needsExtraction) tasks.push(refreshExtraction());
    if (needsJobPosting) tasks.push(refreshPosting());
    if (needsJobAnalysis) tasks.push(refreshJobAnalysis());
    if (needsCompanyAnalysis) tasks.push(refreshCompanyAnalysis());
    if (needsBFailureLogs) tasks.push(refreshBFailureLogs());
    await Promise.all(tasks);
  };

  if (authLoading) {
    return (
      <div className="min-h-[calc(100vh-72px)] bg-slate-50 px-4 py-10">
        <div className="mx-auto max-w-7xl">
          <div className="h-96 animate-pulse rounded-lg bg-slate-200" />
        </div>
      </div>
    );
  }

  if (!isAuthenticated) {
    return (
      <LoginRequiredState
        title="지원 건 상세는 로그인 후 확인할 수 있습니다"
        description="지원 건 상세와 공고문은 본인 데이터만 조회합니다."
      />
    );
  }

  if (!id) {
    return (
      <div className="min-h-[calc(100vh-72px)] bg-slate-50 px-4 py-10">
        <Card className="mx-auto max-w-lg border-slate-200 bg-card">
          <CardContent className="space-y-4 p-8 text-center">
            <div className="font-semibold text-slate-900">지원 건 ID가 올바르지 않습니다.</div>
            <Button onClick={() => navigate("/applications")}>목록으로 이동</Button>
          </CardContent>
        </Card>
      </div>
    );
  }

  return (
    <div className="min-h-[calc(100vh-72px)] bg-slate-50">
      <div className="mx-auto grid max-w-7xl gap-6 px-4 py-8 sm:px-6 lg:grid-cols-[260px_minmax(0,1fr)]">
        <aside className="hidden lg:block">
          <div className="sticky top-20 space-y-4">
            <div className="flex items-center justify-between">
              <Button asChild variant="ghost" className="px-0 text-slate-600 hover:bg-transparent hover:text-blue-700">
                <Link to="/applications">
                  <ArrowLeft className="size-4" />
                  목록
                </Link>
              </Button>
              <Button size="sm" className="bg-blue-600 text-white hover:bg-blue-700" onClick={() => navigate("/applications/new")}>
                <Plus className="size-4" />
              </Button>
            </div>

            <Card className="border-slate-200 bg-card">
              <CardContent className="space-y-2 p-3">
                <div className="mb-2 text-xs font-semibold text-slate-500">지원 건</div>
                {sidebarLoading ? (
                  <div className="h-24 animate-pulse rounded-md bg-slate-100" />
                ) : (
                  applicationCases.map((item) => (
                    <Link
                      key={item.id}
                      to={detailPath(item.id, activeTab, activeMode)}
                      className={`block rounded-md border px-3 py-2 text-sm transition-colors ${
                        item.id === id
                          ? "border-blue-200 bg-blue-50 text-blue-800"
                          : "border-transparent hover:bg-slate-50"
                      }`}
                    >
                      <div className="truncate font-semibold">{item.companyName}</div>
                      <div className="truncate text-xs text-slate-500">{item.jobTitle}</div>
                      <div className="mt-1.5">
                        <ApplicationStatusBadge status={item.status} />
                      </div>
                    </Link>
                  ))
                )}
              </CardContent>
            </Card>
          </div>
        </aside>

        <main className="min-w-0 space-y-5">
          <div className="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
            <div className="min-w-0">
              <Button asChild variant="ghost" className="mb-3 px-0 text-slate-600 hover:bg-transparent hover:text-blue-700 lg:hidden">
                <Link to="/applications">
                  <ArrowLeft className="size-4" />
                  목록
                </Link>
              </Button>
              <h1 className="flex min-w-0 items-center gap-2 text-2xl font-bold text-slate-950">
                <Briefcase className="size-6 shrink-0 text-blue-600" />
                <span className="truncate">
                  {applicationCase ? `${applicationCase.companyName} · ${applicationCase.jobTitle}` : "지원 건 상세"}
                </span>
              </h1>
              {applicationCase && (
                <div className="mt-2 flex flex-wrap gap-2">
                  <ApplicationStatusBadge status={applicationCase.status} />
                  <ApplicationExtractionBadge extraction={extraction} />
                  {extraction?.status === "FAILED" && (
                    <Button
                      type="button"
                      size="sm"
                      variant="outline"
                      className="h-7 border-red-200 px-2 text-xs text-red-700 hover:bg-red-50 hover:text-red-800"
                      disabled={retryingExtraction}
                      onClick={() => void retryExtraction()}
                    >
                      <RefreshCw className={`size-3.5 ${retryingExtraction ? "animate-spin" : ""}`} />
                      다시 추출
                    </Button>
                  )}
                </div>
              )}
            </div>
            <Button variant="outline" onClick={() => void handleRefreshCurrentTab()} disabled={loading}>
              <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />
              새로고침
            </Button>
          </div>

          <div className="flex flex-wrap gap-1 rounded-lg border border-slate-200 bg-card p-1">
            {detailTabs.map((tab) => (
              <button
                key={tab.key}
                type="button"
                className={`flex shrink-0 items-center gap-1.5 rounded-md px-3 py-2 text-sm font-semibold transition-colors ${
                  activeTab === tab.key
                    ? "bg-foreground text-background"
                    : "text-slate-600 hover:bg-slate-100 hover:text-slate-900"
                }`}
                onClick={() => navigate(detailPath(id, tab.key))}
              >
                <tab.icon className="size-4" />
                {tab.label}
              </button>
            ))}
          </div>

          {error && (
            <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
              {error}
            </div>
          )}

          {sourceTypeSyncError && (
            <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-700">
              {sourceTypeSyncError}
            </div>
          )}

          {extractionError && (
            <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
              {extractionError}
            </div>
          )}

          {loading || !applicationCase ? (
            <div className="h-96 animate-pulse rounded-lg bg-slate-200" />
          ) : (
            <>
              {activeTab === "overview" && (
                <div className="space-y-4">
                  <ApplicationOverviewPanel
                    applicationCase={applicationCase}
                    extraction={extraction}
                    retryingExtraction={retryingExtraction}
                    onUpdate={handleUpdate}
                    onRetryExtraction={retryExtraction}
                    onDelete={handleDelete}
                  />
                  <AnalysisSummaryCard applicationCaseId={id} onGoFit={() => navigate(detailPath(id, "fit"))} />
                  <Card className="border-slate-200 bg-card">
                    <CardContent className="grid gap-3 p-4 md:grid-cols-3">
                      <button
                        type="button"
                        className="rounded-lg border border-slate-200 bg-slate-50 p-3 text-left text-sm text-slate-700 transition-colors hover:border-blue-200 hover:bg-blue-50"
                        onClick={() => navigate(detailPath(id, "fit"))}
                      >
                        <Target className="mb-2 size-4 text-blue-600" />
                        <div className="font-semibold text-slate-900">적합도 분석</div>
                        <div className="mt-1 text-xs text-slate-500">공고와 내 스펙 비교 결과 확인</div>
                      </button>
                      <button
                        type="button"
                        className="rounded-lg border border-slate-200 bg-slate-50 p-3 text-left text-sm text-slate-700 transition-colors hover:border-indigo-200 hover:bg-indigo-50"
                        onClick={() => navigate(`/interview?caseId=${applicationCase.id}&tab=modes`)}
                      >
                        <MessageSquare className="mb-2 size-4 text-indigo-600" />
                        <div className="font-semibold text-slate-900">예상 질문 / 모의 면접</div>
                        <div className="mt-1 text-xs text-slate-500">이 지원 건이 선택된 면접 화면에서 바로 시작</div>
                      </button>
                      {applicationCase.archived ? (
                        <div
                          className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900 opacity-60"
                          aria-disabled="true"
                        >
                          <PenLine className="mb-2 size-4" />
                          <div className="font-semibold">자기소개서 첨삭</div>
                          <div className="mt-1 text-xs">보관된 지원 건은 복원한 뒤 첨삭할 수 있습니다.</div>
                        </div>
                      ) : (
                        <Link
                          to={`/correction?tab=cover&caseId=${id}`}
                          className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900 transition-colors hover:border-amber-300 hover:bg-amber-100"
                        >
                          <PenLine className="mb-2 size-4" />
                          <div className="font-semibold">자기소개서 첨삭</div>
                          <div className="mt-1 text-xs">이 지원 건의 공고와 직무 맥락을 반영해 첨삭합니다.</div>
                        </Link>
                      )}
                    </CardContent>
                  </Card>
                </div>
              )}

              {activeTab === "posting" && (
                <JobPostingPanel
                  jobPosting={jobPosting}
                  revisions={postingRevisions}
                  loading={postingLoading}
                  saving={postingSaving}
                  uploading={postingUploading}
                  error={postingError}
                  extraction={extraction}
                  retryingExtraction={retryingExtraction}
                  reviewingExtraction={reviewingExtraction}
                  confirmingExtraction={confirmingExtraction}
                  reviewExtractionError={extractionReviewError}
                  confirmExtractionError={extractionConfirmError}
                  onSave={handleSavePosting}
                  onUpload={handleUploadPosting}
                  onRetryExtraction={retryExtraction}
                  onReviewExtraction={handleReviewExtraction}
                />
              )}

              {activeTab === "jobAnalysis" && (
                <JobAnalysisPanel
                  analysis={jobAnalysis}
                  history={jobAnalysisHistory}
                  loading={jobAnalysisLoading}
                  generating={jobAnalysisGenerating}
                  reviewSaving={jobAnalysisReviewSaving}
                  error={jobAnalysisError}
                  reviewError={jobAnalysisReviewError}
                  failures={bFailureLogs}
                  mode={activeMode}
                  viewHref={detailPath(id, "jobAnalysis")}
                  editHref={detailPath(id, "jobAnalysis", "edit")}
                  latestJobPostingRevision={jobPosting?.revision ?? null}
                  onGenerate={handleGenerateJobAnalysis}
                  onReview={reviewJobAnalysis}
                />
              )}

              {activeTab === "companyAnalysis" && (
                <CompanyAnalysisPanel
                  analysis={companyAnalysis}
                  history={companyAnalysisHistory}
                  loading={companyAnalysisLoading}
                  generating={companyAnalysisGenerating}
                  reviewSaving={companyAnalysisReviewSaving}
                  error={companyAnalysisError}
                  reviewError={companyAnalysisReviewError}
                  failures={bFailureLogs}
                  mode={activeMode}
                  viewHref={detailPath(id, "companyAnalysis")}
                  editHref={detailPath(id, "companyAnalysis", "edit")}
                  latestJobPostingRevision={jobPosting?.revision ?? null}
                  onGenerate={handleGenerateCompanyAnalysis}
                  onReview={reviewCompanyAnalysis}
                />
              )}

              {activeTab === "fit" && (
                <div className="space-y-6">
                  {/* C 담당: 적합도/전략/학습 추천. 생성 트리거는 fit-analyses 엔드포인트(현재 mock). */}
                  <div className="flex flex-col gap-3 rounded-lg border border-slate-200 bg-card p-4 sm:flex-row sm:items-center sm:justify-between">
                    <p className="text-sm text-slate-600">공고 분석 결과와 내 프로필을 비교해 적합도·부족 역량·학습/자격증·전략을 분석합니다.</p>
                    <div className="flex shrink-0 flex-col items-start gap-1.5 sm:items-end">
                      <AiChargeCostBadge featureType="FIT_ANALYSIS" />
                      <Button
                        className="bg-blue-600 text-white hover:bg-blue-700"
                        disabled={fitGenerating}
                        onClick={() => void generateFit()}
                      >
                        {fitGenerating ? "분석 중..." : fitAnalyses.length > 0 ? "적합도 재분석" : "적합도 분석 생성"}
                      </Button>
                    </div>
                  </div>
                  <FitAnalysisPanel analyses={fitAnalyses} loading={fitAnalysisLoading} generating={fitGenerating} error={fitAnalysisError} />
                  <StrategyPanel analyses={fitAnalyses} loading={fitAnalysisLoading} error={fitAnalysisError} />
                  <LearningRecommendationPanel
                    analyses={fitAnalyses}
                    loading={fitAnalysisLoading}
                    error={fitAnalysisError}
                    onReanalyze={() => void generateFit(true)}
                    reanalyzing={fitGenerating}
                  />
                  {/* C 담당: 재분석 히스토리(점수·역량 변화 추적). 최신 분석 id가 바뀌면 다시 불러온다. */}
                  <FitAnalysisHistoryPanel
                    applicationCaseId={id}
                    enabled={isAuthenticated && Boolean(applicationCase)}
                    refreshKey={fitAnalyses[0]?.id ?? null}
                  />
                </div>
              )}
            </>
          )}
        </main>
      </div>
    </div>
  );
}

/** 지원 건 분석 종합 — GET /application-cases/{id}/analysis 로 공고/적합도 분석 완료 여부를 한눈에 보여준다. */
function AnalysisSummaryCard({ applicationCaseId, onGoFit }: { applicationCaseId: number; onGoFit: () => void }) {
  const [jobDone, setJobDone] = useState<boolean | null>(null);
  const [fitDone, setFitDone] = useState<boolean | null>(null);

  useEffect(() => {
    let ignore = false;
    getApplicationCaseAnalysisOverview(applicationCaseId)
      .then((data) => {
        if (ignore) return;
        setJobDone(Boolean(data.jobAnalysis));
        setFitDone(Boolean(data.fitAnalysis));
      })
      .catch(() => {
        if (ignore) return;
        setJobDone(false);
        setFitDone(false);
      });
    return () => { ignore = true; };
  }, [applicationCaseId]);

  const cell = (label: string, done: boolean | null, icon: typeof Target) => {
    const Icon = icon;
    return (
      <div className="flex items-center gap-2 rounded-lg border border-slate-200 bg-slate-50 p-3 text-sm">
        <Icon className="size-4 text-slate-500" />
        <span className="font-semibold text-slate-800">{label}</span>
        <span className={`ml-auto rounded-full px-2 py-0.5 text-[11px] font-semibold ${
          done == null ? "bg-slate-100 text-slate-500" : done ? "bg-green-100 text-green-700" : "bg-slate-100 text-slate-500"
        }`}>
          {done == null ? "확인 중" : done ? "완료" : "미완료"}
        </span>
      </div>
    );
  };

  return (
    <Card className="border-slate-200 bg-card">
      <CardContent className="space-y-2 p-4">
        <div className="flex items-center justify-between">
          <div className="text-sm font-semibold text-slate-700">AI 분석 종합</div>
          <button type="button" className="text-xs font-semibold text-blue-600 hover:text-blue-700" onClick={onGoFit}>
            적합도 보기
          </button>
        </div>
        <div className="grid gap-2 sm:grid-cols-2">
          {cell("공고 분석", jobDone, BarChart3)}
          {cell("적합도 분석", fitDone, Target)}
        </div>
      </CardContent>
    </Card>
  );
}
