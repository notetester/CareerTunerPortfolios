import { useState } from "react";
import { Button } from "../components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "../components/ui/card";
import { Badge } from "../components/ui/badge";
import { Input } from "../components/ui/input";
import { Progress } from "../components/ui/progress";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "../components/ui/tabs";
import {
  User, FileText, Briefcase, Award, BookOpen, Globe, Plus, Edit,
  CheckCircle2, AlertCircle, Camera,
} from "lucide-react";

const skills = [
  { name: "React", level: 4 },
  { name: "JavaScript", level: 4 },
  { name: "TypeScript", level: 2 },
  { name: "HTML/CSS", level: 4 },
  { name: "Node.js", level: 3 },
  { name: "Git", level: 4 },
  { name: "REST API", level: 3 },
  { name: "AWS", level: 1 },
  { name: "Docker", level: 2 },
  { name: "Python", level: 2 },
];

const projects = [
  { name: "React 게시판 프로젝트", role: "프론트엔드 개발", stack: ["React", "Node.js", "MySQL"], period: "2025.09 - 2025.11", desc: "팀 프로젝트로 게시판 CRUD 및 실시간 댓글 기능 구현" },
  { name: "포트폴리오 사이트", role: "풀스택 개발", stack: ["React", "TypeScript"], period: "2025.12 - 2026.01", desc: "개인 포트폴리오 사이트 제작 및 배포" },
  { name: "날씨 앱", role: "프론트엔드", stack: ["React", "OpenAPI"], period: "2026.02 - 2026.02", desc: "OpenWeather API를 활용한 날씨 조회 앱" },
];

const certificates = [
  { name: "정보처리기사", date: "2025-08", status: "합격" },
  { name: "SQLD", date: "2025-11", status: "합격" },
  { name: "AWS Cloud Practitioner", date: "준비중", status: "준비중" },
];

const profileCompleteness = [
  { label: "기본 정보", done: true },
  { label: "학력 입력", done: true },
  { label: "기술스택 입력", done: true },
  { label: "이력서 업로드", done: true },
  { label: "프로젝트 입력", done: true },
  { label: "자기소개서 작성", done: false },
  { label: "포트폴리오 링크", done: false },
  { label: "자격증 입력", done: true },
];

