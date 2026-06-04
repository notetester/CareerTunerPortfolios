import { Link, useNavigate } from "react-router";
import { Button } from "../components/ui/button";
import { Badge } from "../components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "../components/ui/card";
import { Progress } from "../components/ui/progress";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "../components/ui/tabs";
import {
  Sparkles, ArrowRight, CheckCircle2, FileText, Target, MessageSquare,
  TrendingUp, Users, BarChart3, Zap, Shield, Clock, Star,
  ChevronRight, Play, Building2, Briefcase, BookOpen, PenTool,
  Award, Bot, Mic, Video, Brain, AlertCircle, ThumbsUp, Search,
} from "lucide-react";

const coreFeaturesData = [
  {
    icon: FileText,
    title: "공고문 AI 분석",
    desc: "텍스트/PDF/이미지 업로드만 하면 AI가 요구 기술, 우대 조건, 직무 역량, 예상 난이도를 자동 추출",
    color: "from-blue-500 to-cyan-500",
    badge: "핵심 기능",
  },
  {
    icon: Building2,
    title: "기업 현황 조사",
    desc: "기업 주요 사업, 최근 이슈, 경쟁사, 면접에서 언급하면 좋은 포인트를 AI가 자동 정리",
    color: "from-purple-500 to-violet-500",
    badge: "프로",
  },
  {
    icon: Target,
    title: "내 스펙 비교 분석",
    desc: "공고 요구조건과 내 프로필을 1:1 비교해 직무 적합도 점수와 부족 역량을 정확히 진단",
    color: "from-orange-500 to-rose-500",
    badge: "핵심 기능",
  },
  {
    icon: BookOpen,
    title: "학습/자격증 추천",
    desc: "부족한 역량에 맞는 학습 방향, 자격증, 강의를 우선순위 기준으로 추천",
    color: "from-green-500 to-emerald-500",
    badge: "신규",
  },
  {
    icon: MessageSquare,
    title: "AI 가상 면접 (8가지 모드)",
    desc: "기본/직무/인성/압박/실전/자소서 기반 등 8가지 면접 모드로 실전처럼 연습",
    color: "from-teal-500 to-cyan-500",
    badge: "대표 기능",
  },
  {
    icon: Mic,
    title: "음성 면접 & 분석",
    desc: "마이크로 답변하면 음성 인식 텍스트 변환 + 말속도, 침묵 시간, 발화 길이까지 분석",
    color: "from-pink-500 to-rose-500",
    badge: "프로",
  },
  {
    icon: PenTool,
    title: "답변 첨삭 & 개선",
    desc: "AI가 논리성, 구체성, 직무 적합성을 평가하고 완성도 높은 개선 답변까지 제시",
    color: "from-amber-500 to-orange-500",
    badge: "핵심 기능",
  },
  {
    icon: TrendingUp,
    title: "장기 취업 경향 분석",
    desc: "여러 지원 건을 종합해 반복되는 약점, 지원 패턴, 장기 취업 전략을 AI가 추천",
    color: "from-indigo-500 to-blue-500",
    badge: "프로",
  },
];

const interviewModes = [
  { title: "기본 면접", desc: "자기소개, 지원동기, 장단점", icon: "💬", color: "bg-blue-50 border-blue-200" },
  { title: "직무 면접", desc: "공고 기반 기술/직무 질문", icon: "⚙️", color: "bg-purple-50 border-purple-200" },
  { title: "인성 면접", desc: "협업, 갈등, 책임감, 태도", icon: "🤝", color: "bg-green-50 border-green-200" },
  { title: "압박 면접", desc: "꼬리 질문, 반박 질문", icon: "⚡", color: "bg-red-50 border-red-200" },
  { title: "실전 면접", desc: "시간 제한, 랜덤 질문", icon: "⏱️", color: "bg-orange-50 border-orange-200" },
  { title: "자소서 기반", desc: "자기소개서 문장 기반 질문", icon: "📄", color: "bg-teal-50 border-teal-200" },
  { title: "포트폴리오 기반", desc: "프로젝트 설명 중심 질문", icon: "💼", color: "bg-indigo-50 border-indigo-200" },
  { title: "기업 맞춤", desc: "기업 현황과 공고 기반 질문", icon: "🏢", color: "bg-amber-50 border-amber-200" },
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
  { skill: "React", status: "보유", grade: "강점", color: "text-green-700 bg-green-100" },
  { skill: "TypeScript", status: "일부 경험", grade: "보완 필요", color: "text-amber-700 bg-amber-100" },
  { skill: "AWS", status: "없음", grade: "학습 필요", color: "text-red-700 bg-red-100" },
  { skill: "Git 협업", status: "보유", grade: "강점", color: "text-green-700 bg-green-100" },
  { skill: "REST API", status: "보유", grade: "강점", color: "text-green-700 bg-green-100" },
  { skill: "포트폴리오", status: "부족", grade: "보완 필요", color: "text-amber-700 bg-amber-100" },
];

