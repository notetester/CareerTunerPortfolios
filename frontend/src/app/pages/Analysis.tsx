import { useNavigate } from "react-router";
import { Badge } from "../components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "../components/ui/card";
import { Button } from "../components/ui/button";
import { Progress } from "../components/ui/progress";
import {
  TrendingUp, Target, BarChart3, ArrowUp, ArrowDown, AlertCircle,
  CheckCircle2, ChevronRight, Brain, Award, BookOpen, Briefcase,
} from "lucide-react";

const skillGapData = [
  { skill: "TypeScript", count: 4, total: 5, pct: 80 },
  { skill: "AWS 배포", count: 4, total: 5, pct: 80 },
  { skill: "포트폴리오 구체화", count: 3, total: 5, pct: 60 },
  { skill: "CI/CD", count: 3, total: 5, pct: 60 },
  { skill: "Spring Boot", count: 2, total: 5, pct: 40 },
  { skill: "Kotlin", count: 1, total: 5, pct: 20 },
];

const jobReadiness = [
  { job: "프론트엔드 개발자", readiness: 72, trend: "up", applied: 3 },
  { job: "풀스택 개발자", readiness: 58, trend: "up", applied: 1 },
  { job: "백엔드 개발자", readiness: 44, trend: "down", applied: 1 },
];

const scoreHistory = [
  { date: "5월 1주", score: 48 },
  { date: "5월 2주", score: 53 },
  { date: "5월 3주", score: 60 },
  { date: "5월 4주", score: 62 },
  { date: "6월 1주", score: 68 },
];

const applicationStats = [
  { company: "카카오페이", job: "프론트엔드", score: 72, interviews: 3, trend: "up" },
  { company: "네이버", job: "백엔드", score: 58, interviews: 2, trend: "up" },
  { company: "삼성SDS", job: "IT솔루션", score: 65, interviews: 4, trend: "down" },
  { company: "라인플러스", job: "풀스택", score: 44, interviews: 1, trend: "up" },
  { company: "토스", job: "iOS", score: 35, interviews: 0, trend: "neutral" },
];

