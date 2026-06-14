import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate, useParams } from "react-router";
import {
  ArrowLeft,
  BarChart3,
  Briefcase,
  Building2,
  FileText,
  Info,
  Plus,
  RefreshCw,
  Target,
} from "lucide-react";
import { useAuth } from "@/app/auth/AuthContext";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent } from "@/app/components/ui/card";
import { deleteApplicationCase, updateApplicationCase } from "../api/applicationCasesApi";
import { ApplicationOverviewPanel } from "../components/ApplicationOverviewPanel";
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
import { useApplicationCases } from "../hooks/useApplicationCases";
import { useBAnalysisFailureLogs } from "../hooks/useBAnalysisFailureLogs";
import { useCompanyAnalysis } from "../hooks/useCompanyAnalysis";
import { useJobAnalysis } from "../hooks/useJobAnalysis";
import { useJobPosting } from "../hooks/useJobPosting";
import type { ApplicationSourceType, UpdateApplicationCaseRequest } from "../types/applicationCase";
import type { JobPosting, JobPostingRequest } from "../types/jobPosting";
import { useApplicationFitAnalysis } from "@/features/analysis/hooks/useApplicationFitAnalysis";

type DetailTab = "overview" | "posting" | "jobAnalysis" | "companyAnalysis" | "fit";

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

