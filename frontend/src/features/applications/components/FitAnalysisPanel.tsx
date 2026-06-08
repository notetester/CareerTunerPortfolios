import { Link } from "react-router";
import { AlertCircle, CheckCircle2, Target } from "lucide-react";
import { Badge } from "@/app/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Progress } from "@/app/components/ui/progress";
import type { FitAnalysisDetail } from "@/features/analysis/types/fitAnalysis";
import { parseJsonList, scoreTone } from "@/features/analysis/types/fitAnalysis";

interface FitAnalysisPanelProps {
  analyses: FitAnalysisDetail[];
  loading: boolean;
  error: string | null;
}

export function FitAnalysisPanel({ analyses, loading, error }: FitAnalysisPanelProps) {
  return (
    <div className="space-y-5">
      <div>
        <h2 className="text-lg font-bold text-slate-900">내 스펙과 공고 비교</h2>
        <p className="mt-1 text-sm text-slate-500">지원 건별 최신 적합도 분석을 기준으로 강점과 부족 역량을 확인합니다.</p>
      </div>

      {loading && <StateCard title="적합도 분석을 불러오는 중입니다." />}
      {error && <StateCard title={error} tone="error" />}
      {!loading && !error && analyses.length === 0 && (
        <StateCard title="아직 적합도 분석 결과가 없습니다." description="공고문 분석을 먼저 실행하면 지원 건별 비교 결과가 표시됩니다." />
      )}

      <div className="grid gap-4 lg:grid-cols-2">
        {analyses.map((analysis) => {
          const matchedSkills = parseJsonList(analysis.matchedSkills);
          const missingSkills = parseJsonList(analysis.missingSkills);
          const tone = scoreTone(analysis.fitScore);

          return (
            <Card key={analysis.id} className="border border-slate-200 bg-white">
              <CardHeader className="pb-3">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <CardTitle className="text-base">{analysis.application.companyName}</CardTitle>
                    <p className="mt-1 text-sm text-slate-500">{analysis.application.jobTitle}</p>
                  </div>
                  <Badge className={`${tone.bg} ${tone.text}`}>{tone.label}</Badge>
                </div>
              </CardHeader>
              <CardContent className="space-y-4">
                <div>
                  <div className="mb-1.5 flex items-center justify-between text-sm">
                    <span className="font-medium text-slate-700">직무 적합도</span>
                    <span className={`font-black ${tone.text}`}>{analysis.fitScore ?? 0}점</span>
                  </div>
                  <Progress value={analysis.fitScore ?? 0} className="h-2" />
                </div>

                <SkillList title="매칭된 역량" icon="match" items={matchedSkills} />
                <SkillList title="부족한 역량" icon="gap" items={missingSkills} />

                <Link
                  to={`/applications/${analysis.applicationCaseId}`}
                  className="inline-flex text-sm font-semibold text-blue-600 hover:text-blue-700"
                >
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

function SkillList({ title, icon, items }: { title: string; icon: "match" | "gap"; items: string[] }) {
  const Icon = icon === "match" ? CheckCircle2 : AlertCircle;
  const color = icon === "match" ? "text-green-600" : "text-red-500";
  const bg = icon === "match" ? "bg-green-50" : "bg-red-50";

  return (
    <div>
      <div className="mb-2 flex items-center gap-1.5 text-sm font-semibold text-slate-800">
        <Icon className={`size-4 ${color}`} />
        {title}
      </div>
      <div className="flex flex-wrap gap-1.5">
        {items.length > 0 ? (
          items.map((item) => (
            <span key={item} className={`rounded-full px-2 py-1 text-xs font-medium ${bg} ${color}`}>
              {item}
            </span>
          ))
        ) : (
          <span className="text-xs text-slate-400">분석된 항목 없음</span>
        )}
      </div>
    </div>
  );
}

function StateCard({ title, description, tone = "default" }: { title: string; description?: string; tone?: "default" | "error" }) {
  return (
    <Card className={`border ${tone === "error" ? "border-red-200 bg-red-50" : "border-slate-200 bg-white"}`}>
      <CardContent className="flex items-start gap-3 p-5">
        <Target className={`mt-0.5 size-5 ${tone === "error" ? "text-red-500" : "text-blue-600"}`} />
        <div>
          <div className="text-sm font-semibold text-slate-800">{title}</div>
          {description && <div className="mt-1 text-sm text-slate-500">{description}</div>}
        </div>
      </CardContent>
    </Card>
  );
}
