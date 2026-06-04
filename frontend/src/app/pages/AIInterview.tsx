import { useState } from "react";
import { useNavigate, useSearchParams } from "react-router";
import { Button } from "../components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "../components/ui/card";
import { Badge } from "../components/ui/badge";
import { Progress } from "../components/ui/progress";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "../components/ui/tabs";
import {
  MessageSquare, Mic, Video, FileText, BarChart3, PenTool,
  Play, ChevronRight, ThumbsUp, ThumbsDown, AlertCircle,
  CheckCircle2, Brain, Clock, Target, Star, ArrowRight, Award,
} from "lucide-react";

const interviewModes = [
  { id: "basic", icon: "💬", title: "기본 면접", desc: "자기소개, 지원동기, 장단점", difficulty: "하", recommended: false },
  { id: "job", icon: "⚙️", title: "직무 면접", desc: "공고 기반 기술/직무 질문", difficulty: "상", recommended: true },
  { id: "personality", icon: "🤝", title: "인성 면접", desc: "협업, 갈등, 책임감, 태도", difficulty: "중", recommended: false },
  { id: "pressure", icon: "⚡", title: "압박 면접", desc: "꼬리 질문, 반박 질문", difficulty: "상", recommended: false },
  { id: "realtime", icon: "⏱️", title: "실전 면접", desc: "시간 제한, 랜덤 질문", difficulty: "상", recommended: false },
  { id: "cover", icon: "📄", title: "자소서 기반", desc: "자기소개서 문장을 기반으로 질문", difficulty: "중", recommended: false },
  { id: "portfolio", icon: "💼", title: "포트폴리오 기반", desc: "프로젝트 설명 중심 질문", difficulty: "중", recommended: true },
  { id: "company", icon: "🏢", title: "기업 맞춤", desc: "기업 현황과 공고 기반 질문", difficulty: "상", recommended: false },
];

const recentSessions = [
  { date: "2026-06-03", company: "카카오페이", mode: "직무 면접", score: 68, questions: 7, time: "24분" },
  { date: "2026-06-01", company: "네이버", mode: "인성 면접", score: 72, questions: 6, time: "20분" },
  { date: "2026-05-29", company: "카카오페이", mode: "기본 면접", score: 62, questions: 5, time: "18분" },
  { date: "2026-05-26", company: "삼성SDS", mode: "직무 면접", score: 55, questions: 8, time: "28분" },
];

const evaluationCategories = [
  { label: "답변 내용", score: 68 },
  { label: "직무 적합성", score: 70 },
  { label: "구체성", score: 60 },
  { label: "논리성", score: 80 },
  { label: "표현력", score: 72 },
  { label: "자신감", score: 65 },
  { label: "태도", score: 78 },
  { label: "시간 관리", score: 75 },
];

const correctionExample = {
  question: "React에서 성능 최적화를 어떻게 하시나요?",
  original: "useMemo와 useCallback을 사용합니다. 그리고 React.memo도 씁니다.",
  aiEval: "방향은 맞지만 구체적인 사용 상황, 판단 기준, 실제 경험이 없어 답변이 너무 짧고 피상적입니다.",
  improved: "React 성능 최적화는 상황에 맞게 접근합니다. 첫째로 불필요한 리렌더링을 막기 위해 useMemo와 useCallback을 복잡한 계산이나 자식 컴포넌트로 전달하는 함수에 선택적으로 적용합니다. 실제로 대시보드 프로젝트에서 차트 데이터 계산에 useMemo를 적용해 렌더링 시간을 약 40% 단축한 경험이 있습니다. React.memo는 같은 props로 반복 렌더되는 리스트 아이템에 적용했고, Code Splitting을 통한 번들 크기 최적화도 함께 적용합니다.",
  points: ["구체적 사용 상황 명시", "실제 수치 결과 포함", "판단 기준 설명"],
};

const interviewTabs = ["modes", "questions", "practice", "voice", "avatar", "evaluation", "correction", "report"] as const;
type InterviewTab = (typeof interviewTabs)[number];

