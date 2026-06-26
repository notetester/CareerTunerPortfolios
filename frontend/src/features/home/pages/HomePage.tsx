import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate } from "react-router";
import { useAuth } from "@/app/auth/AuthContext";
import { Button } from "@/app/components/ui/button";
import { Badge } from "@/app/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Progress } from "@/app/components/ui/progress";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/app/components/ui/tabs";
import {
  Sparkles, ArrowRight, CheckCircle2, FileText, Target, MessageSquare,
  TrendingUp, Users, BarChart3, Zap, Shield, Clock, Star,
  ChevronRight, Play, Building2, Briefcase, BookOpen, PenTool,
  Award, Bot, Mic, Video, Brain, AlertCircle, ThumbsUp, Search,
  Plus, Calendar, Loader2, Flame, RefreshCw, Settings2,
} from "lucide-react";
import { getDashboardSummary, refreshDashboardSummary } from "@/features/dashboard/api/dashboardApi";
import type { DashboardActivity, DashboardSummary, DashboardTodo } from "@/features/dashboard/types/dashboardSummary";
import { TodoChecklist } from "@/features/dashboard/components/TodoChecklist";
import { AiResultBadge } from "@/features/analysis/components/AiResultBadge";
import { InterviewHero } from "@/features/interview/components/InterviewHero";
import { AutoPrepPanel } from "@/features/autoprep/components/AutoPrepPanel";
import { GuideButton, type TourStep } from "@/features/analysis/components/GuideTour";

const coreFeaturesData = [
  {
    icon: FileText,
    title: "공고문 AI 분석",
    desc: "텍스트/PDF/이미지 업로드만 하면 AI가 요구 기술, 우대 조건, 직무 역량, 예상 난이도를 자동 추출",
    color: "",
    badge: "핵심 기능",
  },
  {
    icon: Building2,
    title: "기업 현황 조사",
    desc: "기업 주요 사업, 최근 이슈, 경쟁사, 면접에서 언급하면 좋은 포인트를 AI가 자동 정리",
    color: "",
    badge: "프로",
  },
  {
    icon: Target,
    title: "내 스펙 비교 분석",
    desc: "공고 요구조건과 내 프로필을 1:1 비교해 직무 적합도 점수와 부족 역량을 정확히 진단",
    color: "",
    badge: "핵심 기능",
  },
  {
    icon: BookOpen,
    title: "학습/자격증 추천",
    desc: "부족한 역량에 맞는 학습 방향, 자격증, 강의를 우선순위 기준으로 추천",
    color: "",
    badge: "신규",
  },
  {
    icon: MessageSquare,
    title: "AI 가상 면접 (8가지 모드)",
    desc: "기본/직무/인성/압박/실전/자소서 기반 등 8가지 면접 모드로 실전처럼 연습",
    color: "",
    badge: "대표 기능",
  },
  {
    icon: Mic,
    title: "음성 면접 & 분석",
    desc: "마이크로 답변하면 음성 인식 텍스트 변환 + 말속도, 침묵 시간, 발화 길이까지 분석",
    color: "",
    badge: "프로",
  },
  {
    icon: PenTool,
    title: "답변 첨삭 & 개선",
    desc: "AI가 논리성, 구체성, 직무 적합성을 평가하고 완성도 높은 개선 답변까지 제시",
    color: "",
    badge: "핵심 기능",
  },
  {
    icon: TrendingUp,
    title: "장기 취업 경향 분석",
    desc: "여러 지원 건을 종합해 반복되는 약점, 지원 패턴, 장기 취업 전략을 AI가 추천",
    color: "",
    badge: "프로",
  },
];

const interviewModes = [
  { title: "기본 면접", desc: "자기소개, 지원동기, 장단점", icon: MessageSquare, color: "bg-card border-border" },
  { title: "직무 면접", desc: "공고 기반 기술/직무 질문", icon: Settings2, color: "bg-card border-border" },
  { title: "인성 면접", desc: "협업, 갈등, 책임감, 태도", icon: Users, color: "bg-card border-border" },
  { title: "압박 면접", desc: "꼬리 질문, 반박 질문", icon: Zap, color: "bg-card border-border" },
  { title: "실전 면접", desc: "시간 제한, 랜덤 질문", icon: Clock, color: "bg-card border-border" },
  { title: "자소서 기반", desc: "자기소개서 문장 기반 질문", icon: FileText, color: "bg-card border-border" },
  { title: "포트폴리오 기반", desc: "프로젝트 설명 중심 질문", icon: Briefcase, color: "bg-card border-border" },
  { title: "기업 맞춤", desc: "기업 현황과 공고 기반 질문", icon: Building2, color: "bg-card border-border" },
];

const flowSteps = [
  { step: "01", title: "회원가입 & 프로필 작성", desc: "이력서, 기술스택, 자격증, 포트폴리오 등록" },
  { step: "02", title: "새 지원 건 생성", desc: "기업명, 직무, 채용일자 입력" },
  { step: "03", title: "채용공고 업로드", desc: "텍스트 붙여넣기, PDF/이미지 업로드, URL 입력" },
  { step: "04", title: "AI 공고문 분석", desc: "요구 기술, 우대 조건, 직무 역량 자동 추출" },
  { step: "05", title: "기업 현황 조사", desc: "최근 이슈, 경쟁사, 면접 포인트 정리" },
  { step: "06", title: "스펙 비교 & 적합도 진단", desc: "내 프로필과 공고 요구사항 1:1 비교" },
  { step: "07", title: "준비 전략 수립", desc: "부족 역량 우선순위, 학습 방향, 자격증 추천" },
  { step: "08", title: "예상 면접 질문 생성", desc: "기술, 인성, 상황별 예상 질문 목록" },
  { step: "09", title: "AI 가상 면접 진행", desc: "선택한 모드로 실전처럼 모의면접" },
  { step: "10", title: "답변 평가 & 첨삭", desc: "논리성, 구체성, 직무 적합성 점수 + 개선 답변" },
  { step: "11", title: "면접 리포트 확인", desc: "전체 결과 요약, 약점 분석" },
  { step: "12", title: "장기 취업 경향 분석", desc: "여러 지원 건 종합 → 맞춤 전략 추천" },
];

const specComparisonData = [
  { skill: "React", status: "보유", grade: "강점", color: "text-muted-foreground bg-secondary" },
  { skill: "TypeScript", status: "일부 경험", grade: "보완 필요", color: "text-muted-foreground bg-secondary" },
  { skill: "AWS", status: "없음", grade: "학습 필요", color: "text-muted-foreground bg-secondary" },
  { skill: "Git 협업", status: "보유", grade: "강점", color: "text-muted-foreground bg-secondary" },
  { skill: "REST API", status: "보유", grade: "강점", color: "text-muted-foreground bg-secondary" },
  { skill: "포트폴리오", status: "부족", grade: "보완 필요", color: "text-muted-foreground bg-secondary" },
];

const communityPosts = [
  { cat: "면접 후기", company: "카카오페이", job: "프론트엔드", title: "카카오페이 프론트엔드 1차 면접 후기 (합격)", views: 2847, likes: 124, hot: true },
  { cat: "합격 전략", company: "네이버", job: "백엔드", title: "네이버 신입 백엔드 합격 후기 - 준비 과정 총정리", views: 5129, likes: 341, hot: true },
  { cat: "직무별 질문", company: "익명", job: "전산직", title: "공기업 전산직 자주 나오는 기술 질문 100선", views: 8293, likes: 562, hot: false },
  { cat: "취업 후기", company: "삼성SDS", job: "IT 솔루션", title: "삼성SDS IT 솔루션 최종 합격 스펙 & 준비 방법", views: 3412, likes: 187, hot: false },
  { cat: "자유게시판", company: "익명", job: "프론트엔드", title: "CareerTuner으로 면접 준비하고 최종 합격했습니다", views: 1923, likes: 89, hot: false },
];

const testimonials = [
  { name: "이*현", job: "카카오 프론트엔드 합격", avatar: "이", plan: "프로", text: "공고 분석과 스펙 비교 기능이 정말 도움이 됐어요. 부족한 부분을 정확히 짚어줘서 집중적으로 준비할 수 있었습니다." },
  { name: "박*준", job: "네이버 백엔드 합격", avatar: "박", plan: "프리미엄", text: "AI 가상 면접을 20번 넘게 연습했는데, 실제 면접에서 정말 비슷한 질문이 나왔어요. 답변도 훨씬 자연스럽게 나왔습니다." },
  { name: "김*영", job: "삼성SDS 최종 합격", avatar: "김", plan: "프로", text: "답변 첨삭 기능이 최고예요. 막연하게 답했던 것들이 어떻게 개선되어야 하는지 구체적으로 알려줘서 좋았습니다." },
];

