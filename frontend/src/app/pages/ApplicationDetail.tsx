import { useState } from "react";
import { useParams, Link, useNavigate } from "react-router";
import { Button } from "../components/ui/button";
import { Card, CardContent } from "../components/ui/card";
import { Badge } from "../components/ui/badge";
import { Progress } from "../components/ui/progress";
import {
  ChevronLeft, Plus, Star, Archive, FileText, Building2, Target,
  Map, HelpCircle, MessageSquare, BarChart3, PenTool, AlertCircle,
  CheckCircle2, ChevronRight, Mic, Award, BookOpen, Clock, ArrowRight,
  ThumbsUp, ThumbsDown, Play, MoreHorizontal, Sparkles, Brain,
} from "lucide-react";

const sidebarApplications = [
  { id: "1", company: "카카오페이", job: "프론트엔드", score: 72, active: true },
  { id: "2", company: "네이버", job: "백엔드 개발", score: 58, active: false },
  { id: "3", company: "삼성SDS", job: "IT 솔루션", score: 65, active: false },
  { id: "4", company: "라인플러스", job: "풀스택 개발", score: 44, active: false },
  { id: "5", company: "토스", job: "iOS 개발자", score: 35, active: false },
];

const tabs = [
  { key: "job", label: "공고 분석", icon: FileText },
  { key: "company", label: "기업 분석", icon: Building2 },
  { key: "fit", label: "내 스펙 비교", icon: Target },
  { key: "strategy", label: "지원 전략", icon: Map },
  { key: "questions", label: "예상 질문", icon: HelpCircle },
  { key: "interview", label: "가상 면접", icon: MessageSquare },
  { key: "report", label: "면접 리포트", icon: BarChart3 },
  { key: "corrections", label: "첨삭 기록", icon: PenTool },
];

const specData = [
  { skill: "React", status: "보유", grade: "강점", match: true },
  { skill: "TypeScript", status: "일부 경험", grade: "보완 필요", match: false },
  { skill: "AWS", status: "없음", grade: "학습 필요", match: false },
  { skill: "Git 협업", status: "보유", grade: "강점", match: true },
  { skill: "REST API 연동", status: "보유", grade: "강점", match: true },
  { skill: "포트폴리오", status: "부족", grade: "보완 필요", match: false },
  { skill: "CI/CD", status: "일부 경험", grade: "보완 필요", match: false },
  { skill: "코드 리뷰", status: "경험 있음", grade: "무난", match: true },
];

const expectedQuestions = [
  { type: "기술", q: "React에서 상태 관리를 어떻게 설계하나요? Recoil과 Zustand를 비교해서 설명해주세요.", difficulty: "상" },
  { type: "기술", q: "TypeScript의 타입 시스템이 JavaScript와 다른 점은 무엇인가요?", difficulty: "중" },
  { type: "직무", q: "성능 최적화 경험이 있으신가요? 구체적인 사례를 들어주세요.", difficulty: "상" },
  { type: "인성", q: "팀원과 의견 충돌이 있었을 때 어떻게 해결했나요?", difficulty: "중" },
  { type: "지원동기", q: "카카오페이를 지원하게 된 이유는 무엇인가요?", difficulty: "중" },
  { type: "기술", q: "CSR과 SSR의 차이점과 각각의 장단점을 설명해주세요.", difficulty: "중" },
  { type: "상황", q: "짧은 기간 안에 처음 접하는 기술을 빠르게 익혀야 했던 경험이 있나요?", difficulty: "하" },
];

const correctionHistory = [
  {
    question: "React 프로젝트 경험을 설명해주세요.",
    original: "학교 프로젝트에서 React를 사용해서 게시판을 만들었습니다.",
    improved: "팀 프로젝트에서 React로 게시판을 구현했습니다. 저는 목록/작성/수정/삭제 화면을 맡았고, REST API 연동과 useState/useEffect 기반 상태 관리를 구현했습니다.",
    score: 45,
    newScore: 82,
    date: "2026-06-03",
  },
  {
    question: "TypeScript를 사용해본 경험이 있나요?",
    original: "TypeScript는 아직 많이 사용해보지 않았습니다.",
    improved: "현재 TypeScript를 학습 중이며, 개인 프로젝트에서 기본 타입과 인터페이스를 적용해봤습니다. 팀 프로젝트에도 도입하고 싶은 기술입니다.",
    score: 32,
    newScore: 68,
    date: "2026-06-02",
  },
];

