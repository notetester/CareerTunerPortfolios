import { useState } from "react";
import { useSearchParams } from "react-router";
import { Badge } from "../components/ui/badge";
import { Button } from "../components/ui/button";
import { Card, CardContent } from "../components/ui/card";
import { Input } from "../components/ui/input";
import {
  Users, Search, Flame, Clock, Eye, Heart, MessageSquare,
  Plus, ChevronRight, Pin,
} from "lucide-react";

const categories = [
  { id: "all", label: "전체", matches: ["전체"] },
  { id: "hired", label: "취업 후기", matches: ["취업 후기"] },
  { id: "interview", label: "면접 후기", matches: ["면접 후기"] },
  { id: "questions", label: "직무별 질문 공유", matches: ["직무별 질문", "직무별 질문 공유"] },
  { id: "strategy", label: "합격 전략 게시판", matches: ["합격 전략", "합격 전략 게시판"] },
  { id: "certificates", label: "자격증 후기", matches: ["자격증 후기"] },
  { id: "portfolio", label: "포트폴리오 피드백", matches: ["포트폴리오 피드백"] },
  { id: "free", label: "자유게시판", matches: ["자유게시판"] },
];

const posts = [
  { id: 1, cat: "면접 후기", company: "카카오페이", job: "프론트엔드", title: "카카오페이 프론트엔드 1차 합격 후기 (기술 면접 질문 총정리)", author: "익명", date: "2026-06-03", views: 3847, likes: 134, comments: 28, hot: true, pinned: true },
  { id: 2, cat: "합격 전략", company: "네이버", job: "백엔드", title: "네이버 신입 백엔드 합격 후기 - 6개월 준비 과정 총정리", author: "박**준", date: "2026-06-02", views: 6129, likes: 341, comments: 54, hot: true, pinned: false },
  { id: 3, cat: "직무별 질문", company: "", job: "프론트엔드", title: "[2026 최신] 대기업 프론트엔드 면접 기술 질문 모음 (React/TS)", author: "이**현", date: "2026-06-01", views: 9293, likes: 562, comments: 87, hot: true, pinned: false },
  { id: 4, cat: "취업 후기", company: "삼성SDS", job: "IT솔루션", title: "삼성SDS IT솔루션 신입 최종 합격 스펙 + 준비 방법 공유", author: "익명", date: "2026-05-31", views: 4512, likes: 197, comments: 33, hot: false, pinned: false },
  { id: 5, cat: "자격증 후기", company: "", job: "", title: "정보처리기사 필기 독학 합격 후기 + 요약 노트 공유", author: "최**은", date: "2026-05-30", views: 2183, likes: 88, comments: 21, hot: false, pinned: false },
  { id: 6, cat: "포트폴리오 피드백", company: "", job: "프론트엔드", title: "포트폴리오 피드백 부탁드립니다 (React 토이 프로젝트)", author: "익명", date: "2026-05-29", views: 782, likes: 15, comments: 12, hot: false, pinned: false },
  { id: 7, cat: "면접 후기", company: "토스", job: "iOS", title: "토스 iOS 개발자 면접 후기 + 실제 받은 질문 공유", author: "익명", date: "2026-05-28", views: 3217, likes: 156, comments: 41, hot: false, pinned: false },
  { id: 8, cat: "자유게시판", company: "", job: "", title: "CareerTuner 쓰다가 카카오 면접 합격했어요 🎉 추천합니다", author: "익명", date: "2026-05-27", views: 2923, likes: 89, comments: 18, hot: false, pinned: false },
  { id: 9, cat: "합격 전략", company: "LINE", job: "백엔드", title: "LINE 백엔드 개발자 직무 면접 전략 - 준비 방법과 합격 팁", author: "성**민", date: "2026-05-26", views: 4788, likes: 224, comments: 36, hot: false, pinned: false },
  { id: 10, cat: "직무별 질문", company: "", job: "공기업", title: "2026 공기업 전산직 NCS + 면접 질문 완전 정리", author: "김**진", date: "2026-05-25", views: 11234, likes: 687, comments: 102, hot: true, pinned: false },
];

