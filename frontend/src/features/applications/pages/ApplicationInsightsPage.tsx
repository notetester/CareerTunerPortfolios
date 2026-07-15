import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router";
import { ArrowLeft, Compass, GraduationCap, Target } from "lucide-react";
import { useAuth } from "@/app/auth/AuthContext";
import { Button } from "@/app/components/ui/button";
import { getFitAnalyses } from "@/features/analysis/api/fitAnalysisApi";
import type { FitAnalysisDetail } from "@/features/analysis/types/fitAnalysis";
import { FitAnalysisPanel } from "../components/FitAnalysisPanel";
import { LearningRecommendationPanel } from "../components/LearningRecommendationPanel";
import { StrategyPanel } from "../components/StrategyPanel";
import { LoginRequiredState } from "../components/LoginRequiredState";

type InsightFeature = "compare" | "strategy" | "learning";

const FEATURE_META: Record<InsightFeature, { title: string; description: string; icon: typeof Target }> = {
  compare: {
    title: "내 스펙과 공고 비교",
    description: "등록한 모든 지원 건의 적합도를 한눈에 비교하고 강점·부족 역량을 확인합니다.",
    icon: Target,
  },
  strategy: {
    title: "지원 전략",
    description: "모든 지원 건의 지원 전략과 단계별 실행 계획을 한곳에 모아 봅니다.",
    icon: Compass,
  },
  learning: {
    title: "학습·자격증 추천",
    description: "모든 지원 건의 부족 역량을 기반으로 학습 과제와 자격증을 추천합니다.",
    icon: GraduationCap,
  },
};

/**
 * 지원 건별 기능(적합도 비교·전략·학습/자격증)을 전체 지원 건에 걸쳐 모아 보는 독립 페이지.
 * 헤더 '지원 건 관리' 하위메뉴가 여기로 직접 진입한다(지원 건 목록을 거치지 않는다).
 * 데이터는 지원 건별 최신 적합도 분석 목록(GET /fit-analyses) 하나로, 세 화면이 같은 소스를 다르게 본다.
 */
function ApplicationInsightsPage({ feature }: { feature: InsightFeature }) {
  const navigate = useNavigate();
  const { loading: authLoading, isAuthenticated } = useAuth();
  const [analyses, setAnalyses] = useState<FitAnalysisDetail[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const meta = FEATURE_META[feature];

  useEffect(() => {
    if (!isAuthenticated) return;
    let ignore = false;
    setLoading(true);
    setError(null);
    getFitAnalyses()
      .then((rows) => { if (!ignore) setAnalyses(rows); })
      .catch((requestError) => {
        if (!ignore) setError(requestError instanceof Error ? requestError.message : "적합도 분석을 불러오지 못했습니다.");
      })
      .finally(() => { if (!ignore) setLoading(false); });
    return () => { ignore = true; };
  }, [isAuthenticated]);

  if (authLoading) {
    return (
      <div className="min-h-[calc(100vh-72px)] bg-slate-50 px-4 py-10">
        <div className="mx-auto max-w-6xl">
          <div className="h-32 animate-pulse rounded-lg bg-slate-200" />
        </div>
      </div>
    );
  }

  if (!isAuthenticated) {
    return (
      <LoginRequiredState
        title={`${meta.title}는 로그인 후 사용할 수 있습니다`}
        description="본인 지원 건의 적합도 분석 결과로만 조회합니다."
      />
    );
  }

  const isEmpty = !loading && !error && analyses.length === 0;

  return (
    <div className="min-h-[calc(100vh-72px)] bg-slate-50">
      <div className="mx-auto max-w-7xl space-y-5 px-4 py-8 sm:px-6">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <h1 className="flex items-center gap-2 text-2xl font-bold text-slate-950">
              <meta.icon className="size-6 text-blue-600" />
              {meta.title}
            </h1>
            <p className="mt-1 text-sm text-slate-500">{meta.description}</p>
          </div>
          <div className="flex items-center gap-3">
            {!loading && !error && analyses.length > 0 && (
              <span className="text-sm text-slate-500">전체 지원 건 {analyses.length}개</span>
            )}
            <Button variant="outline" size="sm" className="gap-1.5" onClick={() => navigate("/applications")}>
              <ArrowLeft className="size-4" />
              지원 건 관리
            </Button>
          </div>
        </div>

        {isEmpty ? (
          <div className="flex flex-col items-center gap-4 rounded-lg border border-dashed border-slate-300 bg-card p-10 text-center">
            <div className="flex size-12 items-center justify-center rounded-lg bg-slate-100 text-slate-500">
              <meta.icon className="size-6" />
            </div>
            <div>
              <div className="font-semibold text-slate-900">아직 적합도 분석 결과가 없습니다</div>
              <p className="mt-1 text-sm text-slate-500">
                지원 건을 열어 <b>적합도 분석</b>을 실행하면 이곳에 모든 지원 건의 {meta.title}가 모입니다.
              </p>
            </div>
            <Button className="bg-blue-600 text-white hover:bg-blue-700" onClick={() => navigate("/applications")}>
              지원 건 관리로 가기
            </Button>
          </div>
        ) : feature === "compare" ? (
          <FitAnalysisPanel analyses={analyses} loading={loading} error={error} hideHeading />
        ) : feature === "strategy" ? (
          <StrategyPanel analyses={analyses} loading={loading} error={error} hideHeading />
        ) : (
          <LearningRecommendationPanel analyses={analyses} loading={loading} error={error} hideHeading />
        )}
      </div>
    </div>
  );
}

export function ApplicationComparePage() {
  return <ApplicationInsightsPage feature="compare" />;
}

export function ApplicationStrategyPage() {
  return <ApplicationInsightsPage feature="strategy" />;
}

export function ApplicationLearningPage() {
  return <ApplicationInsightsPage feature="learning" />;
}
