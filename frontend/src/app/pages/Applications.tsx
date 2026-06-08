import { useEffect, useState } from "react";
import { Link, useLocation, useNavigate, useSearchParams } from "react-router";
import type { LucideIcon } from "lucide-react";
import { Button } from "../components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "../components/ui/card";
import { Badge } from "../components/ui/badge";
import { Progress } from "../components/ui/progress";
import { Input } from "../components/ui/input";
import {
  Plus, Search, Filter, Calendar, ChevronRight, FileText,
  Briefcase, Clock, Star, Archive, MoreHorizontal, Building2,
  SortAsc, Upload, BarChart3, Target, Map, GraduationCap, ClipboardList,
} from "lucide-react";
import { getFitAnalyses } from "@/features/analysis/api/fitAnalysisApi";
import type { FitAnalysisDetail } from "@/features/analysis/types/fitAnalysis";
import { FitAnalysisPanel } from "@/features/applications/components/FitAnalysisPanel";
import { LearningRecommendationPanel } from "@/features/applications/components/LearningRecommendationPanel";
import { StrategyPanel } from "@/features/applications/components/StrategyPanel";

const applications = [
  { id: "1", company: "카카오페이", job: "프론트엔드 개발자", date: "2026-08-01", score: 72, status: "준비중", phase: "스펙비교완료", tags: ["React", "TypeScript", "AWS"], starred: true, desc: "결제 플랫폼 개발 · 경력 1-3년" },
  { id: "2", company: "네이버", job: "백엔드 개발자", date: "2026-07-20", score: 58, status: "면접연습중", phase: "가상면접진행", tags: ["Java", "Spring", "MySQL"], starred: true, desc: "검색 인프라 개발 · 신입/경력" },
  { id: "3", company: "삼성SDS", job: "IT 솔루션 개발", date: "2026-07-15", score: 65, status: "분석완료", phase: "전략수립완료", tags: ["Java", "Oracle", "Linux"], starred: false, desc: "ERP/SCM 시스템 개발 · 신입" },
  { id: "4", company: "라인플러스", job: "풀스택 개발자", date: "2026-07-10", score: 44, status: "공고입력", phase: "공고분석필요", tags: ["Node.js", "React", "MongoDB"], starred: false, desc: "글로벌 서비스 개발 · 경력 3년+" },
  { id: "5", company: "토스", job: "iOS 개발자", date: "2026-07-05", score: 35, status: "분석중", phase: "기업조사중", tags: ["Swift", "iOS", "RxSwift"], starred: false, desc: "핀테크 앱 개발 · 신입/경력" },
  { id: "6", company: "당근", job: "안드로이드 개발자", date: "2026-06-30", score: 28, status: "공고수집", phase: "공고입력대기", tags: ["Kotlin", "Android", "Jetpack"], starred: false, desc: "로컬 커머스 서비스 · 경력 2년+" },
];

const statusOptions = ["전체", "공고입력", "분석완료", "준비중", "면접연습중", "보관함"];

const applicationTabs = ["overview", "new", "upload", "analysis", "fit", "strategy", "learning", "records"] as const;
type ApplicationTab = (typeof applicationTabs)[number];

const applicationNav: { key: ApplicationTab; label: string; icon: LucideIcon }[] = [
  { key: "overview", label: "전체 지원 건 목록", icon: Briefcase },
  { key: "new", label: "새 지원 건 만들기", icon: Plus },
  { key: "upload", label: "공고문 업로드", icon: Upload },
  { key: "analysis", label: "공고문 분석 결과", icon: BarChart3 },
  { key: "fit", label: "내 스펙과 비교", icon: Target },
  { key: "strategy", label: "지원 전략", icon: Map },
  { key: "learning", label: "학습/자격증 추천", icon: GraduationCap },
  { key: "records", label: "지원 건별 기록", icon: ClipboardList },
];

