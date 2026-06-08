import { Award, BookOpen, GraduationCap } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import type { FitAnalysisDetail } from "@/features/analysis/types/fitAnalysis";
import { parseJsonList } from "@/features/analysis/types/fitAnalysis";

interface LearningRecommendationPanelProps {
  analyses: FitAnalysisDetail[];
  loading: boolean;
  error: string | null;
}

export function LearningRecommendationPanel({ analyses, loading, error }: LearningRecommendationPanelProps) {
  if (loading) return <StateCard title="학습 추천을 불러오는 중입니다." />;
  if (error) return <StateCard title={error} tone="error" />;
  if (analyses.length === 0) {
    return <StateCard title="아직 추천할 학습 결과가 없습니다." description="적합도 분석을 먼저 실행하면 부족 역량 기반 추천이 표시됩니다." />;
  }

  return (
    <div className="space-y-5">
      <div>
        <h2 className="text-lg font-bold text-slate-900">학습/자격증 추천</h2>
        <p className="mt-1 text-sm text-slate-500">지원 건별 부족 역량을 학습 과제와 자격증 추천으로 연결합니다.</p>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        {analyses.map((analysis) => {
          const studyItems = parseJsonList(analysis.recommendedStudy);
          const certificates = parseJsonList(analysis.recommendedCertificates);

          return (
            <Card key={analysis.id} className="border border-slate-200 bg-white">
              <CardHeader className="pb-3">
                <CardTitle className="text-base">{analysis.application.companyName} · {analysis.application.jobTitle}</CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                <RecommendationList
                  icon="study"
                  title="추천 학습"
                  items={studyItems}
                  emptyText="추천 학습이 아직 생성되지 않았습니다."
                />
                <RecommendationList
                  icon="certificate"
                  title="추천 자격증"
                  items={certificates}
                  emptyText="이 지원 건에는 우선 추천 자격증이 없습니다."
                />
              </CardContent>
            </Card>
          );
        })}
      </div>
    </div>
  );
}

function RecommendationList({ icon, title, items, emptyText }: { icon: "study" | "certificate"; title: string; items: string[]; emptyText: string }) {
  const Icon = icon === "study" ? BookOpen : Award;
  const color = icon === "study" ? "text-blue-600" : "text-amber-600";

  return (
    <div>
      <div className="mb-2 flex items-center gap-1.5 text-sm font-semibold text-slate-800">
        <Icon className={`size-4 ${color}`} />
        {title}
      </div>
      <div className="space-y-2">
        {items.length > 0 ? (
          items.map((item, index) => (
            <div key={`${item}-${index}`} className="rounded-lg bg-slate-50 p-3 text-sm text-slate-700">
              {item}
            </div>
          ))
        ) : (
          <div className="rounded-lg bg-slate-50 p-3 text-sm text-slate-400">{emptyText}</div>
        )}
      </div>
    </div>
  );
}

function StateCard({ title, description, tone = "default" }: { title: string; description?: string; tone?: "default" | "error" }) {
  return (
    <Card className={`border ${tone === "error" ? "border-red-200 bg-red-50" : "border-slate-200 bg-white"}`}>
      <CardContent className="flex items-start gap-3 p-5">
        <GraduationCap className={`mt-0.5 size-5 ${tone === "error" ? "text-red-500" : "text-blue-600"}`} />
        <div>
          <div className="text-sm font-semibold text-slate-800">{title}</div>
          {description && <div className="mt-1 text-sm text-slate-500">{description}</div>}
        </div>
      </CardContent>
    </Card>
  );
}