export function ApplicationDetailPage() {
  const navigate = useNavigate();
  const params = useParams();
  const id = useMemo(() => {
    const value = Number(params.id);
    return Number.isFinite(value) && value > 0 ? value : null;
  }, [params.id]);
  const activeTab = params.section ? tabKeysBySlug[params.section] ?? "overview" : "overview";
  const { loading: authLoading, isAuthenticated } = useAuth();
  const {
    applicationCase,
    setApplicationCase,
    loading,
    error,
    refresh,
  } = useApplicationCase(id, isAuthenticated);
  const {
    applicationCases,
    loading: sidebarLoading,
  } = useApplicationCases(isAuthenticated);
  const {
    jobPosting,
    loading: postingLoading,
    saving: postingSaving,
    uploading: postingUploading,
    error: postingError,
    revisions: postingRevisions,
    save: savePosting,
    upload: uploadPosting,
  } = useJobPosting(id, isAuthenticated && Boolean(applicationCase));
  const {
    jobAnalysis,
    loading: jobAnalysisLoading,
    generating: jobAnalysisGenerating,
    error: jobAnalysisError,
    history: jobAnalysisHistory,
    generate: generateJobAnalysis,
    review: reviewJobAnalysis,
  } = useJobAnalysis(id, isAuthenticated && Boolean(applicationCase));
  const {
    companyAnalysis,
    loading: companyAnalysisLoading,
    generating: companyAnalysisGenerating,
    error: companyAnalysisError,
    history: companyAnalysisHistory,
    generate: generateCompanyAnalysis,
    review: reviewCompanyAnalysis,
  } = useCompanyAnalysis(id, isAuthenticated && Boolean(applicationCase));
  const {
    failureLogs: bFailureLogs,
    refresh: refreshBFailureLogs,
  } = useBAnalysisFailureLogs(id, isAuthenticated && Boolean(applicationCase));
  const [sourceTypeSyncError, setSourceTypeSyncError] = useState<string | null>(null);
  const {
    analyses: fitAnalyses,
    loading: fitAnalysisLoading,
    generating: fitGenerating,
    error: fitAnalysisError,
    generate: generateFit,
  } = useApplicationFitAnalysis(id, isAuthenticated && Boolean(applicationCase));
  useEffect(() => {
    if (id && params.section && !tabKeysBySlug[params.section]) {
      navigate(`/applications/${id}/overview`, { replace: true });
    }
  }, [id, navigate, params.section]);

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

  const handleSavePosting = async (request: JobPostingRequest): Promise<JobPosting | null> => {
    const posting = await savePosting(request);
    return syncCaseSourceType(posting);
  };

  const handleUploadPosting = async (
    sourceType: Extract<ApplicationSourceType, "PDF" | "IMAGE">,
    file: File,
  ): Promise<JobPosting | null> => {
    const posting = await uploadPosting(sourceType, file);
    return syncCaseSourceType(posting);
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
        <Card className="mx-auto max-w-lg border-slate-200 bg-white">
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

            <Card className="border-slate-200 bg-white">
              <CardContent className="space-y-2 p-3">
                <div className="mb-2 text-xs font-semibold text-slate-500">지원 건</div>
                {sidebarLoading ? (
                  <div className="h-24 animate-pulse rounded-md bg-slate-100" />
                ) : (
                  applicationCases.map((item) => (
                    <Link
                      key={item.id}
                      to={`/applications/${item.id}/${tabSlugs[activeTab]}`}
                      className={`block rounded-md border px-3 py-2 text-sm transition-colors ${
                        item.id === id
                          ? "border-blue-200 bg-blue-50 text-blue-800"
                          : "border-transparent hover:bg-slate-50"
                      }`}
                    >
                      <div className="truncate font-semibold">{item.companyName}</div>
                      <div className="truncate text-xs text-slate-500">{item.jobTitle}</div>
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
                </div>
              )}
            </div>
            <Button variant="outline" onClick={() => void refresh()} disabled={loading}>
              <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />
              새로고침
            </Button>
          </div>

          <div className="flex overflow-x-auto rounded-lg border border-slate-200 bg-white p-1">
            {detailTabs.map((tab) => (
              <button
                key={tab.key}
                type="button"
                className={`flex shrink-0 items-center gap-1.5 rounded-md px-3 py-2 text-sm font-semibold transition-colors ${
                  activeTab === tab.key
                    ? "bg-slate-900 text-white"
                    : "text-slate-600 hover:bg-slate-100 hover:text-slate-900"
                }`}
                onClick={() => navigate(`/applications/${id}/${tabSlugs[tab.key]}`)}
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

          {loading || !applicationCase ? (
            <div className="h-96 animate-pulse rounded-lg bg-slate-200" />
          ) : (
            <>
              {activeTab === "overview" && (
                <div className="space-y-4">
                  <ApplicationOverviewPanel
                    applicationCase={applicationCase}
                    onUpdate={handleUpdate}
                    onDelete={handleDelete}
                  />
                  <Card className="border-slate-200 bg-white">
                    <CardContent className="grid gap-3 p-4 md:grid-cols-3">
                      {[
                        "적합도 분석 완료 - 위 '적합도' 탭에서 확인",
                        "예상 질문 / 모의 면접 준비 중 - D 담당",
                        "첨삭 기록 준비 중 - E 담당",
                      ].map((item) => (
                        <div key={item} className="rounded-lg border border-slate-200 bg-slate-50 p-3 text-sm text-slate-600">
                          {item}
                        </div>
                      ))}
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
                  onSave={handleSavePosting}
                  onUpload={handleUploadPosting}
                />
              )}

              {activeTab === "jobAnalysis" && (
                <JobAnalysisPanel
                  analysis={jobAnalysis}
                  history={jobAnalysisHistory}
                  loading={jobAnalysisLoading}
                  generating={jobAnalysisGenerating}
                  error={jobAnalysisError}
                  failures={bFailureLogs}
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
                  error={companyAnalysisError}
                  failures={bFailureLogs}
                  onGenerate={handleGenerateCompanyAnalysis}
                  onReview={reviewCompanyAnalysis}
                />
              )}

              {activeTab === "fit" && (
                <div className="space-y-6">
                  {/* C 담당: 적합도/전략/학습 추천. 생성 트리거는 fit-analyses 엔드포인트(현재 mock). */}
                  <div className="flex flex-col gap-3 rounded-lg border border-slate-200 bg-white p-4 sm:flex-row sm:items-center sm:justify-between">
                    <p className="text-sm text-slate-600">공고 분석 결과와 내 프로필을 비교해 적합도·부족 역량·학습/자격증·전략을 분석합니다.</p>
                    <Button
                      className="bg-blue-600 text-white hover:bg-blue-700"
                      disabled={fitGenerating}
                      onClick={() => void generateFit()}
                    >
                      {fitGenerating ? "분석 중..." : fitAnalyses.length > 0 ? "적합도 재분석" : "적합도 분석 생성"}
                    </Button>
                  </div>
                  <FitAnalysisPanel analyses={fitAnalyses} loading={fitAnalysisLoading} generating={fitGenerating} error={fitAnalysisError} />
                  <StrategyPanel analyses={fitAnalyses} loading={fitAnalysisLoading} error={fitAnalysisError} />
                  <LearningRecommendationPanel
                    analyses={fitAnalyses}
                    loading={fitAnalysisLoading}
                    error={fitAnalysisError}
                    onReanalyze={() => void generateFit()}
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
