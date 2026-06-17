import { useSearchParams } from "react-router";
import { Badge } from "../components/ui/badge";
import { Button } from "../components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "../components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "../components/ui/tabs";
import { AlertTriangle, Award, CheckCircle2, FileText, PenLine, Sparkles, Upload } from "lucide-react";

const tabs = ["answer", "cover", "resume", "portfolio"] as const;
type CorrectionTab = (typeof tabs)[number];

const correctionMeta: Record<CorrectionTab, { title: string; desc: string; placeholder: string; credit: number }> = {
  answer: {
    title: "답변 첨삭",
    desc: "면접 답변을 직무 적합성, 구체성, 논리성 기준으로 개선합니다",
    placeholder: "예: 고객 불만을 해결했던 경험, 매출을 개선한 경험, 협업 갈등을 조정한 경험을 붙여넣으세요.",
    credit: 1,
  },
  cover: {
    title: "자기소개서 첨삭",
    desc: "문항 의도, 경험 구조, 성과 수치화, 지원 직무 연결성을 점검합니다",
    placeholder: "자기소개서 문항과 답변을 붙여넣으세요.",
    credit: 2,
  },
  resume: {
    title: "이력서 첨삭",
    desc: "경험 표현, 직무 역량 정리, 성과 중심 문장을 보강합니다",
    placeholder: "이력서 경력, 활동, 프로젝트, 실습, 아르바이트 경험 항목을 붙여넣으세요.",
    credit: 2,
  },
  portfolio: {
    title: "포트폴리오 설명 첨삭",
    desc: "작업물, 활동, 프로젝트의 배경, 역할, 문제 해결, 결과를 채용자가 읽기 좋게 다듬습니다",
    placeholder: "디자인 작업물, 마케팅 캠페인, 고객 응대 개선, 개발 프로젝트 등 포트폴리오 설명을 붙여넣으세요.",
    credit: 2,
  },
};

const checklist = ["질문 의도와 답변 방향 일치", "경험의 맥락과 본인 역할 구분", "성과 수치 또는 비교 기준 포함", "지원 직무와의 연결성 강화"];

export function CorrectionPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const requestedTab = searchParams.get("tab") ?? "answer";
  const activeTab: CorrectionTab = tabs.includes(requestedTab as CorrectionTab) ? (requestedTab as CorrectionTab) : "answer";
  const active = correctionMeta[activeTab];

  return (
    <div className="min-h-screen bg-slate-50">
      <div className="mx-auto w-full max-w-[1200px] space-y-6 px-4 py-8 sm:px-6">
        <div>
          <h1 className="flex items-center gap-2 text-2xl font-black text-slate-900">
            <PenLine className="size-6 text-blue-600" />
            AI 첨삭
          </h1>
          <p className="mt-1 text-sm text-slate-500">답변, 자기소개서, 이력서, 포트폴리오 설명을 지원 건 기준으로 다듬습니다</p>
        </div>

        <div className="flex items-start gap-3 rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">
          <AlertTriangle className="mt-0.5 size-5 shrink-0" />
          <div>
            <div className="font-bold">첨삭 API 준비 중</div>
            <p className="mt-1 leading-6">현재 화면은 입력 흐름 샘플입니다. 첨삭 실행, 파일 업로드, 최근 기록은 백엔드 구현 후 활성화됩니다.</p>
          </div>
        </div>

        <Tabs value={activeTab} onValueChange={(value) => setSearchParams({ tab: value })}>
          <TabsList className="h-auto w-full justify-start overflow-x-auto border border-slate-200 bg-white p-1">
            <TabsTrigger value="answer">답변 첨삭</TabsTrigger>
            <TabsTrigger value="cover">자기소개서 첨삭</TabsTrigger>
            <TabsTrigger value="resume">이력서 첨삭</TabsTrigger>
            <TabsTrigger value="portfolio">포트폴리오 설명 첨삭</TabsTrigger>
          </TabsList>

          {tabs.map((tab) => (
            <TabsContent key={tab} value={tab} className="mt-5">
              <div className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_320px]">
                <Card className="border border-slate-200 bg-white">
                  <CardHeader>
                    <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                      <div>
                        <CardTitle className="flex items-center gap-2 text-lg">
                          <Sparkles className="size-5 text-blue-600" />
                          {active.title}
                        </CardTitle>
                        <p className="mt-1 text-sm text-slate-500">{active.desc}</p>
                      </div>
                      <Badge className="bg-amber-100 text-amber-700">
                        <Award className="mr-1 size-3" />
                        {active.credit} 크레딧
                      </Badge>
                    </div>
                  </CardHeader>
                  <CardContent className="space-y-4">
                    <div className="rounded-xl border-2 border-dashed border-slate-300 bg-slate-50 p-6 text-center">
                      <Upload className="mx-auto size-8 text-slate-400" />
                      <div className="mt-2 text-sm font-semibold text-slate-700">파일 업로드 또는 텍스트 입력</div>
                      <div className="mt-1 text-xs text-slate-500">PDF, DOCX, TXT 또는 직접 붙여넣기 흐름을 지원할 예정입니다</div>
                    </div>
                    <div className="min-h-44 rounded-xl border border-slate-200 bg-white p-4 text-sm text-slate-400">
                      {active.placeholder}
                    </div>
                    <div className="flex flex-col gap-2 sm:flex-row">
                      <Button disabled className="bg-primary">준비 중</Button>
                      <Button disabled variant="outline">지원 건 연결</Button>
                      <Button disabled variant="outline">임시 저장</Button>
                    </div>
                  </CardContent>
                </Card>

                <div className="space-y-4">
                  <Card className="border border-slate-200 bg-white">
                    <CardHeader>
                      <CardTitle className="flex items-center gap-2 text-base">
                        <CheckCircle2 className="size-4 text-green-600" />
                        첨삭 기준
                      </CardTitle>
                    </CardHeader>
                    <CardContent className="space-y-2">
                      {checklist.map((item) => (
                        <div key={item} className="flex items-start gap-2 text-sm text-slate-700">
                          <CheckCircle2 className="mt-0.5 size-4 text-green-600" />
                          {item}
                        </div>
                      ))}
                    </CardContent>
                  </Card>
                  <Card className="border border-slate-200 bg-white">
                    <CardHeader>
                      <CardTitle className="flex items-center gap-2 text-base">
                        <FileText className="size-4 text-blue-600" />
                        최근 첨삭 기록
                      </CardTitle>
                    </CardHeader>
                    <CardContent className="space-y-2">
                      {["영업 직무 답변", "간호사 자기소개서", "마케팅 캠페인 설명 개선"].map((item) => (
                        <div key={item} className="rounded-lg bg-slate-50 p-3 text-sm font-semibold text-slate-500">샘플 · {item}</div>
                      ))}
                    </CardContent>
                  </Card>
                </div>
              </div>
            </TabsContent>
          ))}
        </Tabs>
      </div>
    </div>
  );
}
