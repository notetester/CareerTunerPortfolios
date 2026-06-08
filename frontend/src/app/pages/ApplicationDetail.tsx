import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate, useParams } from "react-router";
import {
  AlertCircle,
  ArrowLeft,
  BarChart3,
  BookOpen,
  Briefcase,
  CheckCircle2,
  FileText,
  GraduationCap,
  Loader2,
  Map,
  Play,
  RefreshCw,
  Sparkles,
  Star,
  Target,
} from "lucide-react";
import { Badge } from "../components/ui/badge";
import { Button } from "../components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "../components/ui/card";
import { Progress } from "../components/ui/progress";
import {
  createMockAnalysis,
  getApplicationAnalysis,
  getApplicationCase,
  getApplicationCases,
  getJobPosting,
} from "@/features/applications/api/applicationCasesApi";
import type {
  ApplicationAnalysis,
  ApplicationCase,
  ApplicationFitAnalysis,
  JobAnalysis,
  JobPosting,
} from "@/features/applications/types/applicationCase";
import { parseJsonList, scoreTone } from "@/features/analysis/types/fitAnalysis";

const tabs = [
  { key: "job", label: "공고 분석", icon: FileText },
  { key: "fit", label: "스펙 비교", icon: Target },
  { key: "strategy", label: "지원 전략", icon: Map },
  { key: "learning", label: "학습 추천", icon: GraduationCap },
  { key: "posting", label: "공고 원문", icon: Briefcase },
] as const;
type DetailTab = (typeof tabs)[number]["key"];

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

function formatDate(value: string | null | undefined) {
  if (!value) return "날짜 없음";
  return new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium" }).format(new Date(value));
}

function difficultyLabel(value: string | null | undefined) {
  if (value === "EASY") return "낮음";
  if (value === "NORMAL") return "보통";
  if (value === "HARD") return "높음";
  return "미정";
}

