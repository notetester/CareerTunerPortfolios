import { useCallback, useEffect, useMemo, useState } from "react";
import { Link } from "react-router";
import { ArrowRight, Award, Loader2, Map, RefreshCw, TrendingUp } from "lucide-react";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { getAnalysisSummary } from "../api/analysisSummaryApi";
import type { AnalysisSummary } from "../types/analysisSummary";
import { ANALYSIS_SECTION_PATHS, analysisTabs, type AnalysisTab } from "./AnalysisPage";

const SECTION_DESCRIPTION: Record<AnalysisTab, string> = {
  trend: "지원 직무·기업 분포와 최근 지원 흐름을 확인합니다.",
  weakness: "여러 지원 건에서 반복되는 부족 역량을 우선순위로 정리합니다.",
  readiness: "직무별 지원 건과 적합도를 합쳐 현재 준비도를 비교합니다.",
  score: "모의면접 세션·답변 평균과 지난주 대비 점수 변화를 추적합니다.",
  recommendation: "지원 우선순위, 위험 요인과 다음 행동을 실행 계획으로 바꿉니다.",
};

export function AnalysisHubPage() {
  const [summary, setSummary] = useState<AnalysisSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setSummary(await getAnalysisSummary());
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "취업 분석 요약을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const sectionValue = useMemo<Record<AnalysisTab, string>>(() => ({
    trend: summary ? `지원 ${summary.stats.totalApplications}건 · 분석 ${summary.stats.analyzedApplications}건` : "지원 흐름 확인",
    weakness: summary?.skillGaps[0] ? `${summary.skillGaps[0].skill} · ${summary.skillGaps[0].percentage}%` : "반복 부족 역량 확인",
    readiness: summary?.jobReadiness[0] ? `${summary.jobReadiness[0].jobTitle} · ${summary.jobReadiness[0].readiness}점` : "직무 준비도 확인",
    score: summary?.interviewTrend.totalSessions
      ? `세션 평균 ${summary.interviewTrend.averageSessionScore}점 · ${summary.interviewTrend.totalSessions}회`
      : "면접 점수 변화 확인",
    recommendation: summary?.recommendedDirections[0] ?? "다음 지원 방향 확인",
  }), [summary]);

  return (
    <main className="min-h-screen bg-slate-50">
      <div className="mx-auto w-full max-w-[1400px] space-y-6 px-4 py-8 sm:px-6 lg:px-8">
        <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <h1 className="flex items-center gap-2 text-2xl font-black text-slate-900">
              <TrendingUp className="size-6 text-emerald-600" />
              취업 분석
            </h1>
            <p className="mt-1 text-sm text-slate-500">지원 기록과 면접 결과를 다섯 관점으로 나눠 필요한 분석부터 확인하세요.</p>
          </div>
          <Button type="button" variant="outline" onClick={() => void load()} disabled={loading}>
            {loading ? <Loader2 className="size-4 animate-spin" /> : <RefreshCw className="size-4" />}
            요약 새로고침
          </Button>
        </header>

        {error && (
          <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
            <span>{error}</span>
            <Button type="button" size="sm" variant="outline" onClick={() => void load()}>다시 시도</Button>
          </div>
        )}

        <section aria-label="취업 분석 기능" className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {analysisTabs.map((section) => {
            const Icon = section.icon;
            return (
              <Card key={section.key} className="border-slate-200 bg-card transition-shadow hover:shadow-md">
                <CardHeader className="pb-3">
                  <div className="flex items-start justify-between gap-3">
                    <div className="flex size-10 items-center justify-center rounded-lg bg-emerald-50 text-emerald-700">
                      <Icon className="size-5" />
                    </div>
                    {loading && <Loader2 className="size-4 animate-spin text-slate-400" aria-label="요약 불러오는 중" />}
                  </div>
                  <CardTitle className="text-base">{section.label}</CardTitle>
                </CardHeader>
                <CardContent className="flex h-full flex-col gap-4">
                  <p className="text-sm leading-6 text-slate-500">{SECTION_DESCRIPTION[section.key]}</p>
                  <div className="rounded-lg bg-slate-50 px-3 py-2 text-sm font-semibold text-slate-700">
                    {sectionValue[section.key]}
                  </div>
                  <Link
                    to={ANALYSIS_SECTION_PATHS[section.key]}
                    className="mt-auto inline-flex items-center gap-1 text-sm font-bold text-emerald-700 hover:text-emerald-800"
                  >
                    분석 열기 <ArrowRight className="size-4" />
                  </Link>
                </CardContent>
              </Card>
            );
          })}
        </section>

        <section className="grid gap-4 md:grid-cols-2" aria-label="장기 준비 도구">
          <Link to="/career-roadmap" className="rounded-xl border border-indigo-200 bg-indigo-50 p-5 transition-colors hover:bg-indigo-100">
            <div className="flex items-center gap-2 font-bold text-indigo-900"><Map className="size-5" /> 장기 커리어 로드맵</div>
            <p className="mt-2 text-sm leading-6 text-indigo-800">추천 방향을 월별 실행 일정으로 만들고 플래너에 바로 추가합니다.</p>
          </Link>
          <Link to="/certificates" className="rounded-xl border border-blue-200 bg-blue-50 p-5 transition-colors hover:bg-blue-100">
            <div className="flex items-center gap-2 font-bold text-blue-900"><Award className="size-5" /> 자격증 검색</div>
            <p className="mt-2 text-sm leading-6 text-blue-800">직무와 부족 역량에 맞는 자격증을 검색하고 준비 정보를 확인합니다.</p>
          </Link>
        </section>
      </div>
    </main>
  );
}