const sectionCopy: Record<Exclude<ApplicationTab, "overview">, { title: string; desc: string; bullets: string[]; cta: string }> = {
  new: {
    title: "새 지원 건 만들기",
    desc: "기업명, 직무명, 마감일, 채용공고 원문을 등록해 하나의 지원 건으로 관리합니다.",
    bullets: ["기업/직무 기본 정보 입력", "지원 단계와 마감일 설정", "이력서·자기소개서 연결", "AI 분석 대기열 등록"],
    cta: "지원 건 생성",
  },
  upload: {
    title: "공고문 업로드",
    desc: "채용공고 URL, 텍스트, PDF를 입력하고 AI 분석을 시작하는 영역입니다.",
    bullets: ["공고 URL 붙여넣기", "공고문 텍스트 직접 입력", "PDF/DOCX 업로드", "공고 원문 버전 관리"],
    cta: "공고문 업로드",
  },
  analysis: {
    title: "공고문 분석 결과",
    desc: "공고에서 직무, 경력, 기술스택, 우대사항, 난이도를 구조화해 보여줍니다.",
    bullets: ["필수/우대 역량 분리", "주요 업무와 평가 포인트 요약", "채용 난이도 추정", "면접 예상 방향 도출"],
    cta: "분석 결과 보기",
  },
  fit: {
    title: "내 스펙과 비교",
    desc: "내 프로필과 공고 요구사항을 비교해 강점과 부족 역량을 표시합니다.",
    bullets: ["보유 기술 매칭", "부족 기술 표시", "프로젝트/경력 연결", "직무 적합도 점수화"],
    cta: "스펙 비교 실행",
  },
  strategy: {
    title: "지원 전략",
    desc: "지원서, 포트폴리오, 면접에서 무엇을 강조할지 단계별 전략을 제공합니다.",
    bullets: ["단기 보완 과제", "자기소개서 강조 포인트", "면접 답변 방향", "기업 맞춤 지원 메시지"],
    cta: "전략 생성",
  },
  learning: {
    title: "학습/자격증 추천",
    desc: "부족 역량에 맞는 학습 로드맵과 자격증, 프로젝트 과제를 추천합니다.",
    bullets: ["기술별 학습 순서", "추천 강의/자료", "자격증 우선순위", "포트폴리오 보완 과제"],
    cta: "추천 로드맵 보기",
  },
  records: {
    title: "지원 건별 기록",
    desc: "지원 건마다 분석, 첨삭, 면접, 제출 기록을 타임라인으로 남깁니다.",
    bullets: ["공고 변경 이력", "AI 분석 기록", "면접 연습 기록", "지원서 제출/결과 메모"],
    cta: "기록 추가",
  },
};

function ApplicationStructurePanel({ section }: { section: Exclude<ApplicationTab, "overview"> }) {
  const copy = sectionCopy[section];
  return (
    <div className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_360px]">
      <Card className="border border-slate-200 bg-white">
        <CardHeader>
          <CardTitle className="text-lg">{copy.title}</CardTitle>
          <p className="text-sm text-slate-500">{copy.desc}</p>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid gap-3 sm:grid-cols-2">
            {copy.bullets.map((bullet) => (
              <div key={bullet} className="flex items-start gap-2 rounded-xl bg-slate-50 p-4 text-sm text-slate-700">
                <ChevronRight className="mt-0.5 size-4 text-blue-600" />
                {bullet}
              </div>
            ))}
          </div>
          <Button className="bg-gradient-to-r from-blue-600 to-indigo-600">{copy.cta}</Button>
        </CardContent>
      </Card>
      <Card className="border border-slate-200 bg-white">
        <CardHeader>
          <CardTitle className="text-base">연결되는 다음 단계</CardTitle>
        </CardHeader>
        <CardContent className="space-y-2">
          {applications.slice(0, 3).map((app) => (
            <Link key={app.id} to={`/applications/${app.id}`} className="block rounded-xl border border-slate-200 bg-slate-50 p-3 hover:border-blue-300">
              <div className="text-sm font-semibold text-slate-800">{app.company} · {app.job}</div>
              <div className="mt-1 text-xs text-slate-500">{app.phase}</div>
            </Link>
          ))}
        </CardContent>
      </Card>
    </div>
  );
}