export function ApplicationDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const applicationCaseId = Number(id);
  const [activeTab, setActiveTab] = useState<DetailTab>("fit");
  const [applications, setApplications] = useState<ApplicationCase[]>([]);
  const [application, setApplication] = useState<ApplicationCase | null>(null);
  const [jobPosting, setJobPosting] = useState<JobPosting | null>(null);
  const [analysis, setAnalysis] = useState<ApplicationAnalysis | null>(null);
  const [loading, setLoading] = useState(true);
  const [analyzing, setAnalyzing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const fitAnalysis = analysis?.fitAnalysis ?? null;
  const jobAnalysis = analysis?.jobAnalysis ?? null;
  const tone = scoreTone(fitAnalysis?.fitScore);
  const matchedSkills = useMemo(() => parseJsonList(fitAnalysis?.matchedSkills), [fitAnalysis?.matchedSkills]);
  const missingSkills = useMemo(() => parseJsonList(fitAnalysis?.missingSkills), [fitAnalysis?.missingSkills]);
  const requiredSkills = useMemo(() => parseJsonList(jobAnalysis?.requiredSkills), [jobAnalysis?.requiredSkills]);
  const preferredSkills = useMemo(() => parseJsonList(jobAnalysis?.preferredSkills), [jobAnalysis?.preferredSkills]);
  const studyItems = useMemo(() => parseJsonList(fitAnalysis?.recommendedStudy), [fitAnalysis?.recommendedStudy]);
  const certificates = useMemo(() => parseJsonList(fitAnalysis?.recommendedCertificates), [fitAnalysis?.recommendedCertificates]);

  useEffect(() => {
    if (!Number.isFinite(applicationCaseId) || applicationCaseId <= 0) {
      setError("지원 건 번호가 올바르지 않습니다.");
      setLoading(false);
      return;
    }

    let ignore = false;
    setLoading(true);
    setError(null);
    setNotice(null);

    Promise.all([
      getApplicationCases(),
      getApplicationCase(applicationCaseId),
      getApplicationAnalysis(applicationCaseId),
      getJobPosting(applicationCaseId).catch(() => null),
    ])
      .then(([caseList, currentCase, currentAnalysis, currentPosting]) => {
        if (ignore) return;
        setApplications(caseList);
        setApplication(currentCase);
        setAnalysis(currentAnalysis);
        setJobPosting(currentPosting);
      })
      .catch((requestError) => {
        if (!ignore) setError(requestError instanceof Error ? requestError.message : "지원 건 상세를 불러오지 못했습니다.");
      })
      .finally(() => {
        if (!ignore) setLoading(false);
      });

    return () => {
      ignore = true;
    };
  }, [applicationCaseId]);

  async function handleCreateMockAnalysis() {
    if (!application) return;
    setAnalyzing(true);
    setError(null);
    setNotice(null);

    try {
      const nextAnalysis = await createMockAnalysis(application.id);
      const [nextApplication, nextApplications] = await Promise.all([
        getApplicationCase(application.id),
        getApplicationCases(),
      ]);
      setApplication(nextApplication);
      setApplications(nextApplications);
      setAnalysis(nextAnalysis);
      setNotice("개발용 샘플 분석이 생성됐습니다. 실제 API 키가 발급되면 같은 위치에 실제 분석 결과가 표시됩니다.");
      setActiveTab("fit");
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "분석 샘플을 생성하지 못했습니다.");
    } finally {
      setAnalyzing(false);
    }
  }

  if (loading) {
    return (
      <div className="min-h-screen bg-slate-50 p-6">
        <Card className="mx-auto max-w-3xl border border-slate-200 bg-white">
          <CardContent className="flex items-center gap-3 p-6 text-sm text-slate-600">
            <Loader2 className="size-4 animate-spin text-blue-600" />
            지원 건 상세를 불러오는 중입니다.
          </CardContent>
        </Card>
      </div>
    );
  }

  if (error && !application) {
    return (
      <div className="min-h-screen bg-slate-50 p-6">
        <Card className="mx-auto max-w-3xl border border-red-200 bg-red-50">
          <CardContent className="space-y-4 p-6">
            <div className="flex items-center gap-2 text-sm font-semibold text-red-700">
              <AlertCircle className="size-4" />
              {error}
            </div>
            <Button variant="outline" onClick={() => navigate("/applications")}>
              지원 건 목록으로 이동
            </Button>
          </CardContent>
        </Card>
      </div>
    );
  }

  if (!application) return null;

  return (
    <div className="flex min-h-[calc(100vh-64px)] bg-slate-50">
      <aside className="hidden w-64 shrink-0 border-r border-slate-200 bg-slate-950 text-white lg:block">
        <div className="border-b border-slate-800 p-4">
          <button
            type="button"
            onClick={() => navigate("/applications")}
            className="inline-flex items-center gap-1.5 text-xs font-semibold text-slate-400 hover:text-white"
          >
            <ArrowLeft className="size-3.5" />
            전체 지원 건
          </button>
        </div>
        <div className="space-y-1 p-3">
          {applications.map((item) => {
            const active = item.id === application.id;
            return (
              <Link
                key={item.id}
                to={`/applications/${item.id}`}
                className={`block rounded-lg p-3 transition-colors ${active ? "bg-blue-600" : "hover:bg-slate-900"}`}
              >
                <div className="flex items-center justify-between gap-2">
                  <div className="truncate text-sm font-bold">{item.companyName}</div>
                  {item.favorite && <Star className="size-3.5 shrink-0 fill-amber-300 text-amber-300" />}
                </div>
                <div className="mt-0.5 truncate text-xs text-slate-300">{item.jobTitle}</div>
                <div className="mt-2">
                  <Badge className={`text-[10px] ${statusColor[item.status] ?? "bg-slate-100 text-slate-700"}`}>
                    {statusLabel[item.status] ?? item.status}
                  </Badge>
                </div>
              </Link>
            );
          })}
        </div>
      </aside>

      <main className="min-w-0 flex-1">
        <div className="border-b border-slate-200 bg-white px-5 py-4">
          <div className="flex flex-col gap-4 xl:flex-row xl:items-center xl:justify-between">
            <div>
              <div className="flex flex-wrap items-center gap-2">
                <div className="flex size-9 items-center justify-center rounded-lg bg-gradient-to-br from-blue-600 to-indigo-600 text-sm font-black text-white">
                  {application.companyName[0]}
                </div>
                <h1 className="text-xl font-black text-slate-900">
                  {application.companyName} · {application.jobTitle}
                </h1>
                {application.favorite && <Badge className="bg-amber-100 text-amber-700">관심</Badge>}
                <Badge className={statusColor[application.status] ?? "bg-slate-100 text-slate-700"}>
                  {statusLabel[application.status] ?? application.status}
                </Badge>
                {fitAnalysis && <Badge className="bg-indigo-100 text-indigo-700">샘플 AI 결과</Badge>}
              </div>
              <div className="mt-2 text-sm text-slate-500">
                마감/공고일 {formatDate(application.postingDate)} · 입력 방식 {application.sourceType} · 최근 수정 {formatDate(application.updatedAt)}
              </div>
            </div>
            <div className="flex flex-wrap items-center gap-2">
              <Button variant="outline" onClick={() => navigate("/analysis")} className="gap-1.5">
                <BarChart3 className="size-4" />
                종합 분석
              </Button>
              <Button onClick={handleCreateMockAnalysis} disabled={analyzing} className="gap-1.5 bg-blue-600 hover:bg-blue-700">
                {analyzing ? <Loader2 className="size-4 animate-spin" /> : <Play className="size-4" />}
                {fitAnalysis ? "샘플 재분석" : "샘플 분석 생성"}
              </Button>
            </div>
          </div>

          {notice && (
            <div className="mt-4 rounded-lg border border-blue-200 bg-blue-50 px-4 py-3 text-sm text-blue-800">
              {notice}
            </div>
          )}
          {error && (
            <div className="mt-4 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
              {error}
            </div>
          )}
        </div>

        <div className="border-b border-slate-200 bg-white px-5">
          <div className="flex overflow-x-auto">
            {tabs.map((tab) => (
              <button
                key={tab.key}
                type="button"
                onClick={() => setActiveTab(tab.key)}
                className={`flex shrink-0 items-center gap-1.5 border-b-2 px-4 py-3 text-xs font-bold transition-colors ${
                  activeTab === tab.key
                    ? "border-blue-600 text-blue-600"
                    : "border-transparent text-slate-500 hover:text-blue-600"
                }`}
              >
                <tab.icon className="size-3.5" />
                {tab.label}
              </button>
            ))}
          </div>
        </div>

        <div className="grid gap-5 p-5 xl:grid-cols-[minmax(0,1fr)_300px]">
          <section className="min-w-0">
            {!fitAnalysis && !jobAnalysis && (
              <Card className="mb-5 border border-amber-200 bg-amber-50">
                <CardContent className="flex flex-col gap-3 p-5 sm:flex-row sm:items-center sm:justify-between">
                  <div className="flex items-start gap-3">
                    <Sparkles className="mt-0.5 size-5 text-amber-600" />
                    <div>
                      <div className="text-sm font-bold text-amber-900">아직 분석 결과가 없습니다.</div>
                      <div className="mt-1 text-sm text-amber-700">
                        API 키 발급 전에는 개발용 샘플 분석을 생성해서 실제 화면 흐름을 검증합니다.
                      </div>
                    </div>
                  </div>
                  <Button onClick={handleCreateMockAnalysis} disabled={analyzing} className="gap-1.5 bg-amber-600 hover:bg-amber-700">
                    {analyzing ? <Loader2 className="size-4 animate-spin" /> : <RefreshCw className="size-4" />}
                    샘플 분석 생성
                  </Button>
                </CardContent>
              </Card>
            )}

            {activeTab === "job" && <JobAnalysisSection jobAnalysis={jobAnalysis} requiredSkills={requiredSkills} preferredSkills={preferredSkills} />}
            {activeTab === "fit" && <FitAnalysisSection fitAnalysis={fitAnalysis} matchedSkills={matchedSkills} missingSkills={missingSkills} />}
            {activeTab === "strategy" && <StrategySection fitAnalysis={fitAnalysis} />}
            {activeTab === "learning" && <LearningSection studyItems={studyItems} certificates={certificates} />}
            {activeTab === "posting" && <PostingSection jobPosting={jobPosting} />}
          </section>

          <aside className="space-y-4">
            <Card className="border border-slate-200 bg-white">
              <CardHeader className="pb-3">
                <CardTitle className="flex items-center gap-2 text-base">
                  <Target className="size-4 text-blue-600" />
                  적합도 요약
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="text-center">
                  <div className={`text-5xl font-black ${tone.text}`}>{fitAnalysis?.fitScore ?? 0}</div>
                  <div className="mt-1 text-xs text-slate-500">/ 100점</div>
                </div>
                <Progress value={fitAnalysis?.fitScore ?? 0} className="h-2" />
                <Badge className={`w-full justify-center ${tone.bg} ${tone.text}`}>{fitAnalysis ? tone.label : "미분석"}</Badge>
              </CardContent>
            </Card>

            <QuickList title="매칭 역량" icon="match" items={matchedSkills} />
            <QuickList title="부족 역량" icon="gap" items={missingSkills} />
            <QuickList title="추천 학습" icon="study" items={studyItems.slice(0, 4)} />
          </aside>
        </div>
      </main>
    </div>
  );
}