export function AIInterviewPage() {
  const [selectedMode, setSelectedMode] = useState<string | null>(null);
  const [searchParams, setSearchParams] = useSearchParams();
  const requestedTab = searchParams.get("tab") ?? "modes";
  const activeTab: InterviewTab = interviewTabs.includes(requestedTab as InterviewTab) ? (requestedTab as InterviewTab) : "modes";
  const navigate = useNavigate();

  return (
    <div className="bg-slate-50 min-h-screen">
      <div className="max-w-[1400px] mx-auto px-6 py-8 space-y-8">
        {/* Header */}
        <div>
          <h1 className="text-2xl font-black text-slate-900 flex items-center gap-2">
            <MessageSquare className="size-6 text-blue-600" />
            AI 가상 면접 & 첨삭
          </h1>
          <p className="text-slate-500 text-sm mt-1">8가지 면접 모드로 실전처럼 연습하고, AI가 즉시 평가 및 개선 답변을 제공합니다</p>
        </div>

        <Tabs value={activeTab} onValueChange={(value) => setSearchParams(value === "modes" ? {} : { tab: value })}>
          <TabsList className="bg-white border border-slate-200 h-auto w-full justify-start overflow-x-auto p-1">
            <TabsTrigger value="modes">면접 모드 선택</TabsTrigger>
            <TabsTrigger value="questions">예상 질문 목록</TabsTrigger>
            <TabsTrigger value="practice">실전 모의면접</TabsTrigger>
            <TabsTrigger value="voice">음성 면접</TabsTrigger>
            <TabsTrigger value="avatar">아바타 면접관</TabsTrigger>
            <TabsTrigger value="evaluation">답변 평가 기준</TabsTrigger>
            <TabsTrigger value="correction">AI 첨삭</TabsTrigger>
            <TabsTrigger value="report">면접 리포트</TabsTrigger>
          </TabsList>

          {/* ─── 면접 모드 ─── */}
          <TabsContent value="modes" className="mt-6 space-y-6">
            <div className="bg-white rounded-2xl border border-slate-200 p-5">
              <div className="text-sm font-bold text-slate-700 mb-3">지원 건 선택</div>
              <div className="flex gap-2 flex-wrap">
                {["카카오페이 · 프론트엔드", "네이버 · 백엔드", "삼성SDS · IT솔루션"].map((app, i) => (
                  <button
                    key={app}
                    className={`px-3 py-1.5 rounded-full text-xs font-semibold border transition-colors ${i === 0 ? "bg-blue-600 text-white border-blue-600" : "border-slate-300 text-slate-600 hover:border-blue-400"}`}
                  >
                    {app}
                  </button>
                ))}
              </div>
            </div>

            <div className="grid sm:grid-cols-2 lg:grid-cols-4 gap-4">
              {interviewModes.map((mode) => (
                <button
                  key={mode.id}
                  onClick={() => setSelectedMode(mode.id)}
                  className={`relative p-5 rounded-2xl border-2 text-left transition-all hover:shadow-lg group ${
                    selectedMode === mode.id
                      ? "border-blue-500 bg-blue-50 shadow-lg"
                      : "border-slate-200 bg-white hover:border-blue-300"
                  }`}
                >
                  {mode.recommended && (
                    <div className="absolute -top-2 -right-2">
                      <Badge className="text-[9px] bg-gradient-to-r from-blue-600 to-indigo-600 text-white px-2 py-0.5">추천</Badge>
                    </div>
                  )}
                  <div className="text-3xl mb-3 group-hover:scale-110 transition-transform">{mode.icon}</div>
                  <div className="font-bold text-slate-800 text-sm mb-1">{mode.title}</div>
                  <div className="text-xs text-slate-500 mb-2 leading-relaxed">{mode.desc}</div>
                  <Badge className={`text-[10px] px-2 py-0.5 ${
                    mode.difficulty === "상" ? "bg-red-100 text-red-700" :
                    mode.difficulty === "중" ? "bg-amber-100 text-amber-700" :
                    "bg-green-100 text-green-700"
                  }`}>난이도 {mode.difficulty}</Badge>
                </button>
              ))}
            </div>

            <div className="grid md:grid-cols-3 gap-4">
              <Button
                size="lg"
                className="bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-700 hover:to-indigo-700 gap-2 h-14"
                disabled={!selectedMode}
              >
                <Mic className="size-5" />
                음성 면접 시작
              </Button>
              <Button
                size="lg"
                variant="outline"
                className="gap-2 h-14"
                disabled={!selectedMode}
              >
                <FileText className="size-5" />
                텍스트 면접 시작
              </Button>
              <Button
                size="lg"
                variant="outline"
                className="gap-2 h-14 border-purple-300 text-purple-700 hover:bg-purple-50"
                disabled={!selectedMode}
              >
                <Video className="size-5" />
                영상 면접 시작 (프리미엄)
              </Button>
            </div>

            {/* Recent sessions */}
            <div>
              <h3 className="font-bold text-slate-800 mb-3">최근 면접 기록</h3>
              <div className="space-y-2">
                {recentSessions.map((s, i) => (
                  <div key={i} className="bg-white border border-slate-200 rounded-xl p-4 flex items-center gap-4 hover:border-blue-300 transition-colors">
                    <div className="size-10 rounded-xl bg-gradient-to-br from-purple-500 to-indigo-500 flex items-center justify-center text-white text-xs font-bold flex-shrink-0">
                      {s.company[0]}
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="font-semibold text-slate-800 text-sm">{s.company} · {s.mode}</div>
                      <div className="text-xs text-slate-500 mt-0.5">{s.date} · {s.questions}개 질문 · {s.time}</div>
                    </div>
                    <div className="flex items-center gap-3">
                      <div className="text-center">
                        <div className={`text-lg font-black ${s.score >= 70 ? "text-green-600" : s.score >= 60 ? "text-amber-600" : "text-red-500"}`}>{s.score}</div>
                        <div className="text-[10px] text-slate-400">점수</div>
                      </div>
                      <Button size="sm" variant="outline" className="text-xs gap-1 h-7">
                        <BarChart3 className="size-3" /> 리포트
                      </Button>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </TabsContent>

          <TabsContent value="practice" className="mt-6 space-y-5">
            <Card className="border border-slate-200 bg-white">
              <CardHeader>
                <CardTitle className="flex items-center gap-2 text-base">
                  <Clock className="size-4 text-blue-600" />
                  실전 모의면접
                </CardTitle>
              </CardHeader>
              <CardContent className="grid gap-4 md:grid-cols-3">
                {[
                  { label: "면접 시간", value: "30분", desc: "실제 면접 흐름과 유사한 제한 시간" },
                  { label: "질문 구성", value: "8문항", desc: "자기소개, 직무, 인성, 꼬리질문 혼합" },
                  { label: "진행 방식", value: "랜덤", desc: "공고와 프로필 기반으로 즉시 출제" },
                ].map((item) => (
                  <div key={item.label} className="rounded-xl bg-slate-50 p-4">
                    <div className="text-xs text-slate-500">{item.label}</div>
                    <div className="mt-1 text-2xl font-black text-slate-900">{item.value}</div>
                    <div className="mt-2 text-xs text-slate-500">{item.desc}</div>
                  </div>
                ))}
              </CardContent>
            </Card>
            <Button className="bg-gradient-to-r from-blue-600 to-indigo-600">
              <Play className="mr-2 size-4" />
              실전 모의면접 시작
            </Button>
          </TabsContent>

          <TabsContent value="voice" className="mt-6 space-y-5">
            <Card className="border border-slate-200 bg-white">
              <CardHeader>
                <CardTitle className="flex items-center gap-2 text-base">
                  <Mic className="size-4 text-purple-600" />
                  음성 면접
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="grid gap-3 md:grid-cols-2">
                  {["말 속도", "침묵 구간", "반복 표현", "답변 길이"].map((metric) => (
                    <div key={metric} className="rounded-xl border border-slate-200 bg-slate-50 p-4">
                      <div className="text-sm font-semibold text-slate-800">{metric}</div>
                      <div className="mt-1 text-xs text-slate-500">녹음 기반 분석 지표가 이 영역에 표시됩니다</div>
                    </div>
                  ))}
                </div>
                <Button className="bg-gradient-to-r from-purple-600 to-indigo-600">
                  <Mic className="mr-2 size-4" />
                  음성 면접 시작
                </Button>
              </CardContent>
            </Card>
          </TabsContent>

          <TabsContent value="avatar" className="mt-6 space-y-5">
            <Card className="overflow-hidden border border-slate-200 bg-white">
              <div className="grid lg:grid-cols-[minmax(0,1fr)_360px]">
                <CardContent className="space-y-4 p-6">
                  <Badge className="bg-purple-100 text-purple-700">프리미엄</Badge>
                  <h2 className="text-xl font-black text-slate-900">아바타 면접관</h2>
                  <p className="text-sm leading-relaxed text-slate-600">
                    실제 면접관과 마주 앉은 흐름을 연습할 수 있도록 표정, 시선, 꼬리질문을 포함한 면접 시뮬레이션 구조를 제공합니다.
                  </p>
                  <div className="grid gap-3 sm:grid-cols-3">
                    {["시선 처리", "표정 피드백", "꼬리 질문"].map((item) => (
                      <div key={item} className="rounded-xl bg-purple-50 p-3 text-sm font-semibold text-purple-800">{item}</div>
                    ))}
                  </div>
                  <Button variant="outline" className="border-purple-300 text-purple-700 hover:bg-purple-50">
                    아바타 면접 설정
                  </Button>
                </CardContent>
                <div className="flex min-h-64 items-center justify-center bg-gradient-to-br from-slate-900 via-indigo-950 to-blue-950 p-6">
                  <div className="rounded-full border-4 border-blue-300 bg-white/10 p-8 text-center text-white shadow-2xl">
                    <Video className="mx-auto size-12" />
                    <div className="mt-3 text-sm font-semibold">AI 면접관 대기 중</div>
                  </div>
                </div>
              </div>
            </Card>
          </TabsContent>

          {/* ─── 예상 질문 ─── */}
          <TabsContent value="questions" className="mt-6 space-y-4">
            <div className="flex items-center justify-between">
              <h2 className="font-bold text-slate-800">예상 면접 질문 목록</h2>
              <Button size="sm" variant="outline" className="gap-1.5 text-xs">
                질문 재생성
              </Button>
            </div>
            {[
              { cat: "기술 질문", questions: [
                "React Hooks의 종류와 각각의 사용 시나리오를 설명해주세요.",
                "TypeScript의 제네릭(Generic)을 왜 사용하고 어떻게 활용하나요?",
                "웹 성능 최적화 경험이 있으신가요? 구체적인 사례를 들어주세요.",
                "Virtual DOM의 동작 원리를 설명해주세요.",
              ]},
              { cat: "직무 적합성 질문", questions: [
                "결제 UX를 개선한 경험이 있으신가요?",
                "대용량 데이터를 다뤄본 경험이 있으신가요?",
                "팀 내에서 기술적 문제를 주도적으로 해결한 경험을 말씀해주세요.",
              ]},
              { cat: "인성 질문", questions: [
                "팀원과 의견 충돌이 있었을 때 어떻게 해결했나요?",
                "실패한 프로젝트 경험이 있으신가요? 어떻게 극복했나요?",
                "빠른 속도로 변화하는 기술 트렌드를 어떻게 따라가시나요?",
              ]},
            ].map((section) => (
              <div key={section.cat}>
                <div className="text-sm font-bold text-slate-700 mb-2">{section.cat}</div>
                <div className="space-y-2">
                  {section.questions.map((q, i) => (
                    <div key={i} className="bg-white border border-slate-200 rounded-xl p-3 flex items-start justify-between gap-3 hover:border-blue-300 transition-colors">
                      <p className="text-sm text-slate-700 flex-1">{q}</p>
                      <Button size="sm" variant="ghost" className="flex-shrink-0 text-xs gap-1 h-7 px-2 text-blue-600">
                        <Play className="size-3" /> 연습
                      </Button>
                    </div>
                  ))}
                </div>
              </div>
            ))}
          </TabsContent>

          {/* ─── 평가 기준 ─── */}
          <TabsContent value="evaluation" className="mt-6">
            <div className="grid md:grid-cols-2 gap-5 max-w-4xl">
              <div>
                <h2 className="font-bold text-slate-800 mb-4">답변 평가 항목</h2>
                <div className="space-y-3">
                  {[
                    { cat: "답변 내용", desc: "질문에 제대로 답했는가 · 핵심 포인트를 짚었는가" },
                    { cat: "직무 적합성", desc: "공고 요구 역량과 연결되는가 · 직무 관련 경험이 있는가" },
                    { cat: "구체성", desc: "경험, 수치, 사례가 있는가 · 추상적 말보다 구체적 사례 제시" },
                    { cat: "논리성", desc: "답변 구조가 자연스러운가 · STAR 기법 등 논리적 흐름" },
                    { cat: "표현력", desc: "말이 명확한가 · 전문 용어 적절 사용 · 필러 워드 최소화" },
                    { cat: "자신감", desc: "답변이 지나치게 위축되어 있지 않은가" },
                    { cat: "태도", desc: "면접 상황에 적절한 태도인가 · 경청, 예의" },
                    { cat: "시간 관리", desc: "답변 시간이 너무 짧거나 길지 않은가 (1-3분 권장)" },
                  ].map((e) => (
                    <div key={e.cat} className="bg-white border border-slate-200 rounded-xl p-3">
                      <div className="font-semibold text-slate-800 text-sm">{e.cat}</div>
                      <div className="text-xs text-slate-500 mt-0.5">{e.desc}</div>
                    </div>
                  ))}
                </div>
              </div>
              <div>
                <h2 className="font-bold text-slate-800 mb-4">답변 채점 기준</h2>
                <div className="space-y-3">
                  {[
                    { range: "90-100점", desc: "완성도 높은 답변 · 구체적 사례 + 수치 + 직무 연결 완벽", color: "bg-green-50 border-green-200 text-green-800" },
                    { range: "75-89점", desc: "방향은 맞고 주요 포인트 포함 · 구체성 보완 여지 있음", color: "bg-blue-50 border-blue-200 text-blue-800" },
                    { range: "60-74점", desc: "기본 방향은 맞으나 내용이 부족하거나 논리 연결 미흡", color: "bg-amber-50 border-amber-200 text-amber-800" },
                    { range: "40-59점", desc: "부분적으로만 답변 · 직무 연결성 부족", color: "bg-orange-50 border-orange-200 text-orange-800" },
                    { range: "0-39점", desc: "방향이 틀리거나 답변이 지나치게 짧고 구체성 전무", color: "bg-red-50 border-red-200 text-red-800" },
                  ].map((s) => (
                    <div key={s.range} className={`border rounded-xl p-3 ${s.color}`}>
                      <div className="font-black text-sm">{s.range}</div>
                      <div className="text-xs mt-0.5 opacity-80">{s.desc}</div>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          </TabsContent>

          {/* ─── AI 첨삭 ─── */}
          <TabsContent value="correction" className="mt-6 max-w-3xl space-y-5">
            <h2 className="font-bold text-slate-800">AI 답변 첨삭 예시</h2>
            <div className="bg-slate-100 rounded-xl p-4">
              <div className="text-sm font-bold text-slate-700 mb-1">질문</div>
              <p className="text-sm text-slate-800">{correctionExample.question}</p>
            </div>
            <div className="grid md:grid-cols-2 gap-4">
              <Card className="border-2 border-red-200 bg-red-50">
                <CardHeader className="pb-2">
                  <CardTitle className="text-sm text-red-700 flex items-center gap-1.5">
                    <ThumbsDown className="size-4" /> 원답변
                  </CardTitle>
                </CardHeader>
                <CardContent className="space-y-3">
                  <div className="text-sm text-slate-700 bg-white rounded-lg p-3 border border-red-100">
                    {correctionExample.original}
                  </div>
                  <div className="bg-red-100 rounded-lg p-3 text-xs text-red-700">
                    <div className="font-bold mb-1">AI 평가</div>
                    {correctionExample.aiEval}
                  </div>
                </CardContent>
              </Card>
              <Card className="border-2 border-green-200 bg-green-50">
                <CardHeader className="pb-2">
                  <CardTitle className="text-sm text-green-700 flex items-center gap-1.5">
                    <ThumbsUp className="size-4" /> AI 개선 답변
                  </CardTitle>
                </CardHeader>
                <CardContent className="space-y-3">
                  <div className="text-sm text-slate-700 bg-white rounded-lg p-3 border border-green-100 leading-relaxed">
                    {correctionExample.improved}
                  </div>
                  <div className="space-y-1">
                    {correctionExample.points.map((p) => (
                      <div key={p} className="flex items-center gap-1.5 text-xs text-green-700">
                        <CheckCircle2 className="size-3.5 flex-shrink-0" /> {p}
                      </div>
                    ))}
                  </div>
                </CardContent>
              </Card>
            </div>

            <div className="space-y-3">
              <h3 className="font-bold text-slate-800 text-sm">첨삭 지원 범위</h3>
              <div className="grid sm:grid-cols-2 gap-3">
                {[
                  { title: "면접 답변 첨삭", desc: "모의면접 답변에 대한 즉각 피드백 + 개선 답변 제공", credit: 1 },
                  { title: "자기소개서 첨삭", desc: "문항별 자기소개서 논리성·구체성·직무 적합성 첨삭", credit: 2 },
                  { title: "이력서 첨삭", desc: "이력서 항목별 표현 및 강점 부각 방향 개선", credit: 2 },
                  { title: "포트폴리오 첨삭", desc: "프로젝트 설명 구체화, 성과 수치화, 기술 강조 방향", credit: 2 },
                ].map((item) => (
                  <div key={item.title} className="bg-white border border-slate-200 rounded-xl p-4 hover:border-blue-300 transition-colors">
                    <div className="flex items-start justify-between mb-1">
                      <div className="font-semibold text-slate-800 text-sm">{item.title}</div>
                      <div className="flex items-center gap-0.5 text-xs text-amber-600">
                        <Award className="size-3" /> {item.credit}
                      </div>
                    </div>
                    <p className="text-xs text-slate-500">{item.desc}</p>
                  </div>
                ))}
              </div>
            </div>
          </TabsContent>

          {/* ─── 리포트 ─── */}
          <TabsContent value="report" className="mt-6 space-y-6 max-w-3xl">
            <div className="flex items-center justify-between">
              <h2 className="font-bold text-slate-800">면접 리포트</h2>
              <Badge className="bg-purple-100 text-purple-700">카카오페이 · 직무면접 · 2026-06-03</Badge>
            </div>
            <div className="grid md:grid-cols-3 gap-4">
              <div className="text-center bg-white border-2 border-blue-200 rounded-2xl p-6">
                <div className="text-5xl font-black text-blue-600">68</div>
                <div className="text-sm text-slate-500 mt-1">총점 (이전 +6점)</div>
              </div>
              <div className="text-center bg-white border border-slate-200 rounded-2xl p-6">
                <div className="text-3xl font-black text-slate-700">7</div>
                <div className="text-sm text-slate-500 mt-1">진행 질문 수</div>
              </div>
              <div className="text-center bg-white border border-slate-200 rounded-2xl p-6">
                <div className="text-3xl font-black text-green-600">24분</div>
                <div className="text-sm text-slate-500 mt-1">면접 진행 시간</div>
              </div>
            </div>

            <div className="space-y-3">
              {evaluationCategories.map((e) => (
                <div key={e.label} className="bg-white border border-slate-200 rounded-xl p-4 space-y-2">
                  <div className="flex items-center justify-between">
                    <span className="text-sm font-semibold text-slate-700">{e.label}</span>
                    <span className={`font-black text-sm ${e.score >= 75 ? "text-green-600" : e.score >= 60 ? "text-amber-600" : "text-red-500"}`}>
                      {e.score}점
                    </span>
                  </div>
                  <Progress value={e.score} className="h-2" />
                </div>
              ))}
            </div>

            <div className="bg-blue-50 border border-blue-200 rounded-xl p-5">
              <div className="font-bold text-blue-800 text-sm mb-3 flex items-center gap-2">
                <Brain className="size-4" /> AI 종합 피드백
              </div>
              <ul className="space-y-2 text-sm text-blue-700">
                <li className="flex items-start gap-2"><ChevronRight className="size-4 flex-shrink-0 mt-0.5" /> 직무 관련 기술 질문에서 방향성은 맞으나 구체적인 수치와 사례가 부족합니다.</li>
                <li className="flex items-start gap-2"><ChevronRight className="size-4 flex-shrink-0 mt-0.5" /> 논리적 답변 구조는 우수합니다. STAR 기법을 더 의식적으로 활용하면 좋겠습니다.</li>
                <li className="flex items-start gap-2"><ChevronRight className="size-4 flex-shrink-0 mt-0.5" /> 자신감 있는 표현을 더 사용하세요. "~것 같아요" 대신 "~했습니다" 형태로 답변을 마무리하는 연습이 필요합니다.</li>
              </ul>
            </div>
          </TabsContent>
        </Tabs>
      </div>
    </div>
  );
}