const interviewReportData = [
  { label: "답변 내용", score: 75, feedback: "질문에 대한 답변 방향은 맞으나 구체성 보완 필요" },
  { label: "직무 적합성", score: 70, feedback: "공고 핵심 역량과 연결되는 경험 언급 보완 필요" },
  { label: "구체성", score: 60, feedback: "수치, 결과, 문제 해결 사례 부족" },
  { label: "논리성", score: 80, feedback: "답변 구조는 자연스러움" },
  { label: "표현력", score: 72, feedback: "말이 명확하나 전문 용어 활용 더 필요" },
  { label: "자신감", score: 65, feedback: "답변이 다소 위축된 경향" },
];

export function ApplicationDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState("fit");

  return (
    <div className="flex min-h-[calc(100vh-64px)] lg:h-[calc(100vh-80px)] bg-slate-100 overflow-hidden">
      {/* Left Sidebar */}
      <aside className="hidden lg:flex w-60 bg-slate-900 text-white flex-col flex-shrink-0 overflow-hidden">
        <div className="p-3 border-b border-slate-700 flex items-center justify-between">
          <button
            onClick={() => navigate("/applications")}
            className="flex items-center gap-1 text-xs text-slate-400 hover:text-white transition-colors"
          >
            <ChevronLeft className="size-3.5" /> 전체 목록
          </button>
          <button
            onClick={() => navigate("/applications/new")}
            className="size-6 rounded bg-blue-600 hover:bg-blue-500 flex items-center justify-center transition-colors"
          >
            <Plus className="size-3.5" />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto">
          <div className="p-3">
            <div className="text-[10px] text-slate-500 font-semibold uppercase tracking-wider mb-2">지원 건 목록</div>
            {sidebarApplications.map((a) => (
              <Link key={a.id} to={`/applications/${a.id}`}>
                <div className={`mb-1 p-2.5 rounded-lg cursor-pointer transition-colors ${a.id === id || (id === undefined && a.active) ? "bg-blue-600" : "hover:bg-slate-800"}`}>
                  <div className="text-xs font-semibold">{a.company}</div>
                  <div className="text-[10px] text-slate-400">{a.job}</div>
                  <div className="flex items-center gap-1.5 mt-1">
                    <div className="flex-1 h-1 bg-slate-700 rounded-full overflow-hidden">
                      <div
                        className="h-full bg-blue-400 rounded-full"
                        style={{ width: `${a.score}%` }}
                      />
                    </div>
                    <span className="text-[10px] text-blue-300">{a.score}점</span>
                  </div>
                </div>
              </Link>
            ))}
          </div>

          <div className="p-3 border-t border-slate-700 mt-2">
            <div className="text-[10px] text-slate-500 font-semibold uppercase tracking-wider mb-2">즐겨찾기</div>
            <div className="flex items-center gap-1.5 text-xs text-slate-400">
              <Star className="size-3 text-amber-400 fill-amber-400" />
              카카오페이 · 프론트엔드
            </div>
            <div className="flex items-center gap-1.5 text-xs text-slate-400 mt-1">
              <Star className="size-3 text-amber-400 fill-amber-400" />
              네이버 · 백엔드
            </div>
          </div>

          <div className="p-3 border-t border-slate-700">
            <div className="text-[10px] text-slate-500 font-semibold uppercase tracking-wider mb-2">보관함</div>
            <div className="flex items-center gap-1.5 text-xs text-slate-500">
              <Archive className="size-3" />
              보관된 항목 없음
            </div>
          </div>
        </div>
      </aside>

      {/* Center Main */}
      <main className="flex-1 w-full flex flex-col min-w-0 overflow-hidden">
        {/* Company header */}
        <div className="bg-white border-b border-slate-200 px-4 sm:px-5 py-3 flex flex-col sm:flex-row sm:items-center justify-between gap-3 flex-shrink-0">
          <div className="min-w-0">
            <div className="flex items-center gap-2 flex-wrap">
              <div className="size-7 rounded-lg bg-gradient-to-br from-blue-600 to-indigo-600 flex items-center justify-center text-white text-xs font-bold">카</div>
              <span className="font-bold text-slate-900 text-sm">카카오페이 · 프론트엔드 개발자</span>
              <Badge className="bg-blue-100 text-blue-700 text-xs">준비중</Badge>
            </div>
            <div className="text-xs text-slate-500 mt-0.5 sm:ml-9">2026-08-01 공고 · 경력 1-3년 · React, TypeScript, AWS</div>
          </div>
          <div className="flex items-center gap-2 self-end sm:self-auto">
            <button className="p-1.5 rounded-lg hover:bg-slate-100 transition-colors">
              <Star className="size-4 text-amber-400 fill-amber-400" />
            </button>
            <button className="p-1.5 rounded-lg hover:bg-slate-100 transition-colors">
              <MoreHorizontal className="size-4 text-slate-400" />
            </button>
          </div>
        </div>

        {/* Tabs */}
        <div className="bg-white border-b border-slate-200 flex overflow-x-auto flex-shrink-0">
          {tabs.map((tab) => (
            <button
              key={tab.key}
              onClick={() => setActiveTab(tab.key)}
              className={`flex items-center gap-1.5 px-4 py-2.5 text-xs font-medium whitespace-nowrap border-b-2 transition-colors ${
                activeTab === tab.key
                  ? "border-blue-600 text-blue-600 bg-blue-50"
                  : "border-transparent text-slate-600 hover:text-blue-600 hover:bg-slate-50"
              }`}
            >
              <tab.icon className="size-3.5" />
              {tab.label}
            </button>
          ))}
        </div>

        {/* Tab content */}
        <div className="flex-1 overflow-y-auto p-4 sm:p-5">
          {activeTab === "job" && (
            <div className="space-y-5 max-w-2xl">
              <div className="flex items-center justify-between">
                <h2 className="font-bold text-slate-800">공고 분석 결과</h2>
                <Badge className="bg-green-100 text-green-700">분석 완료</Badge>
              </div>
              <div className="grid md:grid-cols-2 gap-4">
                {[
                  { label: "기업명", value: "카카오페이" },
                  { label: "직무명", value: "프론트엔드 개발자" },
                  { label: "고용 형태", value: "정규직" },
                  { label: "경력 조건", value: "경력 1-3년" },
                  { label: "예상 난이도", value: "보통 ~ 어려움" },
                  { label: "채용 일자", value: "2026-08-01" },
                ].map((item) => (
                  <div key={item.label} className="flex items-center gap-3 p-3 bg-slate-50 rounded-xl text-sm">
                    <span className="text-slate-500 w-20 flex-shrink-0">{item.label}</span>
                    <span className="font-semibold text-slate-800">{item.value}</span>
                  </div>
                ))}
              </div>
              <div className="space-y-3">
                <div>
                  <div className="text-sm font-bold text-slate-700 mb-2">필수 기술</div>
                  <div className="flex flex-wrap gap-1.5">
                    {["React", "JavaScript", "TypeScript", "REST API", "Git"].map(s => (
                      <Badge key={s} className="bg-red-100 text-red-700 text-xs">{s}</Badge>
                    ))}
                  </div>
                </div>
                <div>
                  <div className="text-sm font-bold text-slate-700 mb-2">우대 기술</div>
                  <div className="flex flex-wrap gap-1.5">
                    {["AWS", "Next.js", "CI/CD", "성능 최적화", "디자인 시스템"].map(s => (
                      <Badge key={s} className="bg-blue-100 text-blue-700 text-xs">{s}</Badge>
                    ))}
                  </div>
                </div>
                <div>
                  <div className="text-sm font-bold text-slate-700 mb-2">담당 업무</div>
                  <ul className="space-y-1.5 text-sm text-slate-600">
                    {["결제 서비스 프론트엔드 개발 및 유지보수", "사용자 경험 개선을 위한 UI 최적화", "신규 기능 기획 및 구현", "성능 모니터링 및 개선"].map(t => (
                      <li key={t} className="flex items-start gap-2"><ChevronRight className="size-3.5 mt-0.5 text-blue-500 flex-shrink-0" />{t}</li>
                    ))}
                  </ul>
                </div>
              </div>
            </div>
          )}

          {activeTab === "company" && (
            <div className="space-y-5 max-w-2xl">
              <div className="flex items-center justify-between">
                <h2 className="font-bold text-slate-800">기업 현황 분석</h2>
                <Badge className="bg-purple-100 text-purple-700">AI 조사 완료</Badge>
              </div>
              <Card className="border border-blue-200 bg-blue-50">
                <CardContent className="p-4">
                  <div className="flex items-start gap-2">
                    <Sparkles className="size-4 text-blue-600 mt-0.5 flex-shrink-0" />
                    <p className="text-sm text-blue-800">
                      카카오페이는 최근 <strong>결제 UX 고도화</strong>와 <strong>글로벌 서비스 확장</strong>에 집중하고 있습니다. 면접에서는 단순한 React 경험보다 <strong>성능 최적화, 반응형 UI, 재사용 가능한 컴포넌트 설계 경험</strong>을 강조하는 것이 좋습니다.
                    </p>
                  </div>
                </CardContent>
              </Card>
              {[
                { title: "주요 사업", content: "간편결제, 송금, 대출, 보험 등 핀테크 종합 금융 서비스" },
                { title: "최근 이슈", content: "2025 해외 결제 서비스 확장 · 오프라인 결제 강화 · AI 금융 서비스 도입" },
                { title: "경쟁사", content: "네이버페이, 토스, 삼성페이, 애플페이" },
                { title: "면접 강조 포인트", content: "결제 UX 개선 경험, 성능 최적화, 대용량 데이터 처리, 팀 협업 프로세스" },
              ].map((item) => (
                <div key={item.title} className="p-4 bg-slate-50 rounded-xl border border-slate-200">
                  <div className="text-xs font-bold text-slate-500 uppercase tracking-wide mb-1.5">{item.title}</div>
                  <p className="text-sm text-slate-700">{item.content}</p>
                </div>
              ))}
            </div>
          )}

          {activeTab === "fit" && (
            <div className="space-y-5 max-w-2xl">
              <div className="flex items-center justify-between">
                <h2 className="font-bold text-slate-800">내 스펙 비교 분석</h2>
                <Badge className="bg-blue-100 text-blue-700 font-black">직무 적합도 72점</Badge>
              </div>
              <div className="space-y-2">
                {specData.map((s) => (
                  <div key={s.skill} className="flex flex-col gap-2 sm:flex-row sm:items-center sm:gap-3 p-3 bg-white border border-slate-200 rounded-xl text-sm">
                    <div className="min-w-0 flex-1 sm:flex-none sm:w-24">
                      <div className="font-medium text-slate-700 truncate">{s.skill}</div>
                      <div className="text-slate-500 text-xs sm:hidden">{s.status}</div>
                    </div>
                    <div className="hidden sm:block flex-1 text-slate-500 text-xs">{s.status}</div>
                    <div className="flex items-center gap-2 flex-shrink-0">
                      <span className={`px-2 py-0.5 rounded-full text-xs font-semibold whitespace-nowrap ${
                        s.grade === "강점" ? "bg-green-100 text-green-700" :
                        s.grade === "보완 필요" ? "bg-amber-100 text-amber-700" :
                        s.grade === "학습 필요" ? "bg-red-100 text-red-700" :
                        "bg-slate-100 text-slate-600"
                      }`}>{s.grade}</span>
                      {s.match ? <CheckCircle2 className="size-4 text-green-500 flex-shrink-0" /> : <AlertCircle className="size-4 text-amber-500 flex-shrink-0" />}
                    </div>
                  </div>
                ))}
              </div>
              <div className="bg-amber-50 border border-amber-200 rounded-xl p-4">
                <div className="font-bold text-amber-800 text-sm mb-2 flex items-center gap-2">
                  <Brain className="size-4" /> AI 종합 전략
                </div>
                <ol className="space-y-1.5 text-sm text-amber-700">
                  {[
                    "React 프로젝트에서 본인이 맡은 역할을 구체적으로 정리하세요",
                    "TypeScript 기본 문법과 React 프로젝트 적용 사례를 학습하세요",
                    "AWS S3/CloudFront 배포 경험을 토이 프로젝트로 보완하세요",
                    "포트폴리오에 문제 해결 사례와 수치 결과를 추가하세요",
                  ].map((t, i) => (
                    <li key={i} className="flex items-start gap-2">
                      <span className="font-black flex-shrink-0">{i + 1}.</span>
                      <span className="min-w-0 [word-break:keep-all]">{t}</span>
                    </li>
                  ))}
                </ol>
              </div>
            </div>
          )}

          {activeTab === "strategy" && (
            <div className="space-y-5 max-w-2xl">
              <h2 className="font-bold text-slate-800">지원 전략</h2>
              <div className="grid gap-4">
                {[
                  { phase: "단기 (1-2주)", color: "border-red-200 bg-red-50", items: ["TypeScript 기본 타입 학습 완료", "포트폴리오 문제 해결 사례 1개 추가", "카카오페이 서비스 직접 사용해보기"] },
                  { phase: "중기 (3-4주)", color: "border-amber-200 bg-amber-50", items: ["AWS 배포 토이 프로젝트 완성", "React 최적화 사례 정리", "모의 면접 5회 이상 진행"] },
                  { phase: "장기 (지원 전)", color: "border-green-200 bg-green-50", items: ["자기소개서 카카오페이 맞춤 작성", "지원서 제출 및 포트폴리오 링크 정리", "면접 답변 최종 점검"] },
                ].map((p) => (
                  <div key={p.phase} className={`border ${p.color} rounded-xl p-4`}>
                    <div className="font-bold text-slate-800 text-sm mb-2">{p.phase}</div>
                    <ul className="space-y-1.5">
                      {p.items.map((item) => (
                        <li key={item} className="flex items-start gap-2 text-sm text-slate-700">
                          <div className="size-4 rounded border-2 border-slate-300 flex-shrink-0 mt-0.5" />
                          {item}
                        </li>
                      ))}
                    </ul>
                  </div>
                ))}
              </div>
            </div>
          )}

          {activeTab === "questions" && (
            <div className="space-y-4 max-w-2xl">
              <div className="flex items-center justify-between">
                <h2 className="font-bold text-slate-800">예상 면접 질문</h2>
                <Button size="sm" variant="outline" className="gap-1.5 text-xs">
                  <Sparkles className="size-3.5" /> 질문 재생성
                </Button>
              </div>
              <div className="space-y-3">
                {expectedQuestions.map((q, i) => (
                  <div key={i} className="p-4 bg-white border border-slate-200 rounded-xl hover:border-blue-300 transition-colors">
                    <div className="flex items-start gap-3">
                      <div className="size-6 rounded-full bg-blue-600 text-white text-xs font-bold flex items-center justify-center flex-shrink-0 mt-0.5">
                        {i + 1}
                      </div>
                      <div className="flex-1">
                        <div className="flex items-center gap-2 mb-1.5 flex-wrap">
                          <Badge className={`text-[10px] px-1.5 py-0.5 ${
                            q.type === "기술" ? "bg-blue-100 text-blue-700" :
                            q.type === "인성" ? "bg-green-100 text-green-700" :
                            q.type === "직무" ? "bg-purple-100 text-purple-700" :
                            "bg-orange-100 text-orange-700"
                          }`}>{q.type}</Badge>
                          <Badge className={`text-[10px] px-1.5 py-0.5 ${
                            q.difficulty === "상" ? "bg-red-100 text-red-700" :
                            q.difficulty === "중" ? "bg-amber-100 text-amber-700" :
                            "bg-green-100 text-green-700"
                          }`}>난이도 {q.difficulty}</Badge>
                        </div>
                        <p className="text-sm text-slate-700">{q.q}</p>
                      </div>
                      <Button size="sm" variant="ghost" className="flex-shrink-0 text-xs gap-1 h-7 px-2">
                        <Play className="size-3" /> 연습
                      </Button>
                    </div>
                  </div>
                ))}
              </div>
              <Button className="w-full bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-700 hover:to-indigo-700 gap-2" onClick={() => setActiveTab("interview")}>
                <MessageSquare className="size-4" /> AI 가상 면접 시작하기
              </Button>
            </div>
          )}

          {activeTab === "interview" && (
            <div className="space-y-5 max-w-2xl">
              <h2 className="font-bold text-slate-800">AI 가상 면접</h2>
              <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-3">
                {[
                  { mode: "직무 면접", desc: "공고 기반 기술 질문", icon: "⚙️", recommended: true },
                  { mode: "인성 면접", desc: "협업, 갈등, 책임감", icon: "🤝", recommended: false },
                  { mode: "압박 면접", desc: "꼬리 질문, 반박", icon: "⚡", recommended: false },
                  { mode: "기업 맞춤", desc: "카카오페이 특화", icon: "🏢", recommended: true },
                ].map((m) => (
                  <button
                    key={m.mode}
                    className={`relative p-4 rounded-xl border-2 text-left transition-all hover:shadow-md ${m.recommended ? "border-blue-400 bg-blue-50" : "border-slate-200 bg-white hover:border-blue-300"}`}
                  >
                    {m.recommended && (
                      <div className="absolute -top-2 -right-2">
                        <Badge className="text-[9px] bg-blue-600 text-white px-1.5 py-0.5">추천</Badge>
                      </div>
                    )}
                    <div className="text-2xl mb-2">{m.icon}</div>
                    <div className="font-semibold text-slate-800 text-xs mb-0.5">{m.mode}</div>
                    <div className="text-[10px] text-slate-500">{m.desc}</div>
                  </button>
                ))}
              </div>
              <div className="bg-slate-900 rounded-2xl p-6 text-white">
                <div className="flex items-center gap-3 mb-4">
                  <div className="size-12 rounded-xl bg-gradient-to-br from-blue-500 to-indigo-500 flex items-center justify-center">
                    <Brain className="size-6" />
                  </div>
                  <div>
                    <div className="font-bold">AI 면접관</div>
                    <div className="text-xs text-slate-400">카카오페이 직무 면접 모드</div>
                  </div>
                </div>
                <div className="bg-white/10 rounded-xl p-4 text-sm mb-4">
                  <span className="text-blue-300">[면접관]</span> 안녕하세요. 카카오페이 프론트엔드 개발자 직무 면접을 시작하겠습니다. 먼저 간단한 자기소개를 해주세요.
                </div>
                <div className="flex flex-col sm:flex-row gap-3">
                  <Button className="flex-1 bg-blue-600 hover:bg-blue-500 gap-2">
                    <Mic className="size-4" /> 음성으로 답변
                  </Button>
                  <Button variant="outline" className="flex-1 border-white/30 text-white hover:bg-white/10 gap-2">
                    <FileText className="size-4" /> 텍스트로 답변
                  </Button>
                </div>
              </div>
            </div>
          )}

          {activeTab === "report" && (
            <div className="space-y-5 max-w-2xl">
              <div className="flex items-center justify-between">
                <h2 className="font-bold text-slate-800">면접 리포트</h2>
                <Badge className="bg-purple-100 text-purple-700">최근 1회차 (2026-06-01)</Badge>
              </div>
              <div className="text-center py-4">
                <div className="text-5xl font-black text-blue-600">68</div>
                <div className="text-sm text-slate-500 mt-1">/ 100점 · 이전 대비 +6점</div>
              </div>
              <div className="space-y-3">
                {interviewReportData.map((r) => (
                  <div key={r.label} className="p-3 bg-white border border-slate-200 rounded-xl space-y-2">
                    <div className="flex items-center justify-between text-sm">
                      <span className="font-semibold text-slate-700">{r.label}</span>
                      <span className={`font-black ${r.score >= 75 ? "text-green-600" : r.score >= 60 ? "text-amber-600" : "text-red-500"}`}>{r.score}점</span>
                    </div>
                    <Progress value={r.score} className="h-1.5" />
                    <p className="text-xs text-slate-500">{r.feedback}</p>
                  </div>
                ))}
              </div>
            </div>
          )}

          {activeTab === "corrections" && (
            <div className="space-y-5 max-w-2xl">
              <h2 className="font-bold text-slate-800">첨삭 기록</h2>
              <div className="space-y-5">
                {correctionHistory.map((c, i) => (
                  <div key={i} className="space-y-3">
                    <div className="flex items-center gap-2">
                      <div className="size-5 rounded-full bg-blue-600 text-white text-xs font-bold flex items-center justify-center flex-shrink-0">{i + 1}</div>
                      <div className="font-semibold text-slate-800 text-sm">Q. {c.question}</div>
                      <div className="ml-auto text-xs text-slate-400">{c.date}</div>
                    </div>
                    <div className="grid md:grid-cols-2 gap-3">
                      <div className="p-3 bg-red-50 border border-red-200 rounded-xl">
                        <div className="text-xs font-bold text-red-700 mb-1.5 flex items-center gap-1">
                          <ThumbsDown className="size-3" /> 원답변 ({c.score}점)
                        </div>
                        <p className="text-xs text-slate-600">{c.original}</p>
                      </div>
                      <div className="p-3 bg-green-50 border border-green-200 rounded-xl">
                        <div className="text-xs font-bold text-green-700 mb-1.5 flex items-center gap-1">
                          <ThumbsUp className="size-3" /> 개선 답변 ({c.newScore}점)
                        </div>
                        <p className="text-xs text-slate-600">{c.improved}</p>
                      </div>
                    </div>
                    <div className="flex items-center gap-2 text-xs text-slate-500">
                      <ArrowRight className="size-3" />
                      <span>점수 향상: </span>
                      <span className="font-black text-green-600">+{c.newScore - c.score}점</span>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      </main>

      {/* Right Panel */}
      <aside className="hidden xl:flex w-56 bg-white border-l border-slate-200 flex-col flex-shrink-0 overflow-y-auto">
        <div className="p-4 space-y-5">
          {/* Prep score */}
          <div>
            <div className="text-[10px] text-slate-400 font-semibold uppercase tracking-wider mb-2">준비도 점수</div>
            <div className="text-center py-2">
              <div className="text-4xl font-black text-blue-600">72</div>
              <div className="text-xs text-slate-500">/ 100점</div>
              <Progress value={72} className="mt-2 h-2" />
              <div className="text-[10px] text-green-600 mt-1">지난주 대비 +8점</div>
            </div>
          </div>

          {/* Missing skills */}
          <div>
            <div className="text-[10px] text-slate-400 font-semibold uppercase tracking-wider mb-2">부족 역량</div>
            <div className="space-y-1.5">
              {["TypeScript", "AWS 배포", "포트폴리오 구체화", "CI/CD"].map((s) => (
                <div key={s} className="flex items-center gap-1.5 text-[11px] bg-red-50 text-red-700 px-2 py-1.5 rounded-lg">
                  <AlertCircle className="size-3 flex-shrink-0" /> {s}
                </div>
              ))}
            </div>
          </div>

          {/* Recommended learning */}
          <div>
            <div className="text-[10px] text-slate-400 font-semibold uppercase tracking-wider mb-2">추천 학습</div>
            <div className="space-y-1.5">
              {[
                { title: "TypeScript 기초", type: "강의" },
                { title: "AWS 입문 가이드", type: "문서" },
                { title: "포트폴리오 작성법", type: "아티클" },
              ].map((l) => (
                <div key={l.title} className="flex items-start gap-1.5 text-[11px] text-slate-600 bg-slate-50 px-2 py-1.5 rounded-lg">
                  <BookOpen className="size-3 text-blue-500 flex-shrink-0 mt-0.5" />
                  <div>
                    <div>{l.title}</div>
                    <div className="text-[9px] text-slate-400">{l.type}</div>
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Next tasks */}
          <div>
            <div className="text-[10px] text-slate-400 font-semibold uppercase tracking-wider mb-2">다음 할 일</div>
            <div className="space-y-1.5">
              {[
                "TypeScript 학습 시작",
                "AWS 토이 프로젝트",
                "포트폴리오 수정",
                "모의 면접 3회 진행",
              ].map((t, i) => (
                <div key={t} className="flex items-start gap-1.5 text-[11px] text-slate-600">
                  <div className="size-3.5 rounded-sm border-2 border-slate-300 flex-shrink-0 mt-0.5" />
                  {t}
                </div>
              ))}
            </div>
          </div>

          {/* Credits */}
          <div className="bg-amber-50 rounded-xl p-3 border border-amber-200">
            <div className="text-[10px] text-amber-600 font-semibold uppercase tracking-wider mb-1.5">크레딧 사용</div>
            <div className="flex items-baseline gap-1">
              <span className="text-lg font-black text-amber-700">8</span>
              <span className="text-xs text-amber-600">/ 50 사용</span>
            </div>
            <Progress value={16} className="mt-1.5 h-1.5" />
          </div>
        </div>
      </aside>
    </div>
  );
}
