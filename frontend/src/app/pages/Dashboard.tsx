import { Link, useNavigate } from "react-router";
import { Button } from "../components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "../components/ui/card";
import { Badge } from "../components/ui/badge";
import { Progress } from "../components/ui/progress";
import {
  Plus, Briefcase, MessageSquare, TrendingUp, Award, ArrowRight,
  FileText, BarChart3, Clock, CheckCircle2, AlertCircle, ChevronRight,
  Target, BookOpen, Bell, Calendar, Flame,
} from "lucide-react";

const recentApplications = [
  { id: "1", company: "카카오페이", job: "프론트엔드 개발자", date: "2026-08-01", score: 72, status: "준비중", statusColor: "bg-blue-100 text-blue-700", tags: ["React", "TypeScript"] },
  { id: "2", company: "네이버", job: "백엔드 개발자", date: "2026-07-20", score: 58, status: "분석 완료", statusColor: "bg-purple-100 text-purple-700", tags: ["Java", "Spring"] },
  { id: "3", company: "삼성SDS", job: "IT 솔루션 개발", date: "2026-07-15", score: 65, status: "면접 연습 중", statusColor: "bg-orange-100 text-orange-700", tags: ["Java", "Oracle"] },
  { id: "4", company: "라인플러스", job: "풀스택 개발자", date: "2026-07-10", score: 44, status: "공고 입력 완료", statusColor: "bg-slate-100 text-slate-700", tags: ["Node.js", "React"] },
  { id: "5", company: "토스", job: "iOS 개발자", date: "2026-07-05", score: 35, status: "공고 분석 중", statusColor: "bg-amber-100 text-amber-700", tags: ["Swift", "iOS"] },
];

const todoItems = [
  { done: true, task: "카카오페이 공고문 AI 분석 완료", time: "오늘" },
  { done: true, task: "내 스펙 비교 분석 확인", time: "오늘" },
  { done: false, task: "TypeScript 학습 시작 (추천 강의)", time: "이번 주" },
  { done: false, task: "가상 면접 3회 진행 (직무 모드)", time: "이번 주" },
  { done: false, task: "포트폴리오 문제 해결 사례 추가", time: "이번 주" },
  { done: false, task: "AWS 토이 프로젝트 시작", time: "이번 달" },
];

const activities = [
  { type: "analysis", content: "카카오페이 공고문 분석 완료 · 직무 적합도 72점", time: "30분 전", icon: FileText, color: "text-blue-600" },
  { type: "interview", content: "네이버 백엔드 · 직무 면접 1회 완료 (총점 68점)", time: "2시간 전", icon: MessageSquare, color: "text-purple-600" },
  { type: "correction", content: "자기소개서 첨삭 완료 · 3문항 개선", time: "어제", icon: FileText, color: "text-green-600" },
  { type: "analysis", content: "삼성SDS 기업 현황 조사 완료", time: "어제", icon: Target, color: "text-orange-600" },
];

