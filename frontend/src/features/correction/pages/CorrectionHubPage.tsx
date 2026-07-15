import { useCallback, useEffect, useMemo, useState } from "react";
import { Link } from "react-router";
import { ArrowRight, FileText, Loader2, PenLine, RefreshCw, Sparkles, UserRound } from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { listCorrections } from "../api/correctionApi";
import {
  CORRECTION_TYPE_BY_TAB,
  type CorrectionResponse,
  type CorrectionTab,
} from "../types/correction";
import { CORRECTION_SECTION_PATHS } from "./CorrectionPage";

const SECTIONS: Array<{
  key: CorrectionTab;
  title: string;
  description: string;
  icon: LucideIcon;
}> = [
  { key: "answer", title: "답변 첨삭", description: "면접 질문과 답변을 함께 검토해 논리와 직무 적합성을 높입니다.", icon: Sparkles },
  { key: "cover", title: "자기소개서 첨삭", description: "문항 의도, 경험 구조와 지원 직무 연결을 점검합니다.", icon: PenLine },
  { key: "resume", title: "이력서 첨삭", description: "경력과 프로젝트를 역할·행동·성과 중심 문장으로 바꿉니다.", icon: UserRound },
  { key: "portfolio", title: "포트폴리오 설명 첨삭", description: "작업 배경, 기여 범위와 결과가 빠르게 읽히도록 다듬습니다.", icon: FileText },
];

export function CorrectionHubPage() {
  const [items, setItems] = useState<CorrectionResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setItems(await listCorrections({ limit: 20 }));
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "첨삭 기록을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const sectionSummary = useMemo(() => Object.fromEntries(SECTIONS.map((section) => {
    const matching = items.filter((item) => item.correctionType === CORRECTION_TYPE_BY_TAB[section.key]);
    return [section.key, {
      count: matching.length,
      latest: matching[0]?.summary ?? null,
    }];
  })) as Record<CorrectionTab, { count: number; latest: string | null }>, [items]);

  return (
    <main className="min-h-screen bg-slate-50">
      <div className="mx-auto w-full max-w-[1400px] space-y-6 px-4 py-8 sm:px-6 lg:px-8">
        <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <h1 className="flex items-center gap-2 text-2xl font-black text-slate-900">
              <PenLine className="size-6 text-blue-600" />
              AI 첨삭
            </h1>
            <p className="mt-1 text-sm text-slate-500">고칠 문서 유형을 먼저 선택하면 해당 원문과 기록만 모아 볼 수 있습니다.</p>
          </div>
          <Button type="button" variant="outline" onClick={() => void load()} disabled={loading}>
            {loading ? <Loader2 className="size-4 animate-spin" /> : <RefreshCw className="size-4" />}
            기록 새로고침
          </Button>
        </header>

        {error && (
          <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
            <span>{error}</span>
            <Button type="button" size="sm" variant="outline" onClick={() => void load()}>다시 시도</Button>
          </div>
        )}

        <section aria-label="첨삭 유형" className="grid gap-4 md:grid-cols-2">
          {SECTIONS.map((section) => {
            const Icon = section.icon;
            const summary = sectionSummary[section.key];
            return (
              <Card key={section.key} className="border-slate-200 bg-card transition-shadow hover:shadow-md">
                <CardHeader className="pb-3">
                  <div className="flex items-start justify-between gap-3">
                    <div className="flex size-10 items-center justify-center rounded-lg bg-blue-50 text-blue-700"><Icon className="size-5" /></div>
                    <span className="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-semibold text-slate-600">
                      {loading ? "확인 중" : `최근 ${summary.count}건`}
                    </span>
                  </div>
                  <CardTitle className="text-base">{section.title}</CardTitle>
                </CardHeader>
                <CardContent className="flex h-full flex-col gap-4">
                  <p className="text-sm leading-6 text-slate-500">{section.description}</p>
                  <div className="min-h-14 rounded-lg bg-slate-50 px-3 py-2 text-sm leading-5 text-slate-600">
                    {summary.latest ?? "새 원문을 입력해 첫 첨삭을 시작할 수 있습니다."}
                  </div>
                  <Link
                    to={CORRECTION_SECTION_PATHS[section.key]}
                    className="mt-auto inline-flex items-center gap-1 text-sm font-bold text-blue-700 hover:text-blue-800"
                  >
                    {section.title} 열기 <ArrowRight className="size-4" />
                  </Link>
                </CardContent>
              </Card>
            );
          })}
        </section>
      </div>
    </main>
  );
}