function JobAnalysisSection({
  jobAnalysis,
  requiredSkills,
  preferredSkills,
}: {
  jobAnalysis: JobAnalysis | null;
  requiredSkills: string[];
  preferredSkills: string[];
}) {
  if (!jobAnalysis) return <EmptyState title="공고 분석 결과가 없습니다." description="샘플 분석을 생성하면 공고 요구사항이 구조화되어 표시됩니다." />;

  return (
    <div className="space-y-4">
      <div className="grid gap-3 md:grid-cols-4">
        <InfoCard label="고용 형태" value={jobAnalysis.employmentType ?? "미정"} />
        <InfoCard label="경력 조건" value={jobAnalysis.experienceLevel ?? "미정"} />
        <InfoCard label="난이도" value={difficultyLabel(jobAnalysis.difficulty)} />
        <InfoCard label="분석 시점" value={formatDate(jobAnalysis.createdAt)} />
      </div>
      <Card className="border border-slate-200 bg-white">
        <CardHeader>
          <CardTitle className="text-base">공고 요약</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <p className="text-sm leading-6 text-slate-700">{jobAnalysis.summary ?? "요약 없음"}</p>
          <TagBlock title="필수 역량" items={requiredSkills} tone="red" />
          <TagBlock title="우대 역량" items={preferredSkills} tone="blue" />
          <TextBlock title="담당 업무" value={jobAnalysis.duties} />
          <TextBlock title="자격 요건" value={jobAnalysis.qualifications} />
        </CardContent>
      </Card>
    </div>
  );
}