export function ApplicationsPage() {
  const [search, setSearch] = useState("");
  const [activeStatus, setActiveStatus] = useState("전체");
  const [fitAnalyses, setFitAnalyses] = useState<FitAnalysisDetail[]>([]);
  const [fitAnalysesLoading, setFitAnalysesLoading] = useState(false);
  const [fitAnalysesError, setFitAnalysesError] = useState<string | null>(null);
  const [searchParams] = useSearchParams();
  const location = useLocation();
  const navigate = useNavigate();
  const requestedTab = location.pathname.endsWith("/new") ? "new" : searchParams.get("tab") ?? "overview";
  const activeTab: ApplicationTab = applicationTabs.includes(requestedTab as ApplicationTab) ? (requestedTab as ApplicationTab) : "overview";
  const activeSection = activeTab === "overview" ? null : activeTab;
  const needsFitAnalysis = activeTab === "fit" || activeTab === "strategy" || activeTab === "learning";

  useEffect(() => {
    if (!needsFitAnalysis) return;

    let ignore = false;
    setFitAnalysesLoading(true);
    setFitAnalysesError(null);

    getFitAnalyses()
      .then((data) => {
        if (!ignore) setFitAnalyses(data);
      })
      .catch((error) => {
        if (!ignore) setFitAnalysesError(error instanceof Error ? error.message : "적합도 분석을 불러오지 못했습니다.");
      })
      .finally(() => {
        if (!ignore) setFitAnalysesLoading(false);
      });

    return () => {
      ignore = true;
    };
  }, [needsFitAnalysis]);

  const filtered = applications.filter((a) => {
    const matchSearch = a.company.includes(search) || a.job.includes(search);
    const matchStatus = activeStatus === "전체" || a.status === activeStatus.replace("전체", "");
    return matchSearch && (activeStatus === "전체" || matchStatus);
  });

  return (
    <div className="bg-slate-50 min-h-screen">
      <div className="max-w-[1400px] mx-auto px-6 py-8 space-y-6">
        {/* Header */}
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div>
            <h1 className="text-2xl font-black text-slate-900 flex items-center gap-2">
              <Briefcase className="size-6 text-blue-600" />
              지원 건 관리
            </h1>
            <p className="text-slate-500 text-sm mt-1">공고별·기업별 준비 과정을 독립적으로 관리하세요</p>
          </div>
          <Button
            className="bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-700 hover:to-indigo-700 gap-2"
            onClick={() => navigate("/applications?tab=new")}
          >
            <Plus className="size-4" />
            새 지원 건 만들기
          </Button>
        </div>

        <div className="flex overflow-x-auto rounded-xl border border-slate-200 bg-white p-1">
          {applicationNav.map((item) => (
            <button
              key={item.key}
              onClick={() => navigate(item.key === "overview" ? "/applications" : `/applications?tab=${item.key}`)}
              className={`flex shrink-0 items-center gap-1.5 rounded-lg px-3 py-2 text-xs font-semibold transition-colors ${
                activeTab === item.key ? "bg-blue-600 text-white" : "text-slate-600 hover:bg-slate-50 hover:text-blue-600"
              }`}
            >
              <item.icon className="size-3.5" />
              {item.label}
            </button>
          ))}
        </div>

        {activeSection && (
          activeSection === "fit" ? (
            <FitAnalysisPanel analyses={fitAnalyses} loading={fitAnalysesLoading} error={fitAnalysesError} />
          ) : activeSection === "strategy" ? (
            <StrategyPanel analyses={fitAnalyses} loading={fitAnalysesLoading} error={fitAnalysesError} />
          ) : activeSection === "learning" ? (
            <LearningRecommendationPanel analyses={fitAnalyses} loading={fitAnalysesLoading} error={fitAnalysesError} />
          ) : (
            <ApplicationStructurePanel section={activeSection} />
          )
        )}

        {activeTab === "overview" && (
          <>
        {/* Filters */}
        <Card className="border border-slate-200 bg-white">
          <CardContent className="p-4 flex flex-col md:flex-row gap-4 items-start md:items-center">
            <div className="relative flex-1 max-w-xs">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 size-4 text-slate-400" />
              <Input
                placeholder="기업명, 직무명 검색..."
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className="pl-9 h-9"
              />
            </div>
            <div className="flex gap-2 flex-wrap">
              {statusOptions.map((s) => (
                <button
                  key={s}
                  className={`px-3 py-1.5 rounded-full text-xs font-semibold transition-colors ${
                    activeStatus === s
                      ? "bg-blue-600 text-white"
                      : "bg-slate-100 text-slate-600 hover:bg-slate-200"
                  }`}
                  onClick={() => setActiveStatus(s)}
                >
                  {s}
                </button>
              ))}
            </div>
            <div className="flex gap-2 ml-auto">
              <Button variant="outline" size="sm" className="gap-1.5 h-9">
                <SortAsc className="size-4" /> 정렬
              </Button>
              <Button variant="outline" size="sm" className="gap-1.5 h-9">
                <Filter className="size-4" /> 필터
              </Button>
            </div>
          </CardContent>
        </Card>

        {/* Summary bar */}
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          {[
            { label: "전체 지원 건", value: applications.length, icon: Briefcase, color: "text-blue-600" },
            { label: "준비중", value: applications.filter(a => a.status === "준비중").length, icon: Clock, color: "text-orange-600" },
            { label: "면접연습중", value: applications.filter(a => a.status === "면접연습중").length, icon: FileText, color: "text-purple-600" },
            { label: "즐겨찾기", value: applications.filter(a => a.starred).length, icon: Star, color: "text-amber-600" },
          ].map((s) => (
            <div key={s.label} className="bg-white rounded-xl border border-slate-200 p-4 flex items-center gap-3">
              <s.icon className={`size-5 ${s.color}`} />
              <div>
                <div className="text-xl font-black text-slate-900">{s.value}</div>
                <div className="text-xs text-slate-500">{s.label}</div>
              </div>
            </div>
          ))}
        </div>

        {/* Applications grid */}
        <div className="grid md:grid-cols-2 xl:grid-cols-3 gap-4">
          {/* New application card */}
          <div
            className="border-2 border-dashed border-slate-300 rounded-xl p-6 flex flex-col items-center justify-center gap-3 cursor-pointer hover:border-blue-400 hover:bg-blue-50 transition-all group min-h-[180px]"
            onClick={() => navigate("/applications/new")}
          >
            <div className="size-12 rounded-xl bg-slate-100 group-hover:bg-blue-100 flex items-center justify-center transition-colors">
              <Plus className="size-6 text-slate-400 group-hover:text-blue-600 transition-colors" />
            </div>
            <div className="text-sm font-semibold text-slate-500 group-hover:text-blue-600 transition-colors">새 지원 건 만들기</div>
            <div className="text-xs text-slate-400 text-center">공고문 업로드 후 AI 분석 시작</div>
          </div>

          {filtered.map((app) => (
            <Link to={`/applications/${app.id}`} key={app.id}>
              <Card className="border border-slate-200 bg-white hover:border-blue-400 hover:shadow-lg transition-all cursor-pointer h-full">
                <CardContent className="p-5 space-y-4">
                  {/* Header */}
                  <div className="flex items-start justify-between">
                    <div className="flex items-center gap-3">
                      <div className="size-10 rounded-xl bg-gradient-to-br from-blue-600 to-indigo-600 flex items-center justify-center text-white font-bold text-base flex-shrink-0">
                        {app.company[0]}
                      </div>
                      <div>
                        <div className="font-bold text-slate-800 text-sm">{app.company}</div>
                        <div className="text-xs text-slate-500">{app.job}</div>
                      </div>
                    </div>
                    <div className="flex items-center gap-1">
                      {app.starred && <Star className="size-4 fill-amber-400 text-amber-400" />}
                      <button className="p-1 hover:bg-slate-100 rounded" onClick={(e) => e.preventDefault()}>
                        <MoreHorizontal className="size-4 text-slate-400" />
                      </button>
                    </div>
                  </div>

                  {/* Desc */}
                  <div className="text-xs text-slate-500">{app.desc}</div>

                  {/* Score */}
                  <div className="space-y-1.5">
                    <div className="flex items-center justify-between text-xs">
                      <span className="text-slate-500">직무 적합도</span>
                      <span className={`font-black ${app.score >= 70 ? "text-green-600" : app.score >= 50 ? "text-amber-600" : "text-red-500"}`}>
                        {app.score}점
                      </span>
                    </div>
                    <Progress value={app.score} className="h-1.5" />
                  </div>

                  {/* Tags */}
                  <div className="flex flex-wrap gap-1">
                    {app.tags.map((t) => (
                      <span key={t} className="text-[10px] bg-slate-100 text-slate-600 px-1.5 py-0.5 rounded">{t}</span>
                    ))}
                  </div>

                  {/* Footer */}
                  <div className="flex items-center justify-between pt-1 border-t border-slate-100">
                    <Badge className={`text-[10px] px-2 py-0.5 ${
                      app.status === "준비중" ? "bg-blue-100 text-blue-700" :
                      app.status === "면접연습중" ? "bg-purple-100 text-purple-700" :
                      app.status === "분석완료" ? "bg-green-100 text-green-700" :
                      "bg-slate-100 text-slate-700"
                    }`}>{app.status}</Badge>
                    <div className="flex items-center gap-1 text-xs text-slate-400">
                      <Calendar className="size-3" />
                      {app.date}
                    </div>
                  </div>
                </CardContent>
              </Card>
            </Link>
          ))}
        </div>
          </>
        )}
      </div>
    </div>
  );
}