export function DashboardPage() {
  const navigate = useNavigate();

  return (
    <div className="bg-slate-50 min-h-screen">
      <div className="max-w-[1400px] mx-auto px-6 py-8 space-y-8">

        {/* Welcome bar */}
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div>
            <div className="flex items-center gap-2 text-sm text-slate-500 mb-1">
              <Bell className="size-4" />
              <span>새로운 알림 3건이 있습니다.</span>
            </div>
            <h1 className="text-2xl font-black text-slate-900">안녕하세요, 김지원 님 👋</h1>
            <p className="text-slate-500 mt-1 text-sm">카카오페이 면접 준비가 <strong className="text-blue-600">72%</strong> 완료됐습니다. 오늘도 열심히 준비해봐요!</p>
          </div>
          <Button
            className="bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-700 hover:to-indigo-700 gap-2"
            onClick={() => navigate("/applications")}
          >
            <Plus className="size-4" />
            새 지원 건 만들기
          </Button>
        </div>

        {/* Stats cards */}
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          {[
            { icon: Briefcase, label: "활성 지원 건", value: "5", sub: "이번 달 3건 추가", color: "from-blue-500 to-cyan-500", bg: "bg-blue-50" },
            { icon: MessageSquare, label: "총 모의면접", value: "12", sub: "이번 주 3회", color: "from-purple-500 to-violet-500", bg: "bg-purple-50" },
            { icon: Award, label: "보유 크레딧", value: "42", sub: "금주 8크레딧 사용", color: "from-amber-500 to-orange-500", bg: "bg-amber-50" },
            { icon: TrendingUp, label: "평균 적합도", value: "54점", sub: "지난달 대비 +8점", color: "from-green-500 to-emerald-500", bg: "bg-green-50" },
          ].map((s) => (
            <Card key={s.label} className="border border-slate-200 bg-white hover:shadow-md transition-shadow">
              <CardContent className="p-5">
                <div className="flex items-start justify-between">
                  <div>
                    <div className="text-sm text-slate-500 mb-1">{s.label}</div>
                    <div className="text-3xl font-black text-slate-900">{s.value}</div>
                    <div className="text-xs text-slate-400 mt-1">{s.sub}</div>
                  </div>
                  <div className={`size-10 rounded-xl bg-gradient-to-br ${s.color} flex items-center justify-center`}>
                    <s.icon className="size-5 text-white" />
                  </div>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>

        <div className="grid lg:grid-cols-3 gap-6">
          {/* Recent applications */}
          <div className="lg:col-span-2 space-y-4">
            <div className="flex items-center justify-between">
              <h2 className="font-bold text-slate-900 text-lg">내 지원 건</h2>
              <Link to="/applications" className="text-sm text-blue-600 hover:text-blue-700 flex items-center gap-1">
                전체 보기 <ArrowRight className="size-3.5" />
              </Link>
            </div>
            <div className="space-y-3">
              {recentApplications.map((app) => (
                <Link to={`/applications/${app.id}`} key={app.id}>
                  <Card className="border border-slate-200 bg-white hover:border-blue-300 hover:shadow-md transition-all cursor-pointer">
                    <CardContent className="p-4">
                      <div className="flex items-center gap-4">
                        <div className="size-10 rounded-xl bg-gradient-to-br from-blue-600 to-indigo-600 flex items-center justify-center text-white font-bold text-sm flex-shrink-0">
                          {app.company[0]}
                        </div>
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center gap-2 flex-wrap">
                            <span className="font-semibold text-slate-800 text-sm">{app.company}</span>
                            <span className="text-slate-400 text-xs">·</span>
                            <span className="text-slate-600 text-sm">{app.job}</span>
                            <Badge className={`text-xs ${app.statusColor}`}>{app.status}</Badge>
                          </div>
                          <div className="flex items-center gap-3 mt-1.5">
                            <div className="flex items-center gap-1.5 flex-1">
                              <div className="text-xs text-slate-400">적합도</div>
                              <Progress value={app.score} className="h-1.5 flex-1 max-w-24" />
                              <span className="text-xs font-semibold text-blue-600">{app.score}점</span>
                            </div>
                            <div className="flex gap-1">
                              {app.tags.map((tag) => (
                                <span key={tag} className="text-[10px] bg-slate-100 text-slate-600 px-1.5 py-0.5 rounded">{tag}</span>
                              ))}
                            </div>
                            <div className="text-xs text-slate-400 flex items-center gap-1 flex-shrink-0">
                              <Calendar className="size-3" />
                              {app.date}
                            </div>
                          </div>
                        </div>
                        <ChevronRight className="size-4 text-slate-400 flex-shrink-0" />
                      </div>
                    </CardContent>
                  </Card>
                </Link>
              ))}
            </div>

            {/* Activity timeline */}
            <div className="mt-6">
              <h2 className="font-bold text-slate-900 text-lg mb-4">최근 활동</h2>
              <Card className="border border-slate-200 bg-white">
                <CardContent className="p-5 space-y-4">
                  {activities.map((a, i) => (
                    <div key={i} className="flex items-start gap-3">
                      <div className={`size-8 rounded-lg bg-slate-100 flex items-center justify-center flex-shrink-0 mt-0.5`}>
                        <a.icon className={`size-4 ${a.color}`} />
                      </div>
                      <div className="flex-1">
                        <div className="text-sm text-slate-700">{a.content}</div>
                        <div className="text-xs text-slate-400 mt-0.5">{a.time}</div>
                      </div>
                    </div>
                  ))}
                </CardContent>
              </Card>
            </div>
          </div>

          {/* Right column */}
          <div className="space-y-5">
            {/* Today's tasks */}
            <Card className="border border-slate-200 bg-white">
              <CardHeader className="pb-3">
                <CardTitle className="text-base flex items-center gap-2">
                  <Flame className="size-4 text-orange-500" />
                  오늘의 할 일
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-2.5">
                {todoItems.map((t, i) => (
                  <div key={i} className="flex items-start gap-2.5">
                    <div className={`size-4 rounded flex items-center justify-center flex-shrink-0 mt-0.5 ${t.done ? "bg-green-500" : "border-2 border-slate-300"}`}>
                      {t.done && <CheckCircle2 className="size-3 text-white" />}
                    </div>
                    <div className="flex-1">
                      <div className={`text-sm ${t.done ? "line-through text-slate-400" : "text-slate-700"}`}>{t.task}</div>
                      <div className="text-xs text-slate-400 mt-0.5">{t.time}</div>
                    </div>
                  </div>
                ))}
              </CardContent>
            </Card>

            {/* Credits */}
            <Card className="border border-amber-200 bg-amber-50">
              <CardHeader className="pb-3">
                <CardTitle className="text-base flex items-center gap-2 text-amber-800">
                  <Award className="size-4 text-amber-600" />
                  크레딧 현황
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-3">
                <div className="text-center py-2">
                  <div className="text-4xl font-black text-amber-700">42</div>
                  <div className="text-sm text-amber-600">/ 50 크레딧 잔여</div>
                  <Progress value={84} className="mt-2 h-2" />
                </div>
                <div className="space-y-1.5 text-xs text-amber-700">
                  <div className="flex justify-between">
                    <span>이번 달 사용</span>
                    <span className="font-semibold">8 크레딧</span>
                  </div>
                  <div className="flex justify-between">
                    <span>다음 충전일</span>
                    <span className="font-semibold">2026-07-01</span>
                  </div>
                </div>
                <Button size="sm" variant="outline" className="w-full border-amber-400 text-amber-700 hover:bg-amber-100" onClick={() => navigate("/pricing")}>
                  크레딧 충전하기
                </Button>
              </CardContent>
            </Card>

            {/* Quick links */}
            <Card className="border border-slate-200 bg-white">
              <CardHeader className="pb-3">
                <CardTitle className="text-base">빠른 메뉴</CardTitle>
              </CardHeader>
              <CardContent className="grid grid-cols-2 gap-2">
                {[
                  { label: "새 지원 건", icon: Plus, href: "/applications", color: "text-blue-600" },
                  { label: "가상 면접", icon: MessageSquare, href: "/interview", color: "text-purple-600" },
                  { label: "취업 분석", icon: BarChart3, href: "/analysis", color: "text-green-600" },
                  { label: "커뮤니티", icon: BookOpen, href: "/community", color: "text-orange-600" },
                ].map((m) => (
                  <Link key={m.label} to={m.href}>
                    <div className="flex flex-col items-center gap-1.5 p-3 rounded-xl bg-slate-50 hover:bg-blue-50 transition-colors cursor-pointer">
                      <m.icon className={`size-5 ${m.color}`} />
                      <span className="text-xs font-medium text-slate-700">{m.label}</span>
                    </div>
                  </Link>
                ))}
              </CardContent>
            </Card>

            {/* Top skills needed */}
            <Card className="border border-slate-200 bg-white">
              <CardHeader className="pb-3">
                <CardTitle className="text-base flex items-center gap-2">
                  <AlertCircle className="size-4 text-red-500" />
                  자주 부족한 역량
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-2">
                {[
                  { skill: "TypeScript", count: 4, total: 5 },
                  { skill: "AWS 배포", count: 3, total: 5 },
                  { skill: "포트폴리오", count: 3, total: 5 },
                  { skill: "Spring Boot", count: 2, total: 5 },
                ].map((s) => (
                  <div key={s.skill} className="space-y-1">
                    <div className="flex justify-between text-xs">
                      <span className="text-slate-700 font-medium">{s.skill}</span>
                      <span className="text-slate-400">{s.count}/{s.total}건</span>
                    </div>
                    <Progress value={(s.count / s.total) * 100} className="h-1.5" />
                  </div>
                ))}
              </CardContent>
            </Card>
          </div>
        </div>
      </div>
    </div>
  );
}