const topPosts = [
  { title: "카카오 전 직군 면접 후기 모음 (2026)", views: 15293 },
  { title: "네이버/카카오/라인 면접 질문 비교 총정리", views: 12847 },
  { title: "정보처리기사 독학 합격 가이드 2026", views: 10532 },
  { title: "프론트엔드 포트폴리오 작성 가이드", views: 9284 },
  { title: "취업 준비 6개월 로드맵 공유", views: 8912 },
];

export function CommunityPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [search, setSearch] = useState("");
  const activeCategory = categories.find((cat) => cat.id === (searchParams.get("cat") ?? "all")) ?? categories[0];

  const filtered = posts.filter((p) => {
    const matchCat = activeCategory.id === "all" || activeCategory.matches.includes(p.cat);
    const matchSearch = search === "" || p.title.includes(search) || p.company.includes(search);
    return matchCat && matchSearch;
  });

  return (
    <div className="bg-slate-50 min-h-screen">
      <div className="max-w-[1400px] mx-auto px-6 py-8 space-y-6">
        {/* Header */}
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div>
            <h1 className="text-2xl font-black text-slate-900 flex items-center gap-2">
              <Users className="size-6 text-orange-600" />
              커뮤니티
            </h1>
            <p className="text-slate-500 text-sm mt-1">취업 후기, 면접 후기, 직무별 질문 공유 게시판</p>
          </div>
          <Button className="bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-700 hover:to-indigo-700 gap-2">
            <Plus className="size-4" />
            글 작성하기
          </Button>
        </div>

        <div className="grid lg:grid-cols-4 gap-6">
          {/* Main content */}
          <div className="lg:col-span-3 space-y-4">
            {/* Search + category filter */}
            <div className="bg-white border border-slate-200 rounded-xl p-4 space-y-3">
              <div className="relative">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 size-4 text-slate-400" />
                <Input
                  placeholder="제목, 기업명, 직무 검색..."
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                  className="pl-9 h-9"
                />
              </div>
              <div className="flex gap-2 flex-wrap">
                {categories.map((cat) => (
                  <button
                    key={cat.id}
                    onClick={() => setSearchParams(cat.id === "all" ? {} : { cat: cat.id })}
                    className={`px-3 py-1.5 rounded-full text-xs font-semibold transition-colors ${
                      activeCategory.id === cat.id
                        ? "bg-blue-600 text-white"
                        : "bg-slate-100 text-slate-600 hover:bg-slate-200"
                    }`}
                  >
                    {cat.label}
                  </button>
                ))}
              </div>
            </div>

            {/* Stats bar */}
            <div className="grid grid-cols-3 gap-3">
              {[
                { icon: MessageSquare, label: "전체 게시글", value: "2,847" },
                { icon: Users, label: "오늘 방문자", value: "1,293" },
                { icon: Flame, label: "이번 주 HOT", value: "12" },
              ].map((s) => (
                <div key={s.label} className="bg-white border border-slate-200 rounded-xl p-3 flex items-center gap-3">
                  <s.icon className="size-4 text-blue-600" />
                  <div>
                    <div className="font-black text-slate-900 text-sm">{s.value}</div>
                    <div className="text-[10px] text-slate-400">{s.label}</div>
                  </div>
                </div>
              ))}
            </div>

            {/* Post list */}
            <div className="space-y-2">
              {filtered.map((post) => (
                <div
                  key={post.id}
                  className="bg-white border border-slate-200 hover:border-blue-300 hover:shadow-md transition-all rounded-xl p-4 cursor-pointer"
                >
                  <div className="flex items-start gap-3">
                    <div className="flex flex-col gap-1 flex-shrink-0 items-start">
                      {post.pinned && <Pin className="size-3.5 text-blue-600" />}
                      <Badge className={`text-[10px] px-2 py-0.5 ${
                        post.cat === "면접 후기" ? "bg-blue-100 text-blue-700" :
                        post.cat === "합격 전략" ? "bg-green-100 text-green-700" :
                        post.cat === "직무별 질문" ? "bg-purple-100 text-purple-700" :
                        post.cat === "취업 후기" ? "bg-teal-100 text-teal-700" :
                        post.cat === "자격증 후기" ? "bg-orange-100 text-orange-700" :
                        "bg-slate-100 text-slate-700"
                      }`}>{post.cat}</Badge>
                      {post.hot && <Badge className="text-[10px] px-2 py-0.5 bg-red-100 text-red-600">HOT</Badge>}
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="font-semibold text-slate-800 text-sm hover:text-blue-600 transition-colors line-clamp-2 leading-snug">{post.title}</div>
                      <div className="flex items-center gap-3 mt-1.5 flex-wrap">
                        {post.company && (
                          <span className="text-xs text-slate-500 flex items-center gap-0.5">
                            <span className="text-slate-400">기업:</span> {post.company}
                          </span>
                        )}
                        {post.job && (
                          <span className="text-xs text-slate-500 flex items-center gap-0.5">
                            <span className="text-slate-400">직무:</span> {post.job}
                          </span>
                        )}
                        <span className="text-xs text-slate-400">{post.author}</span>
                        <span className="text-xs text-slate-400 flex items-center gap-0.5">
                          <Clock className="size-3" /> {post.date}
                        </span>
                      </div>
                    </div>
                    <div className="flex items-center gap-3 text-xs text-slate-400 flex-shrink-0">
                      <span className="flex items-center gap-1"><Eye className="size-3" />{post.views.toLocaleString()}</span>
                      <span className="flex items-center gap-1"><Heart className="size-3" />{post.likes}</span>
                      <span className="flex items-center gap-1"><MessageSquare className="size-3" />{post.comments}</span>
                    </div>
                  </div>
                </div>
              ))}
            </div>

            {/* Pagination */}
            <div className="flex items-center justify-center gap-2 pt-2">
              {[1, 2, 3, "...", 10].map((p, i) => (
                <button
                  key={i}
                  className={`size-8 rounded-lg text-sm font-medium transition-colors ${
                    p === 1 ? "bg-blue-600 text-white" : "bg-white border border-slate-200 text-slate-600 hover:border-blue-400"
                  }`}
                >
                  {p}
                </button>
              ))}
            </div>
          </div>

          {/* Right sidebar */}
          <div className="space-y-4">
            {/* Quick write */}
            <div className="bg-gradient-to-br from-blue-600 to-indigo-600 text-white rounded-2xl p-5 space-y-3">
              <div className="font-bold">면접 후기를 공유하세요</div>
              <p className="text-sm text-blue-100">합격 후기와 면접 경험을 공유하면 다른 취준생에게 큰 도움이 됩니다.</p>
              <Button size="sm" className="w-full bg-white text-blue-700 hover:bg-blue-50">
                후기 작성하기
              </Button>
            </div>

            {/* Top posts */}
            <div className="bg-white border border-slate-200 rounded-xl p-4">
              <div className="font-bold text-slate-800 text-sm mb-3 flex items-center gap-2">
                <Flame className="size-4 text-orange-500" /> 인기 게시글 TOP 5
              </div>
              <div className="space-y-2">
                {topPosts.map((p, i) => (
                  <div key={i} className="flex items-start gap-2 hover:text-blue-600 cursor-pointer transition-colors group">
                    <span className={`flex-shrink-0 font-black text-xs w-4 ${i === 0 ? "text-red-500" : i === 1 ? "text-orange-500" : i === 2 ? "text-amber-500" : "text-slate-400"}`}>
                      {i + 1}
                    </span>
                    <div className="flex-1 min-w-0">
                      <div className="text-xs text-slate-700 group-hover:text-blue-600 line-clamp-2 leading-snug">{p.title}</div>
                      <div className="text-[10px] text-slate-400 mt-0.5">👁 {p.views.toLocaleString()}</div>
                    </div>
                  </div>
                ))}
              </div>
            </div>

            {/* Category stats */}
            <div className="bg-white border border-slate-200 rounded-xl p-4">
              <div className="font-bold text-slate-800 text-sm mb-3">카테고리별 게시글</div>
              <div className="space-y-2">
                {[
                  { cat: "면접 후기", count: 847 },
                  { cat: "합격 전략", count: 523 },
                  { cat: "직무별 질문", count: 412 },
                  { cat: "취업 후기", count: 389 },
                  { cat: "자격증 후기", count: 234 },
                ].map((c) => (
                  <div key={c.cat} className="flex items-center justify-between text-xs">
                    <span className="text-slate-600">{c.cat}</span>
                    <span className="font-semibold text-slate-700">{c.count}</span>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
