import { useState } from "react";
import { Link, useNavigate } from "react-router";
import { Button } from "../components/ui/button";
import { Card, CardContent } from "../components/ui/card";
import { Badge } from "../components/ui/badge";
import { Progress } from "../components/ui/progress";
import { Input } from "../components/ui/input";
import {
  Plus, Search, Filter, Calendar, ChevronRight, FileText,
  Briefcase, Clock, Star, Archive, MoreHorizontal, Building2,
  SortAsc,
} from "lucide-react";

const applications = [
  { id: "1", company: "카카오페이", job: "프론트엔드 개발자", date: "2026-08-01", score: 72, status: "준비중", phase: "스펙비교완료", tags: ["React", "TypeScript", "AWS"], starred: true, desc: "결제 플랫폼 개발 · 경력 1-3년" },
  { id: "2", company: "네이버", job: "백엔드 개발자", date: "2026-07-20", score: 58, status: "면접연습중", phase: "가상면접진행", tags: ["Java", "Spring", "MySQL"], starred: true, desc: "검색 인프라 개발 · 신입/경력" },
  { id: "3", company: "삼성SDS", job: "IT 솔루션 개발", date: "2026-07-15", score: 65, status: "분석완료", phase: "전략수립완료", tags: ["Java", "Oracle", "Linux"], starred: false, desc: "ERP/SCM 시스템 개발 · 신입" },
  { id: "4", company: "라인플러스", job: "풀스택 개발자", date: "2026-07-10", score: 44, status: "공고입력", phase: "공고분석필요", tags: ["Node.js", "React", "MongoDB"], starred: false, desc: "글로벌 서비스 개발 · 경력 3년+" },
  { id: "5", company: "토스", job: "iOS 개발자", date: "2026-07-05", score: 35, status: "분석중", phase: "기업조사중", tags: ["Swift", "iOS", "RxSwift"], starred: false, desc: "핀테크 앱 개발 · 신입/경력" },
  { id: "6", company: "당근", job: "안드로이드 개발자", date: "2026-06-30", score: 28, status: "공고수집", phase: "공고입력대기", tags: ["Kotlin", "Android", "Jetpack"], starred: false, desc: "로컬 커머스 서비스 · 경력 2년+" },
];

const statusOptions = ["전체", "공고입력", "분석완료", "준비중", "면접연습중", "보관함"];

export function ApplicationsPage() {
  const [search, setSearch] = useState("");
  const [activeStatus, setActiveStatus] = useState("전체");
  const navigate = useNavigate();

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
            onClick={() => navigate("/applications/new")}
          >
            <Plus className="size-4" />
            새 지원 건 만들기
          </Button>
        </div>

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
      </div>
    </div>
  );
}
