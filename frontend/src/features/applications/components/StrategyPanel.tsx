import { Link } from "react-router";
import { Map, Target } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import type { FitAnalysisDetail } from "@/features/analysis/types/fitAnalysis";
import { parseJsonList, scoreTone } from "@/features/analysis/types/fitAnalysis";

interface StrategyPanelProps {
  analyses: FitAnalysisDetail[];
  loading: boolean;
  error: string | null;
}

export function StrategyPanel({ analyses, loading, error }: StrategyPanelProps) {
  if (loading) return <StateCard title="지원 전략을 불러오는 중입니다." />;
  if (error) return <StateCard title={error} tone="error" />;
  if (analyses.length === 0) {
    return <StateCard title="아직 지원 전략을 만들 분석 결과가 없습니다." description="적합도 분석을 먼저 실행하면 전략이 표시됩니다." />;
  }

  return (
    <div className="space-y-5">
      <div>
        <h2 className="text-lg font-bold text-slate-900">지원 전략</h2>
        <p className="mt-1 text-sm text-slate-500">최신 적합도 분석의 전략 문구를 지원 건별로 정리합니다.</p>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        {analyses.map((analysis) => {
          const tone = scoreTone(analysis.fitScore);
          const actions = parseJsonList(analysis.strategyActions);

          return (
            <Card key={analysis.id} className="border border-slate-200 bg-white">
              <CardHeader className="pb-3">
                <div className="flex items-center justify-between gap-3">
                  <div>
                    <CardTitle className="text-base">{analysis.application.companyName}</CardTitle>
                    <p className="mt-1 text-sm text-slate-500">{analysis.application.jobTitle}</p>
                  </div>
                  <div className={`text-sm font-black ${tone.text}`}>{analysis.fitScore ?? 0}점</div>
                </div>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="rounded-lg bg-slate-50 p-4 text-sm leading-6 text-slate-700">
                  {analysis.strategy || "지원 전략이 아직 생성되지 않았습니다."}
                </div>
                <div className="grid gap-2 text-sm">
                  {actions.length > 0 ? actions.map((action, index) => (
                    <StrategyPoint key={`${action}-${index}`} label={`실행 ${index + 1}`} value={action} />
                  )) : (
                    <StrategyPoint label="다음 액션" value="포트폴리오와 자기소개서에 분석 결과 반영" />
                  )}
                </div>
                <Link to={`/applications/${analysis.applicationCaseId}`} className="inline-flex text-sm font-semibold text-blue-600 hover:text-blue-700">
                  지원 건 상세 보기
                </Link>
              </CardContent>
            </Card>
          );
        })}
      </div>
    </div>
  );
}

function StrategyPoint({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-start gap-2 rounded-lg border border-slate-100 p-3">
      <Target className="mt-0.5 size-4 text-blue-600" />
      <div>
        <div className="font-semibold text-slate-800">{label}</div>
        <div className="mt-0.5 text-slate-500">{value}</div>
      </div>
    </div>
  );
}

function StateCard({ title, description, tone = "default" }: { title: string; description?: string; tone?: "default" | "error" }) {
  return (
    <Card className={`border ${tone === "error" ? "border-red-200 bg-red-50" : "border-slate-200 bg-white"}`}>
      <CardContent className="flex items-start gap-3 p-5">
        <Map className={`mt-0.5 size-5 ${tone === "error" ? "text-red-500" : "text-blue-600"}`} />
        <div>
          <div className="text-sm font-semibold text-slate-800">{title}</div>
          {description && <div className="mt-1 text-sm text-slate-500">{description}</div>}
        </div>
      </CardContent>
    </Card>
  );
}