function FitAnalysisSection({
  fitAnalysis,
  matchedSkills,
  missingSkills,
}: {
  fitAnalysis: ApplicationFitAnalysis | null;
  matchedSkills: string[];
  missingSkills: string[];
}) {
  if (!fitAnalysis) return <EmptyState title="스펙 비교 결과가 없습니다." description="샘플 분석을 생성하면 프로필과 공고 요구사항의 매칭 결과가 표시됩니다." />;

  const tone = scoreTone(fitAnalysis.fitScore);

  return (
    <div className="space-y-4">
      <Card className="border border-slate-200 bg-white">
        <CardContent className="space-y-4 p-5">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <div className="text-sm font-semibold text-slate-500">직무 적합도</div>
              <div className={`mt-1 text-4xl font-black ${tone.text}`}>{fitAnalysis.fitScore ?? 0}점</div>
            </div>
            <Badge className={`${tone.bg} ${tone.text}`}>{tone.label}</Badge>
          </div>
          <Progress value={fitAnalysis.fitScore ?? 0} className="h-2" />
          <div className="grid gap-4 md:grid-cols-2">
            <TagBlock title="매칭된 역량" items={matchedSkills} tone="green" />
            <TagBlock title="부족한 역량" items={missingSkills} tone="red" />
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

function StrategySection({ fitAnalysis }: { fitAnalysis: ApplicationFitAnalysis | null }) {
  if (!fitAnalysis) return <EmptyState title="지원 전략이 없습니다." description="적합도 분석을 생성하면 지원서와 면접에서 강조할 전략이 표시됩니다." />;

  return (
    <Card className="border border-slate-200 bg-white">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <Map className="size-4 text-blue-600" />
          지원 전략
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="rounded-xl bg-blue-50 p-4 text-sm leading-6 text-blue-900">
          {fitAnalysis.strategy ?? "지원 전략이 아직 생성되지 않았습니다."}
        </div>
        <div className="grid gap-3 md:grid-cols-3">
          <InfoCard label="지원서" value="공고 필수 역량과 맞는 경험을 첫 문단에 배치" />
          <InfoCard label="포트폴리오" value="부족 역량 보완 프로젝트와 정량 성과 추가" />
          <InfoCard label="면접" value="강점과 보완 계획을 STAR 구조로 준비" />
        </div>
      </CardContent>
    </Card>
  );
}

function LearningSection({ studyItems, certificates }: { studyItems: string[]; certificates: string[] }) {
  if (studyItems.length === 0 && certificates.length === 0) {
    return <EmptyState title="학습 추천이 없습니다." description="적합도 분석을 생성하면 부족 역량 기반 학습 과제가 표시됩니다." />;
  }

  return (
    <div className="grid gap-4 lg:grid-cols-2">
      <RecommendationCard title="추천 학습" icon={BookOpen} items={studyItems} emptyText="추천 학습 없음" />
      <RecommendationCard title="추천 자격증" icon={GraduationCap} items={certificates} emptyText="우선 추천 자격증 없음" />
    </div>
  );
}

function PostingSection({ jobPosting }: { jobPosting: JobPosting | null }) {
  if (!jobPosting) return <EmptyState title="등록된 공고문이 없습니다." description="공고문이 없어도 샘플 분석은 가능하지만, 실제 분석 품질은 공고 원문이 있을 때 좋아집니다." />;
  const text = jobPosting.extractedText || jobPosting.originalText || "공고문 텍스트가 비어 있습니다.";

  return (
    <Card className="border border-slate-200 bg-white">
      <CardHeader>
        <CardTitle className="text-base">공고 원문</CardTitle>
        <p className="text-sm text-slate-500">입력 방식 {jobPosting.sourceType} · 등록 {formatDate(jobPosting.createdAt)}</p>
      </CardHeader>
      <CardContent>
        <div className="whitespace-pre-wrap rounded-xl bg-slate-50 p-4 text-sm leading-6 text-slate-700">{text}</div>
      </CardContent>
    </Card>
  );
}

function InfoCard({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white p-4">
      <div className="text-xs font-semibold text-slate-500">{label}</div>
      <div className="mt-1 text-sm font-bold leading-5 text-slate-900">{value}</div>
    </div>
  );
}

function TagBlock({ title, items, tone }: { title: string; items: string[]; tone: "green" | "red" | "blue" }) {
  const style = {
    green: "bg-green-50 text-green-700",
    red: "bg-red-50 text-red-700",
    blue: "bg-blue-50 text-blue-700",
  }[tone];

  return (
    <div>
      <div className="mb-2 text-sm font-bold text-slate-800">{title}</div>
      <div className="flex flex-wrap gap-1.5">
        {items.length > 0 ? (
          items.map((item) => (
            <span key={item} className={`rounded-full px-2 py-1 text-xs font-semibold ${style}`}>
              {item}
            </span>
          ))
        ) : (
          <span className="text-sm text-slate-400">분석된 항목 없음</span>
        )}
      </div>
    </div>
  );
}

function TextBlock({ title, value }: { title: string; value: string | null }) {
  return (
    <div>
      <div className="mb-1 text-sm font-bold text-slate-800">{title}</div>
      <div className="rounded-xl bg-slate-50 p-4 text-sm leading-6 text-slate-700">{value || "분석된 내용 없음"}</div>
    </div>
  );
}

function RecommendationCard({
  title,
  icon: Icon,
  items,
  emptyText,
}: {
  title: string;
  icon: typeof BookOpen;
  items: string[];
  emptyText: string;
}) {
  return (
    <Card className="border border-slate-200 bg-white">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <Icon className="size-4 text-blue-600" />
          {title}
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-2">
        {(items.length > 0 ? items : [emptyText]).map((item, index) => (
          <div key={`${item}-${index}`} className="rounded-lg bg-slate-50 p-3 text-sm leading-5 text-slate-700">
            {item}
          </div>
        ))}
      </CardContent>
    </Card>
  );
}

function QuickList({ title, icon, items }: { title: string; icon: "match" | "gap" | "study"; items: string[] }) {
  const Icon = icon === "match" ? CheckCircle2 : icon === "gap" ? AlertCircle : BookOpen;
  const color = icon === "match" ? "text-green-600" : icon === "gap" ? "text-red-500" : "text-blue-600";

  return (
    <Card className="border border-slate-200 bg-white">
      <CardHeader className="pb-3">
        <CardTitle className="flex items-center gap-2 text-base">
          <Icon className={`size-4 ${color}`} />
          {title}
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-2">
        {items.length > 0 ? (
          items.map((item) => (
            <div key={item} className="rounded-lg bg-slate-50 px-3 py-2 text-sm text-slate-700">
              {item}
            </div>
          ))
        ) : (
          <div className="text-sm text-slate-400">표시할 항목 없음</div>
        )}
      </CardContent>
    </Card>
  );
}

function EmptyState({ title, description }: { title: string; description: string }) {
  return (
    <Card className="border border-slate-200 bg-white">
      <CardContent className="flex items-start gap-3 p-5">
        <Sparkles className="mt-0.5 size-5 text-blue-600" />
        <div>
          <div className="text-sm font-bold text-slate-900">{title}</div>
          <div className="mt-1 text-sm text-slate-500">{description}</div>
        </div>
      </CardContent>
    </Card>
  );
}