const communityPosts = [
  { cat: "면접 후기", company: "카카오페이", job: "프론트엔드", title: "카카오페이 프론트엔드 1차 면접 후기 (합격)", views: 2847, likes: 124, hot: true },
  { cat: "합격 전략", company: "네이버", job: "백엔드", title: "네이버 신입 백엔드 합격 후기 - 준비 과정 총정리", views: 5129, likes: 341, hot: true },
  { cat: "직무별 질문", company: "익명", job: "전산직", title: "공기업 전산직 자주 나오는 기술 질문 100선", views: 8293, likes: 562, hot: false },
  { cat: "취업 후기", company: "삼성SDS", job: "IT 솔루션", title: "삼성SDS IT 솔루션 최종 합격 스펙 & 준비 방법", views: 3412, likes: 187, hot: false },
  { cat: "자유게시판", company: "익명", job: "프론트엔드", title: "CareerTuner으로 면접 준비하고 최종 합격했습니다 🎉", views: 1923, likes: 89, hot: false },
];

const testimonials = [
  { name: "이*현", job: "카카오 프론트엔드 합격", avatar: "이", plan: "프로", text: "공고 분석과 스펙 비교 기능이 정말 도움이 됐어요. 부족한 부분을 정확히 짚어줘서 집중적으로 준비할 수 있었습니다." },
  { name: "박*준", job: "네이버 백엔드 합격", avatar: "박", plan: "프리미엄", text: "AI 가상 면접을 20번 넘게 연습했는데, 실제 면접에서 정말 비슷한 질문이 나왔어요. 답변도 훨씬 자연스럽게 나왔습니다." },
  { name: "김*영", job: "삼성SDS 최종 합격", avatar: "김", plan: "프로", text: "답변 첨삭 기능이 최고예요. 막연하게 답했던 것들이 어떻게 개선되어야 하는지 구체적으로 알려줘서 좋았습니다." },
];

