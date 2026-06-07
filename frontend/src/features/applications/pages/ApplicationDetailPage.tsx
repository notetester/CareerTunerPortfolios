import { useMemo, useState } from "react";
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
} from "lucide-react";
import { useAuth } from "@/app/auth/AuthContext";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent } from "@/app/components/ui/card";
import { deleteApplicationCase, updateApplicationCase } from "../api/applicationCasesApi";
import { ApplicationOverviewPanel } from "../components/ApplicationOverviewPanel";
import { ApplicationStatusBadge } from "../components/ApplicationStatusBadge";
import { CompanyAnalysisPanel } from "../components/CompanyAnalysisPanel";
import { JobAnalysisPanel } from "../components/JobAnalysisPanel";
import { JobPostingPanel } from "../components/JobPostingPanel";
import { LoginRequiredState } from "../components/LoginRequiredState";
import { useApplicationCase } from "../hooks/useApplicationCase";
import { useApplicationCases } from "../hooks/useApplicationCases";
import { useCompanyAnalysis } from "../hooks/useCompanyAnalysis";
import { useJobAnalysis } from "../hooks/useJobAnalysis";
import { useJobPosting } from "../hooks/useJobPosting";
import type { UpdateApplicationCaseRequest } from "../types/applicationCase";

type DetailTab = "overview" | "posting" | "jobAnalysis" | "companyAnalysis";

const detailTabs: { key: DetailTab; label: string; icon: typeof Info }[] = [
  { key: "overview", label: "개요", icon: Info },
  { key: "posting", label: "공고문", icon: FileText },
  { key: "jobAnalysis", label: "공고 분석", icon: BarChart3 },
  { key: "companyAnalysis", label: "기업 분석", icon: Building2 },
];

export function ApplicationDetailPage() {
  const navigate = useNavigate();
  const params = useParams();
  const id = useMemo(() => {
    const value = Number(params.id);
    return Number.isFinite(value) && value > 0 ? value : null;
  }, [params.id]);
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
    error: postingError,
    save: savePosting,
  } = useJobPosting(id, isAuthenticated && Boolean(applicationCase));
  const {
    jobAnalysis,
    loading: jobAnalysisLoading,
    generating: jobAnalysisGenerating,
    error: jobAnalysisError,
    createMock: createMockJobAnalysis,
  } = useJobAnalysis(id, isAuthenticated && Boolean(applicationCase));
  const {
    companyAnalysis,
    loading: companyAnalysisLoading,
    generating: companyAnalysisGenerating,
    error: companyAnalysisError,
    createMock: createMockCompanyAnalysis,
  } = useCompanyAnalysis(id, isAuthenticated && Boolean(applicationCase));
  const [activeTab, setActiveTab] = useState<DetailTab>("overview");

  const handleUpdate = async (request: UpdateApplicationCaseRequest) => {
    if (!id) return;
    const updated = await updateApplicationCase(id, request);
    setApplicationCase(updated);
  };

  const handleDelete = async () => {
    if (!id) return;
    await deleteApplicationCase(id);
    navigate("/applications");
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
      <div className="mx-auto grid max-w-7xl gap-6 px-4 py-8 lg:grid-cols-[260px_minmax(0,1fr)] sm:px-6">
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
                      to={`/applications/${item.id}`}
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
                onClick={() => setActiveTab(tab.key)}
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
                        "스펙 비교 준비 중 - C 담당",
                        "예상 질문 / 가상 면접 준비 중 - D 담당",
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
                  loading={postingLoading}
                  saving={postingSaving}
                  error={postingError}
                  onSave={savePosting}
                />
              )}

              {activeTab === "jobAnalysis" && (
                <JobAnalysisPanel
                  analysis={jobAnalysis}
                  loading={jobAnalysisLoading}
                  generating={jobAnalysisGenerating}
                  error={jobAnalysisError}
                  onCreateMock={createMockJobAnalysis}
                />
              )}

              {activeTab === "companyAnalysis" && (
                <CompanyAnalysisPanel
                  analysis={companyAnalysis}
                  loading={companyAnalysisLoading}
                  generating={companyAnalysisGenerating}
                  error={companyAnalysisError}
                  onCreateMock={createMockCompanyAnalysis}
                />
              )}
            </>
          )}
        </main>
      </div>
    </div>
  );
}