const dashboardStatusLabel: Record<string, string> = {
  DRAFT: "공고 입력",
  ANALYZING: "분석 중",
  READY: "준비중",
  APPLIED: "지원 완료",
  CLOSED: "마감",
};

const dashboardStatusColor: Record<string, string> = {
  DRAFT: "bg-slate-100 text-slate-700",
  ANALYZING: "bg-secondary text-muted-foreground",
  READY: "bg-secondary text-muted-foreground",
  APPLIED: "bg-secondary text-muted-foreground",
  CLOSED: "bg-zinc-100 text-zinc-500",
};

function formatDashboardDate(value: string | null) {
  if (!value) return "날짜 없음";
  return new Intl.DateTimeFormat("ko-KR", {
    month: "2-digit",
    day: "2-digit",
  }).format(new Date(value));
}

function formatDashboardTime(value: string) {
  const diffMs = Date.now() - new Date(value).getTime();
  const minutes = Math.floor(diffMs / 60000);
  if (minutes < 1) return "방금 전";
  if (minutes < 60) return `${minutes}분 전`;

  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}시간 전`;

  const days = Math.floor(hours / 24);
  if (days < 7) return `${days}일 전`;

  return formatDashboardDate(value);
}

function dashboardActivityMeta(type: DashboardActivity["type"]) {
  if (type === "INTERVIEW") return { icon: MessageSquare, color: "text-muted-foreground", bg: "bg-secondary" };
  if (type === "APPLICATION") return { icon: Target, color: "text-muted-foreground", bg: "bg-secondary" };
  return { icon: FileText, color: "text-muted-foreground", bg: "bg-secondary" };
}

// C 영역 홈 페이지 안내(가이드 투어) 스텝. data-tour 앵커를 가리킨다.
const HOME_TOUR_STEPS: TourStep[] = [
  { selector: "[data-tour='home-ai-summary']", title: "AI 대시보드 요약", body: "최근 지원 건들을 종합한 적합도·다음 액션 요약입니다. 오른쪽 '재생성'을 누르면 최신 데이터로 AI가 다시 만들어요(크레딧 1 차감)." },
  { selector: "[data-tour='home-readiness']", title: "준비도 점수", body: "지원 건 분석·학습·면접 진척을 합산한 전체 준비도입니다. 분석 가능한 지원 건이 없으면 0%로 표시돼요." },
  { selector: "[data-tour='home-fit-cards']", title: "진행 중 지원 건", body: "회사·직무와 적합도 점수가 보입니다. 카드를 누르면 지원 건 상세로 이동해 공고-스펙 적합도를 확인해요." },
  { selector: "[data-tour='home-todos']", title: "오늘의 우선순위", body: "적합도·부족 역량 분석에서 파생된 할 일입니다. 체크하면 완료 처리되고, 아래 입력창으로 직접 추가할 수 있어요." },
  { selector: "[data-tour='home-skillgaps']", title: "자주 부족한 역량", body: "여러 지원 건에서 반복되는 갭을 집계합니다. 학습·자격증 추천의 근거가 됩니다." },
];

interface MemberHomeProps {
  summary: DashboardSummary | null;
  loading: boolean;
  error: string | null;
  fallbackName: string;
  onRetry: () => void;
  onSummaryRefreshed: (data: DashboardSummary) => void;
  onTodosChange: (todos: DashboardTodo[]) => void;
}

function MemberHome({ summary, loading, error, fallbackName, onRetry, onSummaryRefreshed, onTodosChange }: MemberHomeProps) {
  const navigate = useNavigate();
  const [refreshing, setRefreshing] = useState(false);
  const [refreshError, setRefreshError] = useState<string | null>(null);

  const handleRefreshSummary = async () => {
    setRefreshing(true);
    setRefreshError(null);
    try {
      onSummaryRefreshed(await refreshDashboardSummary());
    } catch (requestError) {
      setRefreshError(requestError instanceof Error ? requestError.message : "요약 재생성에 실패했습니다.");
    } finally {
      setRefreshing(false);
    }
  };

  const stats = summary?.stats;
  const pendingTodos = summary?.todos.filter((todo) => !todo.done).length ?? 0;
  const creditPercent = stats ? Math.min(100, Math.round((stats.credit / Math.max(1, stats.creditLimit)) * 100)) : 0;
  const highFitCount = summary?.recentApplications.filter((application) => (application.fitScore ?? 0) >= 70).length ?? 0;
  const urgentApplications = summary?.recentApplications
    .filter((application) => application.status !== "CLOSED")
    .slice(0, 3) ?? [];

  const statCards = useMemo(() => {
    if (!stats) return [];

    return [
      {
        icon: Briefcase,
        label: "활성 지원 건",
        value: `${stats.activeApplications}`,
        sub: `이번 달 ${stats.newApplicationsThisMonth}건 추가`,
        color: "",
      },
      {
        icon: TrendingUp,
        label: "평균 적합도",
        value: `${stats.averageFitScore}점`,
        sub: `${highFitCount}건은 우선 지원 후보`,
        color: "",
      },
      {
        icon: MessageSquare,
        label: "면접 연습",
        value: `${stats.totalInterviews}회`,
        sub: `이번 주 ${stats.interviewsThisWeek}회 진행`,
        color: "",
      },
      {
        icon: Award,
        label: "보유 크레딧",
        value: `${stats.credit}`,
        sub: `이번 달 ${stats.creditsUsedThisMonth}크레딧 사용`,
        color: "",
      },
    ];
  }, [highFitCount, stats]);

  if (loading && !summary) {
    return (
      <div className="min-h-screen bg-slate-50">
        <div className="max-w-[1400px] mx-auto px-4 sm:px-6 py-8">
          <Card className="border border-slate-200 bg-card">
            <CardContent className="flex items-center gap-3 p-6 text-sm text-slate-600">
              <Loader2 className="size-5 animate-spin text-blue-600" />
              회원 홈 데이터를 불러오는 중입니다.
            </CardContent>
          </Card>
        </div>
      </div>
    );
  }

  if (error && !summary) {
    return (
      <div className="min-h-screen bg-slate-50">
        <div className="max-w-[1400px] mx-auto px-4 sm:px-6 py-8">
          <Card className="border border-red-200 bg-red-50">
            <CardContent className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 p-6">
              <div className="flex items-start gap-3 text-sm text-red-700">
                <AlertCircle className="size-5 mt-0.5 flex-shrink-0" />
                <span>{error}</span>
              </div>
              <Button variant="outline" className="border-red-300 text-red-700 hover:bg-red-100" onClick={onRetry}>
                다시 불러오기
              </Button>
            </CardContent>
          </Card>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-50">
      <div className="max-w-[1400px] mx-auto px-4 sm:px-6 py-6 lg:py-8 space-y-6">
        <div className="flex items-center justify-end">
          <GuideButton steps={HOME_TOUR_STEPS} />
        </div>
        <AutoPrepPanel />
        <section className="grid lg:grid-cols-[1.5fr_0.9fr] gap-5">
          <div className="rounded-2xl border border-slate-200 bg-card p-5 sm:p-7 shadow-sm">
            <div className="flex flex-col lg:flex-row lg:items-start lg:justify-between gap-5">
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-2 text-sm text-slate-500">
                  <Badge className="bg-secondary text-muted-foreground hover:bg-secondary">회원 대시보드</Badge>
                  <span>{pendingTodos > 0 ? `오늘 확인할 항목 ${pendingTodos}건` : "오늘 일정 정리 완료"}</span>
                </div>
                <h1 className="mt-3 text-2xl sm:text-3xl font-black text-slate-950 tracking-normal">
                  {summary?.user.name ?? fallbackName} 님, 오늘은 지원 흐름을 이렇게 가져가면 좋습니다.
                </h1>
                <p className="mt-3 text-sm sm:text-base text-slate-600 leading-7 max-w-3xl">
                  {summary?.focus.description ?? "지원 건을 등록하면 적합도, 면접 준비, 반복 약점이 한 화면에 정리됩니다."}
                </p>
                {summary?.aiSummary && (
                  <div data-tour="home-ai-summary" className="mt-3 max-w-3xl rounded-xl bg-blue-50 px-4 py-3">
                    <div className="flex items-start justify-between gap-3">
                      <p className="text-sm leading-6 text-blue-800">
                        <strong className="font-semibold">AI 요약</strong> <AiResultBadge status={summary.analysisRun.status} /> · {summary.aiSummary}
                      </p>
                      <button
                        type="button"
                        onClick={handleRefreshSummary}
                        disabled={refreshing}
                        title="AI를 다시 실행해 최신 데이터로 요약을 재생성합니다. 크레딧 1이 차감됩니다."
                        className="flex shrink-0 items-center gap-1 rounded-md border border-blue-200 bg-card/70 px-2 py-1 text-xs font-semibold text-blue-700 transition-colors hover:bg-card disabled:opacity-60"
                      >
                        <RefreshCw className={`size-3 ${refreshing ? "animate-spin" : ""}`} />
                        {refreshing ? "재생성 중" : "재생성 (크레딧 1)"}
                      </button>
                    </div>
                    {refreshError && <div className="mt-1.5 text-xs text-red-600">{refreshError}</div>}
                  </div>
                )}
              </div>
              <div className="flex flex-col sm:flex-row lg:flex-col gap-2 shrink-0">
                <Button className="bg-blue-600 hover:bg-blue-700 gap-2" onClick={() => navigate("/applications")}>
                  <Plus className="size-4" />
                  새 지원 건
                </Button>
                <Button variant="outline" className="gap-2" onClick={() => navigate("/analysis")}>
                  <BarChart3 className="size-4" />
                  취업 분석
                </Button>
              </div>
            </div>

            <div className="mt-6 grid sm:grid-cols-[220px_1fr] gap-5">
              <div data-tour="home-readiness" className="rounded-xl bg-card border border-border shadow-[var(--shadow-card)] p-5">
                <div className="text-sm text-muted-foreground">준비도</div>
                <div className="mt-2 flex items-end gap-2">
                  <span className="text-5xl font-black text-primary">{summary?.focus.readiness ?? 0}</span>
                  <span className="pb-2 text-muted-foreground">%</span>
                </div>
                <Progress value={summary?.focus.readiness ?? 0} className="mt-4 h-2 bg-secondary" />
                <div className="mt-4 text-xs text-muted-foreground leading-5">
                  {summary?.focus.headline ?? "분석 가능한 지원 건이 필요합니다."}
                </div>
              </div>

              <div data-tour="home-fit-cards" className="grid sm:grid-cols-3 gap-3">
                {urgentApplications.length > 0 ? urgentApplications.map((application) => (
                  <Link to={`/applications/${application.id}`} key={application.id} className="min-w-0">
                    <div className="h-full rounded-xl border border-slate-200 bg-slate-50 p-4 hover:border-blue-300 hover:bg-blue-50 transition-colors">
                      <div className="flex items-start justify-between gap-3">
                        <div className="min-w-0">
                          <div className="text-sm font-bold text-slate-900 truncate">{application.companyName}</div>
                          <div className="mt-1 text-xs text-slate-500 line-clamp-2">{application.jobTitle}</div>
                        </div>
                        <ChevronRight className="size-4 text-slate-400 shrink-0" />
                      </div>
                      <div className="mt-4 flex items-center justify-between gap-2">
                        <Badge className={`text-xs ${dashboardStatusColor[application.status] ?? "bg-slate-100 text-slate-700"}`}>
                          {dashboardStatusLabel[application.status] ?? application.status}
                        </Badge>
                        <span className="text-sm font-black text-blue-600">{application.fitScore != null ? `${application.fitScore}점` : "미분석"}</span>
                      </div>
                    </div>
                  </Link>
                )) : (
                  <div className="sm:col-span-3 rounded-xl border border-dashed border-slate-300 bg-slate-50 p-5 text-sm text-slate-500">
                    아직 진행 중인 지원 건이 없습니다.
                  </div>
                )}
              </div>
            </div>

            {/* 시작 준비(온보딩) 진행률: 핵심 흐름에서 어디까지 왔는지 보여준다(대시보드 집계 파생). */}
            {summary && (() => {
              const readinessScore = (key: string) =>
                summary.readiness?.components.find((component) => component.key === key)?.score ?? 0;
              const steps = [
                { key: "signup", label: "회원가입", done: true },
                { key: "application", label: "공고 등록", done: (summary.statusCounts?.length ?? 0) > 0 },
                { key: "fit-analysis", label: "적합도 분석", done: readinessScore("analysis") > 0 },
                { key: "learning", label: "학습 과제 완료", done: readinessScore("learning") > 0 },
                { key: "interview", label: "모의면접 연습", done: summary.stats.totalInterviews > 0 },
              ];
              const doneCount = steps.filter((step) => step.done).length;
              if (doneCount === steps.length) return null;
              return (
                <div className="mt-5 rounded-xl border border-slate-200 bg-slate-50 p-4">
                  <div className="flex items-center justify-between text-xs font-semibold text-slate-600">
                    <span>시작 준비 {doneCount}/{steps.length} 완료</span>
                    <span className="text-slate-400">남은 단계를 완료하면 분석 정확도가 올라갑니다</span>
                  </div>
                  <div className="mt-2.5 flex flex-wrap gap-2">
                    {steps.map((step) => (
                      <span
                        key={step.key}
                        className={`inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-xs font-medium ${
                          step.done ? "bg-secondary text-muted-foreground" : "bg-card text-slate-400 border border-slate-200"
                        }`}
                      >
                        {step.done ? "✓" : "○"} {step.label}
                      </span>
                    ))}
                  </div>
                </div>
              );
            })()}
          </div>

          <Card data-tour="home-todos" className="border border-slate-200 bg-card shadow-sm">
            <CardHeader className="pb-3">
              <CardTitle className="text-base flex items-center gap-2">
                <Flame className="size-4 text-muted-foreground" />
                오늘의 우선순위
              </CardTitle>
            </CardHeader>
            <CardContent>
              <TodoChecklist todos={summary?.todos ?? []} onTodosChange={onTodosChange} />
            </CardContent>
          </Card>
        </section>

        <section className="grid grid-cols-2 lg:grid-cols-4 gap-3 sm:gap-4">
          {statCards.map((card) => (
            <Card key={card.label} className="border border-slate-200 bg-card shadow-sm">
              <CardContent className="p-4 sm:p-5">
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <div className="text-xs sm:text-sm text-slate-500">{card.label}</div>
                    <div className="mt-1 text-2xl sm:text-3xl font-black text-slate-950 break-words">{card.value}</div>
                    <div className="mt-1 text-xs text-slate-400 leading-5">{card.sub}</div>
                  </div>
                  <div className="size-10 rounded-xl bg-secondary flex items-center justify-center shrink-0">
                    <card.icon className="size-5 text-muted-foreground" />
                  </div>
                </div>
              </CardContent>
            </Card>
          ))}
        </section>

        <section className="grid lg:grid-cols-[1.45fr_0.85fr] gap-5">
          <div className="space-y-5">
            <Card className="border border-slate-200 bg-card shadow-sm">
              <CardHeader className="pb-3">
                <div className="flex items-center justify-between gap-3">
                  <CardTitle className="text-lg">최근 지원 건</CardTitle>
                  <Link to="/applications" className="text-sm text-blue-600 hover:text-blue-700 flex items-center gap-1 shrink-0">
                    전체 보기 <ArrowRight className="size-3.5" />
                  </Link>
                </div>
              </CardHeader>
              <CardContent className="space-y-3">
                {(summary?.recentApplications ?? []).map((application) => (
                  <Link to={`/applications/${application.id}`} key={application.id} className="block">
                    <div className="rounded-xl border border-slate-200 p-4 hover:border-blue-300 hover:shadow-sm transition-all">
                      <div className="flex flex-col md:flex-row md:items-center gap-4">
                        <div className="flex items-center gap-3 min-w-0 flex-1">
                          <div className="size-11 rounded-xl bg-accent-soft flex items-center justify-center text-primary font-bold shrink-0">
                            {application.companyName[0]}
                          </div>
                          <div className="min-w-0">
                            <div className="flex flex-wrap items-center gap-2">
                              <span className="font-bold text-slate-900">{application.companyName}</span>
                              <span className="text-sm text-slate-500">{application.jobTitle}</span>
                              {application.favorite && <Badge className="bg-secondary text-muted-foreground border border-border">관심</Badge>}
                            </div>
                            <div className="mt-2 flex flex-wrap items-center gap-2">
                              <Badge className={`text-xs ${dashboardStatusColor[application.status] ?? "bg-slate-100 text-slate-700"}`}>
                                {dashboardStatusLabel[application.status] ?? application.status}
                              </Badge>
                              {application.tags.map((tag) => (
                                <span key={tag} className="rounded bg-slate-100 px-2 py-0.5 text-xs text-slate-600">{tag}</span>
                              ))}
                              <span className="flex items-center gap-1 text-xs text-slate-400">
                                <Calendar className="size-3" />
                                {formatDashboardDate(application.postingDate ?? application.updatedAt)}
                              </span>
                            </div>
                          </div>
                        </div>
                        <div className="md:w-48">
                          <div className="flex items-center justify-between text-xs text-slate-500">
                            <span>적합도</span>
                            <span className="font-bold text-blue-600">{application.fitScore != null ? `${application.fitScore}점` : "미분석"}</span>
                          </div>
                          <Progress value={application.fitScore ?? 0} className="mt-2 h-2" />
                        </div>
                      </div>
                    </div>
                  </Link>
                ))}
                {summary?.recentApplications.length === 0 && (
                  <div className="rounded-xl border border-dashed border-slate-300 bg-slate-50 p-5 text-sm text-slate-500">
                    공고를 등록하면 지원 건별 적합도와 면접 준비 현황이 이곳에 쌓입니다.
                  </div>
                )}
              </CardContent>
            </Card>

            <Card className="border border-slate-200 bg-card shadow-sm">
              <CardHeader className="pb-3">
                <CardTitle className="text-lg">최근 활동</CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                {(summary?.activities ?? []).map((activity, index) => {
                  const meta = dashboardActivityMeta(activity.type);
                  return (
                    <div key={`${activity.type}-${activity.occurredAt}-${index}`} className="flex items-start gap-3">
                      <div className={`size-9 rounded-lg ${meta.bg} flex items-center justify-center shrink-0 mt-0.5`}>
                        <meta.icon className={`size-4 ${meta.color}`} />
                      </div>
                      <div className="min-w-0 flex-1">
                        <div className="text-sm text-slate-700 leading-6">{activity.content}</div>
                        <div className="mt-0.5 text-xs text-slate-400">{formatDashboardTime(activity.occurredAt)}</div>
                      </div>
                      {activity.score != null && <Badge className="bg-slate-100 text-slate-700 hover:bg-slate-100">{activity.score}점</Badge>}
                    </div>
                  );
                })}
                {summary?.activities.length === 0 && (
                  <div className="text-sm text-slate-500">아직 기록된 활동이 없습니다.</div>
                )}
              </CardContent>
            </Card>
          </div>

          <div className="space-y-5">
            <Card className="border border-border bg-card shadow-sm">
              <CardHeader className="pb-3">
                <CardTitle className="text-base flex items-center gap-2 text-foreground">
                  <Award className="size-4 text-primary" />
                  크레딧 현황
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="rounded-xl bg-secondary p-4 text-center">
                  <div className="text-4xl font-black text-foreground">{summary?.stats.credit ?? 0}</div>
                  <div className="mt-1 text-sm text-muted-foreground">/ {summary?.stats.creditLimit ?? 0} 크레딧 잔여</div>
                  <Progress value={creditPercent} className="mt-3 h-2" />
                </div>
                <div className="space-y-2 text-xs text-muted-foreground">
                  <div className="flex justify-between gap-3">
                    <span>현재 플랜</span>
                    <span className="font-bold">{summary?.user.plan ?? "Free"}</span>
                  </div>
                  <div className="flex justify-between gap-3">
                    <span>이번 달 사용</span>
                    <span className="font-bold">{summary?.stats.creditsUsedThisMonth ?? 0} 크레딧</span>
                  </div>
                </div>
                <Button size="sm" variant="outline" className="w-full border-border text-foreground" onClick={() => navigate("/pricing")}>
                  크레딧 충전하기
                </Button>
              </CardContent>
            </Card>

            <Card data-tour="home-skillgaps" className="border border-slate-200 bg-card shadow-sm">
              <CardHeader className="pb-3">
                <CardTitle className="text-base">자주 부족한 역량</CardTitle>
              </CardHeader>
              <CardContent className="space-y-3">
                {(summary?.skillGaps ?? []).map((skill) => (
                  <div key={skill.skill} className="space-y-1.5">
                    <div className="flex justify-between gap-3 text-xs">
                      <span className="font-semibold text-slate-700">{skill.skill}</span>
                      <span className="text-slate-400">{skill.count}/{skill.total}건</span>
                    </div>
                    <Progress value={skill.percentage} className="h-2" />
                  </div>
                ))}
                {summary?.skillGaps.length === 0 && (
                  <div className="text-sm text-slate-500">분석 결과가 쌓이면 반복 약점이 표시됩니다.</div>
                )}
              </CardContent>
            </Card>

            <Card className="border border-slate-200 bg-card shadow-sm">
              <CardHeader className="pb-3">
                <CardTitle className="text-base">빠른 이동</CardTitle>
              </CardHeader>
              <CardContent className="grid grid-cols-2 gap-2">
                {[
                  { label: "지원 건", icon: Briefcase, href: "/applications", color: "text-muted-foreground" },
                  { label: "면접", icon: MessageSquare, href: "/interview", color: "text-muted-foreground" },
                  { label: "분석", icon: BarChart3, href: "/analysis", color: "text-muted-foreground" },
                  { label: "커뮤니티", icon: BookOpen, href: "/community", color: "text-muted-foreground" },
                ].map((menu) => (
                  <Link key={menu.label} to={menu.href}>
                    <div className="flex min-h-20 flex-col items-center justify-center gap-2 rounded-xl bg-slate-50 p-3 hover:bg-blue-50 transition-colors">
                      <menu.icon className={`size-5 ${menu.color}`} />
                      <span className="text-xs font-semibold text-slate-700">{menu.label}</span>
                    </div>
                  </Link>
                ))}
              </CardContent>
            </Card>
          </div>
        </section>

        {loading && summary && (
          <div className="fixed bottom-5 right-5 rounded-full border border-slate-200 bg-card px-4 py-2 text-sm text-slate-600 shadow-lg">
            <Loader2 className="mr-2 inline size-4 animate-spin text-blue-600" />
            최신 데이터 동기화 중
          </div>
        )}
      </div>
    </div>
  );
}

export function HomePage() {
  const navigate = useNavigate();
  const { user, loading: authLoading, isAuthenticated } = useAuth();
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [dashboardLoading, setDashboardLoading] = useState(false);
  const [dashboardError, setDashboardError] = useState<string | null>(null);
  const [reloadToken, setReloadToken] = useState(0);

  useEffect(() => {
    if (!isAuthenticated) {
      setSummary(null);
      setDashboardLoading(false);
      setDashboardError(null);
      return;
    }

    let ignore = false;
    setDashboardLoading(true);
    setDashboardError(null);

    getDashboardSummary()
      .then((data) => {
        if (!ignore) setSummary(data);
      })
      .catch((requestError) => {
        if (!ignore) {
          setDashboardError(requestError instanceof Error ? requestError.message : "회원 홈 데이터를 불러오지 못했습니다.");
        }
      })
      .finally(() => {
        if (!ignore) setDashboardLoading(false);
      });

    return () => {
      ignore = true;
    };
  }, [isAuthenticated, reloadToken]);

  if (authLoading) {
    return (
      <div className="min-h-screen bg-slate-50">
        <div className="max-w-[1400px] mx-auto px-4 sm:px-6 py-8">
          <Card className="border border-slate-200 bg-card">
            <CardContent className="flex items-center gap-3 p-6 text-sm text-slate-600">
              <Loader2 className="size-5 animate-spin text-blue-600" />
              세션을 확인하는 중입니다.
            </CardContent>
          </Card>
        </div>
      </div>
    );
  }

  if (isAuthenticated) {
    const memberHomeLoading = dashboardLoading || (!summary && !dashboardError);

    return (
      <MemberHome
        summary={summary}
        loading={memberHomeLoading}
        error={dashboardError}
        fallbackName={user?.name ?? "지원자"}
        onRetry={() => setReloadToken((value) => value + 1)}
        onSummaryRefreshed={(data) => setSummary(data)}
        onTodosChange={(todos) => setSummary((previous) => (previous ? { ...previous, todos } : previous))}
      />
    );
  }

  return (
    <div className="bg-card">
      {/* ─── Hero ─── */}
      <section className="relative overflow-hidden bg-[#0b0c0e] text-white">
        <div className="relative w-full max-w-[1400px] mx-auto px-4 sm:px-6 py-14 lg:py-24">
          <div className="grid lg:grid-cols-2 gap-12 items-center">
            {/* Left text */}
            <div className="space-y-7 min-w-0">
              <div className="flex items-center gap-3">
                <Badge className="bg-blue-500/20 text-[#93c5fd] border-blue-500/30 px-3 py-1 max-w-full whitespace-normal text-left">
                  <Sparkles className="size-3 mr-1.5" />
                  AI 취업 전략 플랫폼 · 2026 NEW
                </Badge>
              </div>
              <h1 className="text-4xl sm:text-5xl xl:text-6xl font-black leading-tight tracking-tight">
                <span className="sm:hidden">
                  채용공고와<br />내 스펙을<br />
                  <span className="text-white">
                    AI가 정밀 분석
                  </span>
                  <br />합격 전략 완성
                </span>
                <span className="hidden sm:inline">
                  채용공고와 내 스펙을<br />
                  <span className="text-white">
                    AI가 정밀 분석
                  </span>
                  <br />합격 전략 완성
                </span>
              </h1>
              <p className="text-base sm:text-lg text-white/80 leading-relaxed max-w-lg">
                <span className="sm:hidden">
                  공고 분석부터 AI 면접까지 <strong className="text-white">하나의 지원 건 공간</strong>에서 관리하세요.
                </span>
                <span className="hidden sm:inline">
                  공고 업로드, 스펙 비교, 예상 질문, AI 면접, 답변 첨삭까지
                  <strong className="text-white"> 하나의 지원 건 공간</strong>에서 관리하세요.
                </span>
              </p>
              <div className="flex flex-col sm:flex-row gap-3">
                <Button
                  size="lg"
                  className="w-full sm:w-auto justify-center bg-primary text-white text-base px-8"
                  onClick={() => navigate("/login")}
                >
                  무료로 시작하기
                  <ArrowRight className="ml-2 size-5" />
                </Button>
                <Button
                  size="lg"
                  variant="outline"
                  className="w-full sm:w-auto justify-center border-white/20 text-white bg-card/10 hover:bg-card/20 text-base px-8"
                  onClick={() => navigate("/applications/demo")}
                >
                  <Play className="mr-2 size-4" />
                  데모 체험하기
                </Button>
              </div>
              <div className="flex flex-col sm:flex-row sm:flex-wrap items-start sm:items-center gap-x-5 gap-y-3 pt-2">
                {[
                  "무료 플랜 제공",
                  "카드 등록 불필요",
                  "즉시 분석 시작",
                ].map((t) => (
                  <div key={t} className="flex items-center gap-1.5 text-sm text-white/80">
                    <CheckCircle2 className="size-4 text-white/50" />
                    {t}
                  </div>
                ))}
              </div>
            </div>

            {/* Right mock UI */}
            <div className="relative hidden lg:block">
              <div className="absolute -inset-4 bg-transparent rounded-3xl blur-2xl" />
              <div className="relative bg-card/10 backdrop-blur-sm border border-white/20 rounded-2xl overflow-hidden shadow-2xl">
                {/* Mock tabs */}
                <div className="flex items-center gap-1 px-4 py-3 bg-card/5 border-b border-white/10 overflow-x-auto">
                  {["공고분석", "기업분석", "스펙비교", "예상질문", "가상면접"].map((t, i) => (
                    <div key={t} className={`px-3 py-1 rounded text-xs font-medium whitespace-nowrap ${i === 2 ? "bg-blue-500 text-white" : "text-white/55"}`}>
                      {t}
                    </div>
                  ))}
                </div>
                <div className="p-5 space-y-4">
                  <div className="flex items-center justify-between">
                    <div>
                      <div className="font-bold text-white text-sm">카카오페이 · 프론트엔드 개발자</div>
                      <div className="text-xs text-white/80 mt-0.5">2026-08-01 공고 · React 3년 이상</div>
                    </div>
                    <Badge className="bg-white/10 text-white/70">분석 완료</Badge>
                  </div>

                  {/* Fit score */}
                  <div className="bg-card/5 rounded-xl p-4 space-y-2">
                    <div className="flex items-center justify-between text-sm">
                      <span className="text-white/80">직무 적합도</span>
                      <span className="font-black text-[#93c5fd] text-lg">72점</span>
                    </div>
                    <div className="h-2 bg-card/10 rounded-full overflow-hidden">
                      <div className="h-full w-[72%] bg-primary rounded-full" />
                    </div>
                  </div>

                  {/* Skills grid */}
                  <div className="grid grid-cols-2 gap-2">
                    {specComparisonData.slice(0, 4).map((s) => (
                      <div key={s.skill} className="flex items-center justify-between bg-card/5 rounded-lg px-3 py-2">
                        <span className="text-xs text-white/80">{s.skill}</span>
                        <span className={`text-[10px] font-semibold px-1.5 py-0.5 rounded ${s.color}`}>
                          {s.grade}
                        </span>
                      </div>
                    ))}
                  </div>

                  {/* Interview question preview */}
                  <div className="bg-card/5 rounded-xl p-3">
                    <div className="text-xs text-white/80 mb-2 font-medium">AI 예상 질문 (직무)</div>
                    <div className="text-xs text-white/80 leading-relaxed">
                      "React에서 상태 관리를 어떻게 설계하나요? Recoil과 Zustand를 비교 설명해주세요."
                    </div>
                  </div>

                  <Button className="w-full bg-primary text-sm">
                    <MessageSquare className="mr-2 size-4" />
                    AI 가상 면접 시작하기
                  </Button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* ─── AI 오케스트레이터 면접 진입 검색창 ─── */}
      <section className="bg-card">
        <div className="mx-auto w-full max-w-[1400px] px-4 sm:px-6 pt-8">
          <InterviewHero />
        </div>
      </section>

      {/* ─── Stats bar ─── */}
      <section className="bg-card border-b border-slate-100">
        <div className="w-full max-w-[1400px] mx-auto px-4 sm:px-6 py-8">
          <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-5 lg:gap-12">
            {[
              { icon: Users, value: "10,000+", label: "활성 사용자", sub: "매월 신규 2,000명" },
              { icon: MessageSquare, value: "50,000+", label: "AI 가상 면접", sub: "평균 만족도 4.8/5" },
              { icon: FileText, value: "30,000+", label: "공고 분석 건수", sub: "평균 분석 시간 30초" },
              { icon: TrendingUp, value: "92%", label: "면접 준비도 향상", sub: "3주 사용 후 기준" },
            ].map((s) => (
              <div key={s.label} className="flex items-center gap-4 min-w-0">
                <div className="size-12 rounded-xl bg-accent-soft flex items-center justify-center flex-shrink-0">
                  <s.icon className="size-6 text-primary" />
                </div>
                <div className="min-w-0">
                  <div className="text-xl sm:text-2xl font-black text-foreground">
                    {s.value}
                  </div>
                  <div className="font-semibold text-slate-800 text-sm">{s.label}</div>
                  <div className="text-xs text-slate-500">{s.sub}</div>
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ─── Core Features ─── */}
      <section id="features" className="py-20 bg-slate-50">
        <div className="w-full max-w-[1400px] mx-auto px-6">
          <div className="text-center mb-14 space-y-3">
            <Badge className="bg-secondary text-muted-foreground px-4 py-1">핵심 기능</Badge>
            <h2 className="text-4xl font-black text-slate-900">AI가 제공하는 8가지 취업 솔루션</h2>
            <p className="text-lg text-slate-500 max-w-2xl mx-auto">
              공고 분석부터 장기 취업 전략까지, 취업 준비의 전 과정을 AI가 지원합니다
            </p>
          </div>

          <div className="grid md:grid-cols-2 lg:grid-cols-4 gap-5">
            {coreFeaturesData.map((f, i) => (
              <Card key={i} className="border border-slate-200 hover:border-blue-300 hover:shadow-lg transition-all duration-300 bg-card group">
                <CardHeader className="pb-3">
                  <div className="flex items-start justify-between mb-3">
                    <div className="size-11 rounded-xl bg-accent-soft flex items-center justify-center shadow-md group-hover:scale-110 transition-transform">
                      <f.icon className="size-5 text-primary" />
                    </div>
                    <Badge className="text-xs bg-slate-100 text-slate-600 border-slate-200">{f.badge}</Badge>
                  </div>
                  <CardTitle className="text-base">{f.title}</CardTitle>
                </CardHeader>
                <CardContent>
                  <p className="text-sm text-slate-500 leading-relaxed">{f.desc}</p>
                </CardContent>
              </Card>
            ))}
          </div>
        </div>
      </section>

      {/* ─── How It Works (Full 12-step flow) ─── */}
      <section id="how-it-works" className="py-20 bg-card">
        <div className="w-full max-w-[1400px] mx-auto px-6">
          <div className="text-center mb-14 space-y-3">
            <Badge className="bg-secondary text-muted-foreground px-4 py-1">사용 방법</Badge>
            <h2 className="text-4xl font-black text-slate-900">12단계 AI 취업 준비 프로세스</h2>
            <p className="text-lg text-slate-500 max-w-2xl mx-auto">
              회원가입부터 장기 전략 수립까지, 취업 준비의 모든 단계를 하나의 흐름으로
            </p>
          </div>

          <div className="grid md:grid-cols-3 lg:grid-cols-4 gap-4">
            {flowSteps.map((s, i) => (
              <div
                key={i}
                className="relative flex gap-3 p-4 rounded-xl bg-slate-50 border border-slate-200 hover:border-blue-300 hover:bg-blue-50 transition-all group"
              >
                <div className="size-9 rounded-lg bg-accent-soft text-primary text-xs font-black flex items-center justify-center flex-shrink-0 group-hover:scale-110 transition-transform">
                  {s.step}
                </div>
                <div>
                  <div className="font-semibold text-slate-800 text-sm mb-0.5">{s.title}</div>
                  <div className="text-xs text-slate-500 leading-relaxed">{s.desc}</div>
                </div>
                {i < flowSteps.length - 1 && (
                  <ChevronRight className="absolute -right-2 top-1/2 -translate-y-1/2 size-4 text-blue-400 hidden lg:block" />
                )}
              </div>
            ))}
          </div>

          <div className="mt-10 text-center">
            <Button
              size="lg"
              className="bg-primary text-base px-10"
              onClick={() => window.location.href = "/login"}
            >
              지금 바로 시작하기
              <ArrowRight className="ml-2 size-5" />
            </Button>
          </div>
        </div>
      </section>

      {/* ─── Application Detail Demo (3-column layout preview) ─── */}
      <section className="py-20 bg-muted">
        <div className="w-full max-w-[1400px] mx-auto px-6">
          <div className="text-center mb-14 space-y-3">
            <Badge className="bg-secondary text-muted-foreground px-4 py-1">지원 건 관리</Badge>
            <h2 className="text-4xl font-black text-slate-900">지원 건마다 독립된 AI 공간</h2>
            <p className="text-lg text-slate-500 max-w-2xl mx-auto">
              ChatGPT 세션처럼 기업별·공고별로 분리 관리. 탭 8개에 모든 준비 정보가 담깁니다.
            </p>
          </div>

          {/* Mock 3-column layout */}
          <div className="bg-card rounded-2xl shadow-2xl border border-slate-200 overflow-hidden">
            <div className="flex h-[480px]">
              {/* Left sidebar */}
              <div className="w-56 bg-secondary text-foreground flex-shrink-0 flex flex-col">
                <div className="p-4 border-b border-slate-700">
                  <div className="text-xs text-slate-400 font-semibold uppercase tracking-wide mb-3">지원 건 목록</div>
                  {[
                    { co: "카카오페이", job: "프론트엔드", active: true, score: 72 },
                    { co: "네이버", job: "백엔드 개발", active: false, score: 58 },
                    { co: "삼성SDS", job: "IT 솔루션", active: false, score: 65 },
                    { co: "라인", job: "풀스택 개발", active: false, score: 44 },
                  ].map((item) => (
                    <div key={item.co} className={`mb-2 p-2.5 rounded-lg cursor-pointer transition-colors ${item.active ? "bg-primary text-primary-foreground" : "hover:bg-accent"}`}>
                      <div className="text-xs font-semibold">{item.co}</div>
                      <div className="text-[10px] text-slate-400">{item.job}</div>
                      <div className="text-[10px] mt-1 text-blue-300">적합도 {item.score}점</div>
                    </div>
                  ))}
                </div>
                <div className="p-4">
                  <div className="text-xs text-slate-400 font-semibold uppercase tracking-wide mb-2">즐겨찾기</div>
                  <div className="text-xs text-slate-500">라인 · 풀스택</div>
                </div>
              </div>

              {/* Center main */}
              <div className="flex-1 flex flex-col min-w-0">
                <div className="flex items-center justify-between px-5 py-3 border-b border-slate-200 bg-card">
                  <div>
                    <div className="font-bold text-slate-900 text-sm">카카오페이 · 프론트엔드 개발자</div>
                    <div className="text-xs text-slate-500">2026-08-01 공고 · React 3년 이상</div>
                  </div>
                  <Badge className="bg-secondary text-muted-foreground text-xs">준비중</Badge>
                </div>
                <div className="flex border-b border-slate-200 bg-card overflow-x-auto">
                  {["공고분석", "기업분석", "스펙비교", "지원전략", "예상질문", "가상면접", "면접리포트", "첨삭기록"].map((tab, i) => (
                    <button
                      key={tab}
                      className={`px-4 py-2.5 text-xs font-medium whitespace-nowrap border-b-2 transition-colors ${
                        i === 2
                          ? "border-blue-600 text-blue-600 bg-blue-50"
                          : "border-transparent text-slate-600 hover:text-blue-600"
                      }`}
                    >
                      {tab}
                    </button>
                  ))}
                </div>
                <div className="flex-1 overflow-y-auto p-5 space-y-4">
                  <div className="flex items-center justify-between">
                    <h3 className="font-bold text-slate-800 text-sm">내 스펙 비교 분석</h3>
                    <Badge className="text-xs bg-secondary text-muted-foreground">직무 적합도 72점</Badge>
                  </div>
                  <div className="space-y-2">
                    {specComparisonData.map((s) => (
                      <div key={s.skill} className="flex items-center gap-3 p-2 rounded-lg bg-slate-50 text-xs">
                        <div className="w-20 font-medium text-slate-700">{s.skill}</div>
                        <div className="flex-1 text-slate-500">{s.status}</div>
                        <span className={`px-2 py-0.5 rounded-full text-[10px] font-semibold ${s.color}`}>{s.grade}</span>
                      </div>
                    ))}
                  </div>
                  <div className="bg-card border border-border rounded-xl p-3 text-xs">
                    <div className="font-semibold text-foreground mb-1">AI 준비 전략</div>
                    <div className="text-muted-foreground space-y-1">
                      <p>1. TypeScript 기본 문법과 React 프로젝트 적용 사례 학습</p>
                      <p>2. AWS S3/CloudFront 배포 경험 토이 프로젝트로 보완</p>
                      <p>3. 포트폴리오에 문제 해결 사례와 수치 결과 추가</p>
                    </div>
                  </div>
                </div>
              </div>

              {/* Right panel */}
              <div className="w-56 bg-slate-50 border-l border-slate-200 flex-shrink-0 flex flex-col">
                <div className="p-4 space-y-4">
                  <div>
                    <div className="text-xs text-slate-500 font-semibold uppercase tracking-wide mb-2">준비도 점수</div>
                    <div className="text-center py-3">
                      <div className="text-4xl font-black text-blue-600">72</div>
                      <div className="text-xs text-slate-500">/ 100점</div>
                      <Progress value={72} className="mt-2 h-2" />
                    </div>
                  </div>
                  <div>
                    <div className="text-xs text-slate-500 font-semibold uppercase tracking-wide mb-2">부족 역량</div>
                    <div className="space-y-1.5">
                      {["TypeScript", "AWS 배포", "포트폴리오"].map((s) => (
                        <div key={s} className="flex items-center gap-1.5 text-xs bg-secondary text-muted-foreground px-2 py-1 rounded">
                          <AlertCircle className="size-3" /> {s}
                        </div>
                      ))}
                    </div>
                  </div>
                  <div>
                    <div className="text-xs text-slate-500 font-semibold uppercase tracking-wide mb-2">다음 할 일</div>
                    <div className="space-y-1.5">
                      {["TypeScript 학습 시작", "AWS 토이 프로젝트", "포트폴리오 수정"].map((t) => (
                        <div key={t} className="text-xs text-slate-600 flex items-start gap-1.5">
                          <div className="size-3 rounded-sm border border-slate-300 flex-shrink-0 mt-0.5" />
                          {t}
                        </div>
                      ))}
                    </div>
                  </div>
                  <div className="bg-card rounded-xl p-3 border border-slate-200">
                    <div className="text-xs text-slate-500 mb-1">크레딧 사용</div>
                    <div className="text-lg font-black text-slate-800">38 / 50</div>
                    <Progress value={76} className="mt-1 h-1.5" />
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* ─── Interview Modes ─── */}
      <section className="py-20 bg-card">
        <div className="w-full max-w-[1400px] mx-auto px-6">
          <div className="text-center mb-14 space-y-3">
            <Badge className="bg-secondary text-muted-foreground border border-border px-4 py-1">AI 가상 면접</Badge>
            <h2 className="text-4xl font-black text-slate-900">8가지 면접 모드로 실전 완벽 대비</h2>
            <p className="text-lg text-slate-500 max-w-2xl mx-auto">
              기본 면접부터 압박 면접, 기업 맞춤 면접까지 — 모든 상황을 연습할 수 있습니다
            </p>
          </div>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            {interviewModes.map((m) => (
              <Card key={m.title} className={`border ${m.color} hover:shadow-md transition-shadow cursor-pointer group`}>
                <CardContent className="p-5 text-center space-y-2">
                  <m.icon className="size-6 text-muted-foreground mx-auto group-hover:scale-110 transition-transform" />
                  <div className="font-bold text-slate-800 text-sm">{m.title}</div>
                  <div className="text-xs text-slate-500">{m.desc}</div>
                </CardContent>
              </Card>
            ))}
          </div>

          {/* Answer correction demo */}
          <div className="mt-12 grid md:grid-cols-2 gap-6">
            <Card className="border border-border bg-card">
              <CardHeader className="pb-2">
                <CardTitle className="text-sm flex items-center gap-2 text-destructive">
                  <AlertCircle className="size-4" /> 사용자 원답변
                </CardTitle>
              </CardHeader>
              <CardContent>
                <div className="text-sm text-slate-600 bg-secondary rounded-lg p-3 border border-border">
                  "학교 프로젝트에서 React를 사용해서 게시판을 만들었습니다."
                </div>
                <div className="mt-3 space-y-1">
                  <div className="text-xs text-destructive flex items-start gap-1.5"><AlertCircle className="size-3 mt-0.5 flex-shrink-0" /> 역할이 불명확</div>
                  <div className="text-xs text-destructive flex items-start gap-1.5"><AlertCircle className="size-3 mt-0.5 flex-shrink-0" /> 구체적 기능 설명 없음</div>
                  <div className="text-xs text-destructive flex items-start gap-1.5"><AlertCircle className="size-3 mt-0.5 flex-shrink-0" /> 문제 해결 경험 없음</div>
                </div>
              </CardContent>
            </Card>
            <Card className="border border-border bg-card">
              <CardHeader className="pb-2">
                <CardTitle className="text-sm flex items-center gap-2 text-green-600">
                  <ThumbsUp className="size-4" /> AI 개선 답변
                </CardTitle>
              </CardHeader>
              <CardContent>
                <div className="text-sm text-slate-600 bg-secondary rounded-lg p-3 border border-border leading-relaxed">
                  "팀 프로젝트에서 React로 게시판 기능을 구현했습니다. 저는 목록/작성/수정/삭제 화면을 맡았고, REST API 연동과 useState/useEffect 기반 상태 관리를 구현했습니다. 입력값 검증을 추가해 데이터 무결성을 확보한 점이 좋은 평가를 받았습니다."
                </div>
                <div className="mt-3 space-y-1">
                  <div className="text-xs text-green-600 flex items-start gap-1.5"><CheckCircle2 className="size-3 mt-0.5 flex-shrink-0" /> 역할 명확</div>
                  <div className="text-xs text-green-600 flex items-start gap-1.5"><CheckCircle2 className="size-3 mt-0.5 flex-shrink-0" /> 기술 스택 구체적</div>
                  <div className="text-xs text-green-600 flex items-start gap-1.5"><CheckCircle2 className="size-3 mt-0.5 flex-shrink-0" /> 문제 해결 + 결과 포함</div>
                </div>
              </CardContent>
            </Card>
          </div>
        </div>
      </section>

      {/* ─── Comparison ─── */}
      <section className="py-20 bg-muted">
        <div className="w-full max-w-[1400px] mx-auto px-6">
          <div className="text-center mb-14 space-y-3">
            <Badge className="bg-slate-200 text-slate-700 px-4 py-1">왜 다른가요?</Badge>
            <h2 className="text-4xl font-black text-slate-900">일반 채용 사이트와 비교</h2>
          </div>
          <div className="max-w-4xl mx-auto grid md:grid-cols-2 gap-6">
            <Card className="border-2 border-slate-200 bg-card">
              <CardHeader>
                <CardTitle className="flex items-center gap-2 text-slate-700">
                  <div className="size-8 rounded-lg bg-slate-200 flex items-center justify-center">
                    <Search className="size-4 text-slate-600" />
                  </div>
                  일반 채용 플랫폼 (사람인, 잡코리아 등)
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-2.5">
                {[
                  "공고 검색 및 나열",
                  "이력서 등록 및 지원 접수",
                  "합격/불합격 결과 대기",
                  "제한적인 취업 정보 제공",
                  "면접 준비는 개인 몫",
                ].map((t) => (
                  <div key={t} className="flex items-center gap-2 text-slate-500 text-sm">
                    <div className="size-4 rounded-full bg-slate-200 flex items-center justify-center">
                      <div className="size-1.5 bg-slate-400 rounded-full" />
                    </div>
                    {t}
                  </div>
                ))}
              </CardContent>
            </Card>
            <Card className="border-2 border-blue-400 shadow-xl bg-card">
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <div className="size-8 rounded-lg bg-accent-soft flex items-center justify-center">
                    <Sparkles className="size-4 text-primary" />
                  </div>
                  CareerTuner
                  <Badge className="ml-auto bg-blue-600 text-white">추천</Badge>
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-2.5">
                {[
                  "공고 요구사항 AI 자동 분석",
                  "내 스펙과 1:1 실시간 비교 진단",
                  "8가지 모드 AI 가상 면접 연습",
                  "답변 첨삭 + 즉각 개선 방향 제시",
                  "여러 지원 건 종합 장기 전략 수립",
                ].map((t) => (
                  <div key={t} className="flex items-center gap-2 text-slate-800 font-medium text-sm">
                    <CheckCircle2 className="size-4 text-green-600 flex-shrink-0" />
                    {t}
                  </div>
                ))}
              </CardContent>
            </Card>
          </div>
        </div>
      </section>

      {/* ─── Pricing ─── */}
      <section id="pricing" className="py-20 bg-card">
        <div className="w-full max-w-[1400px] mx-auto px-6">
          <div className="text-center mb-14 space-y-3">
            <Badge className="bg-secondary text-muted-foreground border border-border px-4 py-1">요금제</Badge>
            <h2 className="text-4xl font-black text-slate-900">나에게 맞는 플랜 선택</h2>
            <p className="text-lg text-slate-500">무료 플랜으로 시작하고, 필요할 때 업그레이드하세요</p>
          </div>

          <Tabs defaultValue="subscription" className="w-full">
            <div className="flex justify-center mb-10">
              <TabsList className="bg-slate-100">
                <TabsTrigger value="subscription">월 구독형</TabsTrigger>
                <TabsTrigger value="credits">크레딧형</TabsTrigger>
              </TabsList>
            </div>

            <TabsContent value="subscription">
              <div className="grid md:grid-cols-2 lg:grid-cols-4 gap-5 max-w-6xl mx-auto">
                {[
                  {
                    name: "무료",
                    price: "0원",
                    period: "영구 무료",
                    badge: null,
                    features: ["공고 분석 월 3회", "기본 예상 질문 생성", "텍스트 면접 1회", "기본 분석 리포트"],
                    highlighted: false,
                    btnText: "무료 시작",
                  },
                  {
                    name: "베이직",
                    price: "9,900원",
                    period: "월",
                    badge: null,
                    features: ["공고 분석 월 20회", "예상 질문 생성 무제한", "텍스트 모의면접 무제한", "기본 답변 첨삭", "분석 리포트 저장"],
                    highlighted: false,
                    btnText: "시작하기",
                  },
                  {
                    name: "프로",
                    price: "29,000원",
                    period: "월",
                    badge: "인기",
                    features: ["공고 분석 무제한", "음성 AI 면접", "기업 현황 조사", "고급 면접 리포트", "장기 취업 경향 분석", "아바타 면접관"],
                    highlighted: true,
                    btnText: "시작하기",
                  },
                  {
                    name: "프리미엄",
                    price: "49,000원",
                    period: "월",
                    badge: null,
                    features: ["프로 플랜 모든 기능", "영상 표정/자세 분석", "자기소개서 고급 첨삭", "포트폴리오 첨삭", "1:1 전략 컨설팅", "전담 지원"],
                    highlighted: false,
                    btnText: "시작하기",
                  },
                ].map((plan) => (
                  <Card key={plan.name} className={`relative border-2 ${plan.highlighted ? "border-blue-500 shadow-2xl scale-105" : "border-slate-200"}`}>
                    {plan.badge && (
                      <div className="absolute -top-3 left-1/2 -translate-x-1/2">
                        <Badge className="bg-primary text-white px-4">
                          {plan.badge}
                        </Badge>
                      </div>
                    )}
                    <CardHeader className="text-center pt-8">
                      <CardTitle className="text-xl">{plan.name} 플랜</CardTitle>
                      <div>
                        <div className="text-3xl font-black mt-2">{plan.price}</div>
                        <div className="text-slate-500 text-sm">/{plan.period}</div>
                      </div>
                    </CardHeader>
                    <CardContent className="space-y-5">
                      <div className="space-y-2">
                        {plan.features.map((f) => (
                          <div key={f} className="flex items-start gap-2 text-sm">
                            <CheckCircle2 className="size-4 text-green-600 flex-shrink-0 mt-0.5" />
                            {f}
                          </div>
                        ))}
                      </div>
                      <Button
                        className={`w-full ${plan.highlighted ? "bg-primary" : ""}`}
                        variant={plan.highlighted ? "default" : "outline"}
                        onClick={() => navigate("/login")}
                      >
                        {plan.btnText}
                      </Button>
                    </CardContent>
                  </Card>
                ))}
              </div>
            </TabsContent>

            <TabsContent value="credits">
              <div className="max-w-3xl mx-auto">
                <Card className="border-2 border-slate-200">
                  <CardContent className="p-8">
                    <div className="text-center mb-8">
                      <div className="text-lg font-bold text-slate-800 mb-2">기능별 크레딧 소모량</div>
                      <p className="text-slate-500 text-sm">필요한 기능만 사용하는 크레딧 방식. 초기 가입 시 무료 크레딧 10개 제공</p>
                    </div>
                    <div className="grid md:grid-cols-2 gap-3">
                      {[
                        { feature: "공고문 분석", credit: 1, icon: FileText },
                        { feature: "기업 현황 조사", credit: 2, icon: Building2 },
                        { feature: "예상 질문 생성", credit: 1, icon: Brain },
                        { feature: "텍스트 모의면접", credit: 2, icon: MessageSquare },
                        { feature: "음성 모의면접", credit: 3, icon: Mic },
                        { feature: "영상/자세 분석 면접", credit: 5, icon: Video },
                        { feature: "자기소개서 첨삭", credit: 2, icon: PenTool },
                        { feature: "전체 전략 리포트", credit: 3, icon: BarChart3 },
                      ].map((item) => (
                        <div key={item.feature} className="flex items-center justify-between p-3 rounded-xl bg-slate-50 border border-slate-200">
                          <div className="flex items-center gap-2">
                            <item.icon className="size-4 text-blue-600" />
                            <span className="text-sm font-medium text-slate-700">{item.feature}</span>
                          </div>
                          <div className="flex items-center gap-1">
                            <Award className="size-3.5 text-amber-500" />
                            <span className="font-black text-amber-600 text-sm">{item.credit}</span>
                          </div>
                        </div>
                      ))}
                    </div>
                    <div className="mt-6 grid grid-cols-3 gap-3">
                      {[
                        { amount: "10 크레딧", price: "4,900원" },
                        { amount: "30 크레딧", price: "11,900원" },
                        { amount: "100 크레딧", price: "29,000원" },
                      ].map((p) => (
                        <Button key={p.amount} variant="outline" className="flex flex-col h-auto py-3" onClick={() => navigate("/pricing")}>
                          <span className="font-black text-slate-800">{p.amount}</span>
                          <span className="text-xs text-slate-500">{p.price}</span>
                        </Button>
                      ))}
                    </div>
                  </CardContent>
                </Card>
              </div>
            </TabsContent>
          </Tabs>
        </div>
      </section>

      {/* ─── Community Preview ─── */}
      <section className="py-20 bg-slate-50">
        <div className="w-full max-w-[1400px] mx-auto px-6">
          <div className="flex items-end justify-between mb-10">
            <div className="space-y-2">
              <Badge className="bg-secondary text-muted-foreground border border-border px-4 py-1">커뮤니티</Badge>
              <h2 className="text-4xl font-black text-slate-900">합격자들의 생생한 후기</h2>
              <p className="text-slate-500">취업 후기, 면접 후기, 직무별 질문 공유 게시판</p>
            </div>
            <Link to="/community">
              <Button variant="outline" className="gap-1.5">
                전체 보기 <ArrowRight className="size-4" />
              </Button>
            </Link>
          </div>

          <div className="space-y-3">
            {communityPosts.map((post, i) => (
              <Link to="/community" key={i}>
                <div className="bg-card border border-slate-200 hover:border-blue-300 hover:shadow-md transition-all rounded-xl p-4 flex items-center gap-4 group">
                  <Badge className="flex-shrink-0 text-xs bg-secondary text-muted-foreground">{post.cat}</Badge>
                  {post.hot && <Badge className="flex-shrink-0 text-xs bg-secondary text-muted-foreground">HOT</Badge>}
                  <div className="flex-1 min-w-0">
                    <div className="font-semibold text-slate-800 text-sm truncate group-hover:text-blue-600 transition-colors">{post.title}</div>
                    <div className="text-xs text-slate-400 mt-0.5">{post.company !== "익명" ? post.company + " · " : ""}{post.job}</div>
                  </div>
                  <div className="flex items-center gap-4 text-xs text-slate-400 flex-shrink-0">
                    <span>조회 {post.views.toLocaleString()}</span>
                    <span>좋아요 {post.likes}</span>
                  </div>
                </div>
              </Link>
            ))}
          </div>
        </div>
      </section>

      {/* ─── Testimonials ─── */}
      <section className="py-20 bg-card">
        <div className="w-full max-w-[1400px] mx-auto px-6">
          <div className="text-center mb-14 space-y-3">
            <Badge className="bg-secondary text-muted-foreground border border-border px-4 py-1">사용자 후기</Badge>
            <h2 className="text-4xl font-black text-slate-900">실제 합격자들의 이야기</h2>
          </div>
          <div className="grid md:grid-cols-3 gap-6">
            {testimonials.map((t, i) => (
              <Card key={i} className="border-2 border-slate-200 bg-card hover:border-blue-200 hover:shadow-lg transition-all">
                <CardContent className="p-6 space-y-4">
                  <div className="flex items-center gap-3">
                    <div className="size-11 rounded-full bg-accent-soft flex items-center justify-center text-primary font-bold text-base">
                      {t.avatar}
                    </div>
                    <div>
                      <div className="font-bold text-slate-800 text-sm">{t.name}</div>
                      <div className="text-xs text-green-600 font-semibold">{t.job}</div>
                      <div className="flex items-center gap-0.5 mt-0.5">
                        {Array(5).fill(0).map((_, j) => (
                          <Star key={j} className="size-3 fill-amber-400 text-amber-400" />
                        ))}
                      </div>
                    </div>
                    <Badge className="ml-auto text-xs bg-secondary text-muted-foreground">{t.plan}</Badge>
                  </div>
                  <p className="text-sm text-slate-600 leading-relaxed">"{t.text}"</p>
                </CardContent>
              </Card>
            ))}
          </div>
        </div>
      </section>

      {/* ─── CTA ─── */}
      <section className="py-20 bg-[#0b0c0e] text-white">
        <div className="w-full max-w-[1400px] mx-auto px-6 text-center space-y-8">
          <div className="space-y-4">
            <h2 className="text-4xl lg:text-5xl font-black">
              오늘부터 AI와 함께<br />면접 준비를 시작하세요
            </h2>
            <p className="text-xl text-blue-200 max-w-2xl mx-auto">
              무료 플랜으로 시작해서 AI 취업 전략의 효과를 직접 경험해보세요.<br />
              매달 수천 명이 CareerTuner으로 합격하고 있습니다.
            </p>
          </div>
          <div className="flex flex-col sm:flex-row gap-4 justify-center">
            <Button size="lg" className="bg-card text-blue-700 hover:bg-blue-50 text-base px-10 shadow-xl" onClick={() => navigate("/login")}>
              무료로 시작하기
              <ArrowRight className="ml-2 size-5" />
            </Button>
            <Button size="lg" variant="outline" className="border-white/40 bg-transparent text-white hover:bg-card/10 text-base px-10" onClick={() => navigate("/pricing")}>
              요금제 비교하기
            </Button>
          </div>
          <div className="flex flex-wrap items-center justify-center gap-8 pt-4 text-sm text-blue-200">
            <div className="flex items-center gap-2"><CheckCircle2 className="size-4 text-green-400" /> 무료 플랜 영구 제공</div>
            <div className="flex items-center gap-2"><CheckCircle2 className="size-4 text-green-400" /> 카드 등록 불필요</div>
            <div className="flex items-center gap-2"><CheckCircle2 className="size-4 text-green-400" /> 언제든지 플랜 변경 가능</div>
            <div className="flex items-center gap-2"><Shield className="size-4 text-green-400" /> 개인정보 안전 보호</div>
          </div>
        </div>
      </section>
    </div>
  );
}