export function HomePage() {
  const navigate = useNavigate();

  return (
    <div className="bg-white">
      {/* ─── Hero ─── */}
      <section className="relative overflow-hidden bg-[linear-gradient(135deg,#0f172a_0%,#12343b_48%,#4338ca_100%)] text-white">
        <div className="relative w-full max-w-[1400px] mx-auto px-4 sm:px-6 py-14 lg:py-24">
          <div className="grid lg:grid-cols-2 gap-12 items-center">
            {/* Left text */}
            <div className="space-y-7 min-w-0">
              <div className="flex items-center gap-3">
                <Badge className="bg-blue-500/20 text-blue-300 border-blue-500/30 px-3 py-1 max-w-full whitespace-normal text-left">
                  <Sparkles className="size-3 mr-1.5" />
                  AI 취업 전략 플랫폼 · 2026 NEW
                </Badge>
              </div>
              <h1 className="text-4xl sm:text-5xl xl:text-6xl font-black leading-tight tracking-tight">
                <span className="sm:hidden">
                  채용공고와<br />내 스펙을<br />
                  <span className="bg-gradient-to-r from-blue-400 to-cyan-300 bg-clip-text text-transparent">
                    AI가 정밀 분석
                  </span>
                  <br />합격 전략 완성
                </span>
                <span className="hidden sm:inline">
                  채용공고와 내 스펙을<br />
                  <span className="bg-gradient-to-r from-blue-400 to-cyan-300 bg-clip-text text-transparent">
                    AI가 정밀 분석
                  </span>
                  <br />합격 전략 완성
                </span>
              </h1>
              <p className="text-base sm:text-lg text-slate-300 leading-relaxed max-w-lg">
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
                  className="w-full sm:w-auto justify-center bg-gradient-to-r from-blue-500 to-indigo-500 hover:from-blue-400 hover:to-indigo-400 text-white shadow-lg shadow-blue-500/30 text-base px-8"
                  onClick={() => navigate("/login")}
                >
                  무료로 시작하기
                  <ArrowRight className="ml-2 size-5" />
                </Button>
                <Button
                  size="lg"
                  variant="outline"
                  className="w-full sm:w-auto justify-center border-white/20 text-white bg-white/10 hover:bg-white/20 text-base px-8"
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
                  <div key={t} className="flex items-center gap-1.5 text-sm text-slate-300">
                    <CheckCircle2 className="size-4 text-green-400" />
                    {t}
                  </div>
                ))}
              </div>
            </div>

            {/* Right mock UI */}
            <div className="relative hidden lg:block">
              <div className="absolute -inset-4 bg-gradient-to-r from-blue-500/20 to-indigo-500/20 rounded-3xl blur-2xl" />
              <div className="relative bg-white/10 backdrop-blur-sm border border-white/20 rounded-2xl overflow-hidden shadow-2xl">
                {/* Mock tabs */}
                <div className="flex items-center gap-1 px-4 py-3 bg-white/5 border-b border-white/10 overflow-x-auto">
                  {["공고분석", "기업분석", "스펙비교", "예상질문", "가상면접"].map((t, i) => (
                    <div key={t} className={`px-3 py-1 rounded text-xs font-medium whitespace-nowrap ${i === 2 ? "bg-blue-500 text-white" : "text-slate-300"}`}>
                      {t}
                    </div>
                  ))}
                </div>
                <div className="p-5 space-y-4">
                  <div className="flex items-center justify-between">
                    <div>
                      <div className="font-bold text-white text-sm">카카오페이 · 프론트엔드 개발자</div>
                      <div className="text-xs text-slate-400 mt-0.5">2026-08-01 공고 · React 3년 이상</div>
                    </div>
                    <Badge className="bg-green-500/20 text-green-300 border-green-500/30">분석 완료</Badge>
                  </div>

                  {/* Fit score */}
                  <div className="bg-white/5 rounded-xl p-4 space-y-2">
                    <div className="flex items-center justify-between text-sm">
                      <span className="text-slate-300">직무 적합도</span>
                      <span className="font-black text-blue-300 text-lg">72점</span>
                    </div>
                    <div className="h-2 bg-white/10 rounded-full overflow-hidden">
                      <div className="h-full w-[72%] bg-gradient-to-r from-blue-500 to-cyan-500 rounded-full" />
                    </div>
                  </div>

                  {/* Skills grid */}
                  <div className="grid grid-cols-2 gap-2">
                    {specComparisonData.slice(0, 4).map((s) => (
                      <div key={s.skill} className="flex items-center justify-between bg-white/5 rounded-lg px-3 py-2">
                        <span className="text-xs text-slate-300">{s.skill}</span>
                        <span className={`text-[10px] font-semibold px-1.5 py-0.5 rounded ${s.color}`}>
                          {s.grade}
                        </span>
                      </div>
                    ))}
                  </div>

                  {/* Interview question preview */}
                  <div className="bg-white/5 rounded-xl p-3">
                    <div className="text-xs text-slate-400 mb-2 font-medium">🎯 AI 예상 질문 (직무)</div>
                    <div className="text-xs text-slate-200 leading-relaxed">
                      "React에서 상태 관리를 어떻게 설계하나요? Recoil과 Zustand를 비교 설명해주세요."
                    </div>
                  </div>

                  <Button className="w-full bg-gradient-to-r from-blue-500 to-indigo-500 text-sm">
                    <MessageSquare className="mr-2 size-4" />
                    AI 가상 면접 시작하기
                  </Button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* ─── Stats bar ─── */}
      <section className="bg-white border-b border-slate-100">
        <div className="w-full max-w-[1400px] mx-auto px-4 sm:px-6 py-8">
          <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-5 lg:gap-12">
            {[
              { icon: Users, value: "10,000+", label: "활성 사용자", sub: "매월 신규 2,000명" },
              { icon: MessageSquare, value: "50,000+", label: "AI 가상 면접", sub: "평균 만족도 4.8/5" },
              { icon: FileText, value: "30,000+", label: "공고 분석 건수", sub: "평균 분석 시간 30초" },
              { icon: TrendingUp, value: "92%", label: "면접 준비도 향상", sub: "3주 사용 후 기준" },
            ].map((s) => (
              <div key={s.label} className="flex items-center gap-4 min-w-0">
                <div className="size-12 rounded-xl bg-gradient-to-br from-blue-100 to-indigo-100 flex items-center justify-center flex-shrink-0">
                  <s.icon className="size-6 text-blue-600" />
                </div>
                <div className="min-w-0">
                  <div className="text-xl sm:text-2xl font-black bg-gradient-to-r from-blue-600 to-indigo-600 bg-clip-text text-transparent">
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
            <Badge className="bg-blue-100 text-blue-700 px-4 py-1">핵심 기능</Badge>
            <h2 className="text-4xl font-black text-slate-900">AI가 제공하는 8가지 취업 솔루션</h2>
            <p className="text-lg text-slate-500 max-w-2xl mx-auto">
              공고 분석부터 장기 취업 전략까지, 취업 준비의 전 과정을 AI가 지원합니다
            </p>
          </div>

          <div className="grid md:grid-cols-2 lg:grid-cols-4 gap-5">
            {coreFeaturesData.map((f, i) => (
              <Card key={i} className="border border-slate-200 hover:border-blue-300 hover:shadow-lg transition-all duration-300 bg-white group">
                <CardHeader className="pb-3">
                  <div className="flex items-start justify-between mb-3">
                    <div className={`size-11 rounded-xl bg-gradient-to-br ${f.color} flex items-center justify-center shadow-md group-hover:scale-110 transition-transform`}>
                      <f.icon className="size-5 text-white" />
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
      <section id="how-it-works" className="py-20 bg-white">
        <div className="w-full max-w-[1400px] mx-auto px-6">
          <div className="text-center mb-14 space-y-3">
            <Badge className="bg-indigo-100 text-indigo-700 px-4 py-1">사용 방법</Badge>
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
                <div className="size-9 rounded-lg bg-gradient-to-br from-blue-600 to-indigo-600 text-white text-xs font-black flex items-center justify-center flex-shrink-0 group-hover:scale-110 transition-transform">
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
              className="bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-700 hover:to-indigo-700 text-base px-10"
              onClick={() => window.location.href = "/login"}
            >
              지금 바로 시작하기
              <ArrowRight className="ml-2 size-5" />
            </Button>
          </div>
        </div>
      </section>

      {/* ─── Application Detail Demo (3-column layout preview) ─── */}
      <section className="py-20 bg-gradient-to-br from-slate-50 to-blue-50">
        <div className="w-full max-w-[1400px] mx-auto px-6">
          <div className="text-center mb-14 space-y-3">
            <Badge className="bg-teal-100 text-teal-700 px-4 py-1">지원 건 관리</Badge>
            <h2 className="text-4xl font-black text-slate-900">지원 건마다 독립된 AI 공간</h2>
            <p className="text-lg text-slate-500 max-w-2xl mx-auto">
              ChatGPT 세션처럼 기업별·공고별로 분리 관리. 탭 8개에 모든 준비 정보가 담깁니다.
            </p>
          </div>

          {/* Mock 3-column layout */}
          <div className="bg-white rounded-2xl shadow-2xl border border-slate-200 overflow-hidden">
            <div className="flex h-[480px]">
              {/* Left sidebar */}
              <div className="w-56 bg-slate-900 text-white flex-shrink-0 flex flex-col">
                <div className="p-4 border-b border-slate-700">
                  <div className="text-xs text-slate-400 font-semibold uppercase tracking-wide mb-3">지원 건 목록</div>
                  {[
                    { co: "카카오페이", job: "프론트엔드", active: true, score: 72 },
                    { co: "네이버", job: "백엔드 개발", active: false, score: 58 },
                    { co: "삼성SDS", job: "IT 솔루션", active: false, score: 65 },
                    { co: "라인", job: "풀스택 개발", active: false, score: 44 },
                  ].map((item) => (
                    <div key={item.co} className={`mb-2 p-2.5 rounded-lg cursor-pointer transition-colors ${item.active ? "bg-blue-600" : "hover:bg-slate-800"}`}>
                      <div className="text-xs font-semibold">{item.co}</div>
                      <div className="text-[10px] text-slate-400">{item.job}</div>
                      <div className="text-[10px] mt-1 text-blue-300">적합도 {item.score}점</div>
                    </div>
                  ))}
                </div>
                <div className="p-4">
                  <div className="text-xs text-slate-400 font-semibold uppercase tracking-wide mb-2">즐겨찾기</div>
                  <div className="text-xs text-slate-500">⭐ 라인 · 풀스택</div>
                </div>
              </div>

              {/* Center main */}
              <div className="flex-1 flex flex-col min-w-0">
                <div className="flex items-center justify-between px-5 py-3 border-b border-slate-200 bg-white">
                  <div>
                    <div className="font-bold text-slate-900 text-sm">카카오페이 · 프론트엔드 개발자</div>
                    <div className="text-xs text-slate-500">2026-08-01 공고 · React 3년 이상</div>
                  </div>
                  <Badge className="bg-green-100 text-green-700 text-xs">준비중</Badge>
                </div>
                <div className="flex border-b border-slate-200 bg-white overflow-x-auto">
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
                    <Badge className="text-xs bg-blue-100 text-blue-700">직무 적합도 72점</Badge>
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
                  <div className="bg-amber-50 border border-amber-200 rounded-xl p-3 text-xs">
                    <div className="font-semibold text-amber-800 mb-1">⚠ AI 준비 전략</div>
                    <div className="text-amber-700 space-y-1">
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
                        <div key={s} className="flex items-center gap-1.5 text-xs bg-red-50 text-red-700 px-2 py-1 rounded">
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
                  <div className="bg-white rounded-xl p-3 border border-slate-200">
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
      <section className="py-20 bg-white">
        <div className="w-full max-w-[1400px] mx-auto px-6">
          <div className="text-center mb-14 space-y-3">
            <Badge className="bg-purple-100 text-purple-700 px-4 py-1">AI 가상 면접</Badge>
            <h2 className="text-4xl font-black text-slate-900">8가지 면접 모드로 실전 완벽 대비</h2>
            <p className="text-lg text-slate-500 max-w-2xl mx-auto">
              기본 면접부터 압박 면접, 기업 맞춤 면접까지 — 모든 상황을 연습할 수 있습니다
            </p>
          </div>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            {interviewModes.map((m) => (
              <Card key={m.title} className={`border ${m.color} hover:shadow-md transition-shadow cursor-pointer group`}>
                <CardContent className="p-5 text-center space-y-2">
                  <div className="text-3xl group-hover:scale-110 transition-transform">{m.icon}</div>
                  <div className="font-bold text-slate-800 text-sm">{m.title}</div>
                  <div className="text-xs text-slate-500">{m.desc}</div>
                </CardContent>
              </Card>
            ))}
          </div>

          {/* Answer correction demo */}
          <div className="mt-12 grid md:grid-cols-2 gap-6">
            <Card className="border border-red-200 bg-red-50">
              <CardHeader className="pb-2">
                <CardTitle className="text-sm flex items-center gap-2 text-red-700">
                  <AlertCircle className="size-4" /> 사용자 원답변
                </CardTitle>
              </CardHeader>
              <CardContent>
                <div className="text-sm text-slate-600 bg-white rounded-lg p-3 border border-red-100">
                  "학교 프로젝트에서 React를 사용해서 게시판을 만들었습니다."
                </div>
                <div className="mt-3 space-y-1">
                  <div className="text-xs text-red-600 flex items-start gap-1.5"><AlertCircle className="size-3 mt-0.5 flex-shrink-0" /> 역할이 불명확</div>
                  <div className="text-xs text-red-600 flex items-start gap-1.5"><AlertCircle className="size-3 mt-0.5 flex-shrink-0" /> 구체적 기능 설명 없음</div>
                  <div className="text-xs text-red-600 flex items-start gap-1.5"><AlertCircle className="size-3 mt-0.5 flex-shrink-0" /> 문제 해결 경험 없음</div>
                </div>
              </CardContent>
            </Card>
            <Card className="border border-green-200 bg-green-50">
              <CardHeader className="pb-2">
                <CardTitle className="text-sm flex items-center gap-2 text-green-700">
                  <ThumbsUp className="size-4" /> AI 개선 답변
                </CardTitle>
              </CardHeader>
              <CardContent>
                <div className="text-sm text-slate-600 bg-white rounded-lg p-3 border border-green-100 leading-relaxed">
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
      <section className="py-20 bg-gradient-to-br from-slate-50 to-indigo-50">
        <div className="w-full max-w-[1400px] mx-auto px-6">
          <div className="text-center mb-14 space-y-3">
            <Badge className="bg-slate-200 text-slate-700 px-4 py-1">왜 다른가요?</Badge>
            <h2 className="text-4xl font-black text-slate-900">일반 채용 사이트와 비교</h2>
          </div>
          <div className="max-w-4xl mx-auto grid md:grid-cols-2 gap-6">
            <Card className="border-2 border-slate-200 bg-white">
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
            <Card className="border-2 border-blue-400 shadow-xl bg-gradient-to-br from-blue-50 to-indigo-50">
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <div className="size-8 rounded-lg bg-gradient-to-br from-blue-600 to-indigo-600 flex items-center justify-center">
                    <Sparkles className="size-4 text-white" />
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
      <section id="pricing" className="py-20 bg-white">
        <div className="w-full max-w-[1400px] mx-auto px-6">
          <div className="text-center mb-14 space-y-3">
            <Badge className="bg-green-100 text-green-700 px-4 py-1">요금제</Badge>
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
                        <Badge className="bg-gradient-to-r from-blue-600 to-indigo-600 text-white px-4">
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
                        className={`w-full ${plan.highlighted ? "bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-700 hover:to-indigo-700" : ""}`}
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
              <Badge className="bg-orange-100 text-orange-700 px-4 py-1">커뮤니티</Badge>
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
                <div className="bg-white border border-slate-200 hover:border-blue-300 hover:shadow-md transition-all rounded-xl p-4 flex items-center gap-4 group">
                  <Badge className={`flex-shrink-0 text-xs ${
                    post.cat === "면접 후기" ? "bg-blue-100 text-blue-700" :
                    post.cat === "합격 전략" ? "bg-green-100 text-green-700" :
                    post.cat === "직무별 질문" ? "bg-purple-100 text-purple-700" :
                    "bg-slate-100 text-slate-700"
                  }`}>{post.cat}</Badge>
                  {post.hot && <Badge className="flex-shrink-0 text-xs bg-red-100 text-red-600">HOT</Badge>}
                  <div className="flex-1 min-w-0">
                    <div className="font-semibold text-slate-800 text-sm truncate group-hover:text-blue-600 transition-colors">{post.title}</div>
                    <div className="text-xs text-slate-400 mt-0.5">{post.company !== "익명" ? post.company + " · " : ""}{post.job}</div>
                  </div>
                  <div className="flex items-center gap-4 text-xs text-slate-400 flex-shrink-0">
                    <span>👁 {post.views.toLocaleString()}</span>
                    <span>❤ {post.likes}</span>
                  </div>
                </div>
              </Link>
            ))}
          </div>
        </div>
      </section>

      {/* ─── Testimonials ─── */}
      <section className="py-20 bg-white">
        <div className="w-full max-w-[1400px] mx-auto px-6">
          <div className="text-center mb-14 space-y-3">
            <Badge className="bg-yellow-100 text-yellow-700 px-4 py-1">사용자 후기</Badge>
            <h2 className="text-4xl font-black text-slate-900">실제 합격자들의 이야기</h2>
          </div>
          <div className="grid md:grid-cols-3 gap-6">
            {testimonials.map((t, i) => (
              <Card key={i} className="border-2 border-slate-200 bg-white hover:border-blue-200 hover:shadow-lg transition-all">
                <CardContent className="p-6 space-y-4">
                  <div className="flex items-center gap-3">
                    <div className="size-11 rounded-full bg-gradient-to-br from-blue-500 to-indigo-500 flex items-center justify-center text-white font-bold text-base">
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
                    <Badge className="ml-auto text-xs bg-blue-100 text-blue-700">{t.plan}</Badge>
                  </div>
                  <p className="text-sm text-slate-600 leading-relaxed">"{t.text}"</p>
                </CardContent>
              </Card>
            ))}
          </div>
        </div>
      </section>

      {/* ─── CTA ─── */}
      <section className="py-20 bg-gradient-to-br from-blue-700 via-indigo-700 to-purple-700 text-white">
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
            <Button size="lg" className="bg-white text-blue-700 hover:bg-blue-50 text-base px-10 shadow-xl" onClick={() => navigate("/login")}>
              무료로 시작하기
              <ArrowRight className="ml-2 size-5" />
            </Button>
            <Button size="lg" variant="outline" className="border-white/40 text-white hover:bg-white/10 text-base px-10" onClick={() => navigate("/pricing")}>
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