export function ProfilePage() {
  return (
    <div className="bg-slate-50 min-h-screen">
      <div className="max-w-[1400px] mx-auto px-6 py-8 space-y-6">
        {/* Header */}
        <div>
          <h1 className="text-2xl font-black text-slate-900 flex items-center gap-2">
            <User className="size-6 text-blue-600" />
            내 프로필 관리
          </h1>
          <p className="text-slate-500 text-sm mt-1">프로필이 풍부할수록 AI 공고 비교 분석의 정확도가 높아집니다</p>
        </div>

        <div className="grid lg:grid-cols-4 gap-6">
          {/* Left: Profile card + completeness */}
          <div className="space-y-4">
            {/* Profile card */}
            <Card className="border border-slate-200 bg-white">
              <CardContent className="p-5 text-center space-y-3">
                <div className="relative inline-block">
                  <div className="size-20 rounded-full bg-gradient-to-br from-blue-500 to-indigo-500 flex items-center justify-center text-white text-2xl font-black mx-auto">
                    김
                  </div>
                  <button className="absolute bottom-0 right-0 size-6 bg-white border border-slate-200 rounded-full flex items-center justify-center shadow-sm">
                    <Camera className="size-3 text-slate-600" />
                  </button>
                </div>
                <div>
                  <div className="font-black text-slate-900 text-lg">김지원</div>
                  <div className="text-sm text-slate-500">jiwon@example.com</div>
                  <Badge className="mt-1 bg-blue-100 text-blue-700">프로 플랜</Badge>
                </div>
                <div className="text-left space-y-1.5 text-sm">
                  <div className="flex items-center gap-2 text-slate-600">
                    <Briefcase className="size-3.5 text-slate-400" />
                    <span>희망 직무: 프론트엔드 개발자</span>
                  </div>
                  <div className="flex items-center gap-2 text-slate-600">
                    <Globe className="size-3.5 text-slate-400" />
                    <span>희망 지역: 서울, 경기</span>
                  </div>
                </div>
                <Button variant="outline" size="sm" className="w-full gap-1.5">
                  <Edit className="size-3.5" /> 기본 정보 수정
                </Button>
              </CardContent>
            </Card>

            {/* Completeness */}
            <Card className="border border-slate-200 bg-white">
              <CardHeader className="pb-3">
                <CardTitle className="text-sm">프로필 완성도</CardTitle>
              </CardHeader>
              <CardContent className="space-y-3">
                <div className="text-center">
                  <div className="text-3xl font-black text-blue-600">75%</div>
                  <Progress value={75} className="mt-1.5 h-2" />
                </div>
                <div className="space-y-1.5">
                  {profileCompleteness.map((item) => (
                    <div key={item.label} className="flex items-center gap-2 text-xs">
                      {item.done ? (
                        <CheckCircle2 className="size-3.5 text-green-500 flex-shrink-0" />
                      ) : (
                        <AlertCircle className="size-3.5 text-amber-500 flex-shrink-0" />
                      )}
                      <span className={item.done ? "text-slate-600" : "text-amber-700 font-medium"}>{item.label}</span>
                    </div>
                  ))}
                </div>
              </CardContent>
            </Card>
          </div>

          {/* Right: Tabs */}
          <div className="lg:col-span-3">
            <Tabs defaultValue="basic">
              <TabsList className="bg-white border border-slate-200 h-10 w-full justify-start">
                <TabsTrigger value="basic">기본 정보</TabsTrigger>
                <TabsTrigger value="skills">기술스택</TabsTrigger>
                <TabsTrigger value="projects">경력/프로젝트</TabsTrigger>
                <TabsTrigger value="certificates">자격증/학력</TabsTrigger>
                <TabsTrigger value="resume">이력서/자소서</TabsTrigger>
              </TabsList>

              <TabsContent value="basic" className="mt-5">
                <Card className="border border-slate-200 bg-white">
                  <CardHeader className="flex flex-row items-center justify-between pb-4">
                    <CardTitle className="text-base">기본 정보</CardTitle>
                    <Button size="sm" variant="outline" className="gap-1.5"><Edit className="size-3.5" /> 수정</Button>
                  </CardHeader>
                  <CardContent>
                    <div className="grid md:grid-cols-2 gap-4">
                      {[
                        { label: "이름", value: "김지원" },
                        { label: "이메일", value: "jiwon@example.com" },
                        { label: "연락처", value: "010-0000-0000" },
                        { label: "생년월일", value: "1999-03-15" },
                        { label: "희망 직무", value: "프론트엔드 개발자" },
                        { label: "희망 산업군", value: "IT/인터넷, 핀테크" },
                        { label: "희망 지역", value: "서울, 경기" },
                        { label: "희망 연봉", value: "3,500만원 이상" },
                        { label: "근무 형태", value: "정규직" },
                        { label: "취업 준비 단계", value: "취준생 (신입)" },
                      ].map((item) => (
                        <div key={item.label} className="flex items-start gap-3 p-3 bg-slate-50 rounded-xl">
                          <span className="text-xs text-slate-500 w-24 flex-shrink-0 pt-0.5">{item.label}</span>
                          <span className="text-sm font-semibold text-slate-800">{item.value}</span>
                        </div>
                      ))}
                    </div>
                  </CardContent>
                </Card>
              </TabsContent>

              <TabsContent value="skills" className="mt-5">
                <Card className="border border-slate-200 bg-white">
                  <CardHeader className="flex flex-row items-center justify-between pb-4">
                    <CardTitle className="text-base">기술스택</CardTitle>
                    <Button size="sm" variant="outline" className="gap-1.5"><Plus className="size-3.5" /> 기술 추가</Button>
                  </CardHeader>
                  <CardContent className="space-y-3">
                    {skills.map((s) => (
                      <div key={s.name} className="flex items-center gap-3">
                        <div className="w-24 text-sm font-medium text-slate-700">{s.name}</div>
                        <div className="flex gap-1">
                          {[1, 2, 3, 4, 5].map((l) => (
                            <div
                              key={l}
                              className={`size-6 rounded ${l <= s.level ? "bg-blue-600" : "bg-slate-200"}`}
                            />
                          ))}
                        </div>
                        <div className="text-xs text-slate-500 ml-1">
                          {s.level === 1 ? "입문" : s.level === 2 ? "기초" : s.level === 3 ? "중급" : s.level === 4 ? "능숙" : "전문"}
                        </div>
                      </div>
                    ))}
                    <div className="text-xs text-slate-400 mt-3 p-3 bg-slate-50 rounded-xl">
                      ✏️ 기술 레벨: 1=입문, 2=기초, 3=중급, 4=능숙, 5=전문가 수준
                    </div>
                  </CardContent>
                </Card>
              </TabsContent>

              <TabsContent value="projects" className="mt-5 space-y-4">
                {projects.map((p, i) => (
                  <Card key={i} className="border border-slate-200 bg-white">
                    <CardContent className="p-5">
                      <div className="flex items-start justify-between gap-3">
                        <div className="flex-1">
                          <div className="font-bold text-slate-800 mb-1">{p.name}</div>
                          <div className="text-sm text-slate-500 mb-2">{p.role} · {p.period}</div>
                          <p className="text-sm text-slate-600 mb-3">{p.desc}</p>
                          <div className="flex flex-wrap gap-1">
                            {p.stack.map((t) => (
                              <Badge key={t} className="bg-blue-100 text-blue-700 text-xs px-2 py-0.5">{t}</Badge>
                            ))}
                          </div>
                        </div>
                        <Button size="sm" variant="ghost" className="gap-1"><Edit className="size-3.5" /></Button>
                      </div>
                    </CardContent>
                  </Card>
                ))}
                <Button variant="outline" className="w-full gap-2 border-dashed">
                  <Plus className="size-4" /> 프로젝트 추가
                </Button>
              </TabsContent>

              <TabsContent value="certificates" className="mt-5 space-y-4">
                <Card className="border border-slate-200 bg-white">
                  <CardHeader className="flex flex-row items-center justify-between pb-4">
                    <CardTitle className="text-base">자격증</CardTitle>
                    <Button size="sm" variant="outline" className="gap-1.5"><Plus className="size-3.5" /> 추가</Button>
                  </CardHeader>
                  <CardContent className="space-y-3">
                    {certificates.map((c) => (
                      <div key={c.name} className="flex items-center justify-between p-3 bg-slate-50 rounded-xl">
                        <div className="flex items-center gap-3">
                          <Award className="size-5 text-amber-500" />
                          <div>
                            <div className="font-semibold text-slate-800 text-sm">{c.name}</div>
                            <div className="text-xs text-slate-500">{c.date}</div>
                          </div>
                        </div>
                        <Badge className={`text-xs ${c.status === "합격" ? "bg-green-100 text-green-700" : "bg-amber-100 text-amber-700"}`}>{c.status}</Badge>
                      </div>
                    ))}
                  </CardContent>
                </Card>
                <Card className="border border-slate-200 bg-white">
                  <CardHeader className="flex flex-row items-center justify-between pb-4">
                    <CardTitle className="text-base">학력</CardTitle>
                    <Button size="sm" variant="outline" className="gap-1.5"><Edit className="size-3.5" /> 수정</Button>
                  </CardHeader>
                  <CardContent>
                    <div className="p-3 bg-slate-50 rounded-xl">
                      <div className="font-semibold text-slate-800 text-sm">한국대학교 컴퓨터공학과</div>
                      <div className="text-xs text-slate-500 mt-0.5">2018.03 - 2026.02 졸업 예정</div>
                    </div>
                  </CardContent>
                </Card>
              </TabsContent>

              <TabsContent value="resume" className="mt-5 space-y-4">
                <Card className="border border-slate-200 bg-white">
                  <CardHeader className="pb-4">
                    <CardTitle className="text-base flex items-center gap-2">
                      <FileText className="size-4 text-blue-600" /> 이력서 관리
                    </CardTitle>
                  </CardHeader>
                  <CardContent className="space-y-4">
                    <div className="border-2 border-dashed border-slate-300 rounded-xl p-6 text-center space-y-2 hover:border-blue-400 cursor-pointer transition-colors">
                      <FileText className="size-8 text-slate-300 mx-auto" />
                      <div className="text-sm font-medium text-slate-600">이력서 PDF 업로드</div>
                      <div className="text-xs text-slate-400">또는 아래에서 직접 작성</div>
                    </div>
                    <div className="space-y-2">
                      {[
                        { name: "이력서_v3_2026.pdf", date: "2026-05-20", size: "1.2 MB" },
                        { name: "이력서_v2.pdf", date: "2026-04-10", size: "0.9 MB" },
                      ].map((f) => (
                        <div key={f.name} className="flex items-center gap-3 p-3 bg-slate-50 rounded-xl">
                          <FileText className="size-4 text-blue-600 flex-shrink-0" />
                          <div className="flex-1">
                            <div className="text-sm font-medium text-slate-800">{f.name}</div>
                            <div className="text-xs text-slate-400">{f.date} · {f.size}</div>
                          </div>
                          <Button size="sm" variant="ghost" className="text-xs h-7">보기</Button>
                        </div>
                      ))}
                    </div>
                  </CardContent>
                </Card>

                <Card className="border border-slate-200 bg-white">
                  <CardHeader className="pb-4">
                    <CardTitle className="text-base flex items-center gap-2">
                      <BookOpen className="size-4 text-green-600" /> 자기소개서 관리
                    </CardTitle>
                  </CardHeader>
                  <CardContent className="space-y-3">
                    <Button variant="outline" className="w-full gap-2 border-dashed">
                      <Plus className="size-4" /> 자기소개서 작성하기
                    </Button>
                    <div className="text-xs text-slate-400 p-3 bg-slate-50 rounded-xl">
                      ℹ️ 자기소개서를 등록하면 AI가 지원 건에 맞게 맞춤형 첨삭을 제공합니다.
                    </div>
                  </CardContent>
                </Card>
              </TabsContent>
            </Tabs>
          </div>
        </div>
      </div>
    </div>
  );
}