export function AnalysisPage() {
  const navigate = useNavigate();
  return (
    <div className="bg-slate-50 min-h-screen">
      <div className="max-w-[1400px] mx-auto px-6 py-8 space-y-8">
        {/* Header */}
        <div>
          <h1 className="text-2xl font-black text-slate-900 flex items-center gap-2">
            <TrendingUp className="size-6 text-green-600" />
            취업 분석
          </h1>
          <p className="text-slate-500 text-sm mt-1">여러 지원 건을 종합한 AI 장기 취업 경향 분석 및 맞춤 전략</p>
        </div>

        {/* AI Strategy banner */}
        <Card className="border-2 border-blue-200 bg-gradient-to-r from-blue-50 to-indigo-50">
          <CardContent className="p-6">
            <div className="flex items-start gap-4">
              <div className="size-12 rounded-xl bg-gradient-to-br from-blue-600 to-indigo-600 flex items-center justify-center flex-shrink-0">
                <Brain className="size-6 text-white" />
              </div>
              <div>
                <div className="font-bold text-blue-900 mb-2">AI 장기 취업 전략 리포트</div>
                <p className="text-sm text-blue-700 mb-3">최근 5개 지원 건 분석 결과, 프론트엔드/웹 개발 직무 비율이 높고 TypeScript·AWS가 반복적으로 부족 역량으로 나타나고 있습니다. 면접에서는 프로젝트 성과 수치 표현이 약합니다.</p>
                <div className="space-y-1.5">
                  {[
                    "프론트엔드 직무 중심으로 지원 방향을 좁히는 것이 유리합니다",
                    "TypeScript 학습을 최우선 과제로 설정하세요",
                    "AWS 배포 경험을 간단한 토이 프로젝트로 포트폴리오에 추가하면 다수 공고에서 경쟁력이 올라갑니다",
                    "면접에서 '만들었다'보다 '어떤 문제를 해결했고 결과는 무엇이었다'를 중심으로 답변을 구성하세요",
                  ].map((s, i) => (
                    <div key={i} className="flex items-start gap-2 text-sm text-blue-800">
                      <span className="font-black text-blue-500 flex-shrink-0">{i + 1}.</span> {s}
                    </div>
                  ))}
                </div>
              </div>
            </div>
          </CardContent>
        </Card>

        <div className="grid lg:grid-cols-2 gap-6">
          {/* Skill gaps */}
          <Card className="border border-slate-200 bg-white">
            <CardHeader>
              <CardTitle className="text-base flex items-center gap-2">
                <AlertCircle className="size-4 text-red-500" />
                자주 부족한 역량 (5개 지원 건 기준)
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
              {skillGapData.map((s) => (
                <div key={s.skill} className="space-y-1.5">
                  <div className="flex items-center justify-between text-sm">
                    <span className="font-medium text-slate-700">{s.skill}</span>
                    <span className="text-slate-500 text-xs">{s.count}/{s.total}건 부족</span>
                  </div>
                  <Progress value={s.pct} className="h-2" />
                </div>
              ))}
            </CardContent>
          </Card>

          {/* Job readiness */}
          <Card className="border border-slate-200 bg-white">
            <CardHeader>
              <CardTitle className="text-base flex items-center gap-2">
                <Target className="size-4 text-blue-600" />
                직무별 준비도
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              {jobReadiness.map((j) => (
                <div key={j.job} className="space-y-1.5">
                  <div className="flex items-center justify-between">
                    <div>
                      <span className="font-semibold text-slate-800 text-sm">{j.job}</span>
                      <span className="text-xs text-slate-400 ml-2">지원 {j.applied}건</span>
                    </div>
                    <div className="flex items-center gap-1">
                      {j.trend === "up" ? (
                        <ArrowUp className="size-3.5 text-green-600" />
                      ) : (
                        <ArrowDown className="size-3.5 text-red-500" />
                      )}
                      <span className={`font-black text-sm ${j.readiness >= 70 ? "text-green-600" : j.readiness >= 50 ? "text-amber-600" : "text-red-500"}`}>
                        {j.readiness}점
                      </span>
                    </div>
                  </div>
                  <Progress value={j.readiness} className="h-2" />
                </div>
              ))}
            </CardContent>
          </Card>

          {/* Score history bar chart (visual) */}
          <Card className="border border-slate-200 bg-white">
            <CardHeader>
              <CardTitle className="text-base flex items-center gap-2">
                <BarChart3 className="size-4 text-purple-600" />
                면접 점수 변화
              </CardTitle>
            </CardHeader>
            <CardContent>
              <div className="flex items-end gap-3 h-32 pt-2">
                {scoreHistory.map((s, i) => (
                  <div key={s.date} className="flex-1 flex flex-col items-center gap-1">
                    <div className="text-xs font-black text-slate-700">{s.score}</div>
                    <div
                      className={`w-full rounded-t-lg transition-all ${i === scoreHistory.length - 1 ? "bg-blue-600" : "bg-blue-200"}`}
                      style={{ height: `${(s.score / 100) * 100}px` }}
                    />
                    <div className="text-[9px] text-slate-400 text-center whitespace-nowrap">{s.date}</div>
                  </div>
                ))}
              </div>
              <div className="mt-3 flex items-center gap-2 text-xs text-green-600">
                <ArrowUp className="size-3.5" />
                <span>5주간 +20점 상승 (48점 → 68점)</span>
              </div>
            </CardContent>
          </Card>

          {/* Application trends */}
          <Card className="border border-slate-200 bg-white">
            <CardHeader>
              <CardTitle className="text-base flex items-center gap-2">
                <Briefcase className="size-4 text-orange-600" />
                내 지원 경향
              </CardTitle>
            </CardHeader>
            <CardContent>
              <div className="space-y-2">
                {applicationStats.map((a) => (
                  <div key={a.company} className="flex items-center gap-3 p-2.5 rounded-lg bg-slate-50 hover:bg-slate-100 transition-colors text-sm">
                    <div className="size-7 rounded-lg bg-gradient-to-br from-blue-600 to-indigo-600 text-white text-xs font-bold flex items-center justify-center flex-shrink-0">
                      {a.company[0]}
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="font-semibold text-slate-800 text-xs">{a.company}</div>
                      <div className="text-[11px] text-slate-500">{a.job} · 면접 {a.interviews}회</div>
                    </div>
                    <div className="flex items-center gap-1">
                      {a.trend === "up" ? <ArrowUp className="size-3 text-green-500" /> : a.trend === "down" ? <ArrowDown className="size-3 text-red-500" /> : null}
                      <span className={`font-black text-xs ${a.score >= 70 ? "text-green-600" : a.score >= 50 ? "text-amber-600" : "text-red-500"}`}>{a.score}점</span>
                    </div>
                  </div>
                ))}
              </div>
            </CardContent>
          </Card>
        </div>

        {/* Recommended direction */}
        <Card className="border border-slate-200 bg-white">
          <CardHeader>
            <CardTitle className="text-base flex items-center gap-2">
              <BookOpen className="size-4 text-teal-600" />
              AI 추천 학습 로드맵
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="grid md:grid-cols-3 gap-4">
              {[
                {
                  phase: "이번 주 즉시 시작",
                  color: "border-red-200 bg-red-50",
                  textColor: "text-red-800",
                  items: [
                    "TypeScript 기초 강의 수강 (Udemy)",
                    "React + TypeScript 리팩터링 프로젝트",
                    "포트폴리오 1개 항목 성과 수치화",
                  ],
                },
                {
                  phase: "2-3주 내",
                  color: "border-amber-200 bg-amber-50",
                  textColor: "text-amber-800",
                  items: [
                    "AWS S3 + CloudFront 토이 프로젝트",
                    "GitHub Actions CI/CD 파이프라인 구성",
                    "면접 답변 STAR 기법 연습 5회",
                  ],
                },
                {
                  phase: "1달 내",
                  color: "border-green-200 bg-green-50",
                  textColor: "text-green-800",
                  items: [
                    "TypeScript 중급 (제네릭, 유틸리티 타입)",
                    "오픈소스 프로젝트 기여 1건",
                    "카카오페이 맞춤 자기소개서 완성",
                  ],
                },
              ].map((phase) => (
                <div key={phase.phase} className={`border ${phase.color} rounded-xl p-4`}>
                  <div className={`font-bold text-sm mb-3 ${phase.textColor}`}>{phase.phase}</div>
                  <ul className="space-y-2">
                    {phase.items.map((item) => (
                      <li key={item} className="flex items-start gap-2 text-xs text-slate-700">
                        <div className="size-3.5 rounded-sm border-2 border-slate-300 flex-shrink-0 mt-0.5" />
                        {item}
                      </li>
                    ))}
                  </ul>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
