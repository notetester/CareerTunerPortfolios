import { Link } from "react-router";
import { AlertCircle, CheckCircle2, Database, Target } from "lucide-react";
import { Badge } from "@/app/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Progress } from "@/app/components/ui/progress";
import type { FitAnalysisDetail } from "@/features/analysis/types/fitAnalysis";
import type { FitGapRecommendation } from "@/features/analysis/types/fitAnalysis";
import { parseJsonList, parseJsonValue, scoreTone } from "@/features/analysis/types/fitAnalysis";

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
          const scoreBasis = parseJsonList(analysis.scoreBasis);
          const gaps = parseJsonValue<FitGapRecommendation[]>(analysis.gapRecommendations, []);
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
                <DetailList title="점수 산정 근거" items={scoreBasis} />
                <div>
                  <div className="mb-2 flex items-center gap-1.5 text-sm font-semibold text-slate-800">
                    <AlertCircle className="size-4 text-amber-600" />
                    보완 우선순위
                  </div>
                  <div className="space-y-2">
                    {gaps.length > 0 ? gaps.map((gap) => (
                      <div key={`${gap.category}-${gap.skill}`} className="rounded-lg border border-slate-100 p-3">
                        <div className="flex items-center justify-between gap-2">
                          <span className="text-sm font-semibold text-slate-800">{gap.skill}</span>
                          <Badge variant="outline">{priorityLabel(gap.priority)}</Badge>
                        </div>
                        <div className="mt-1 text-xs text-slate-500">{categoryLabel(gap.category)} · {gap.reason}</div>
                      </div>
                    )) : <span className="text-xs text-slate-400">분석된 보완 항목 없음</span>}
                  </div>
                </div>

                <div className="flex items-center gap-2 rounded-lg bg-slate-50 px-3 py-2 text-xs text-slate-500">
                  <Database className="size-3.5" />
                  {analysis.model || "mock"} · {analysis.createdAt ? new Date(analysis.createdAt).toLocaleString("ko-KR") : "생성 시각 없음"}
                </div>

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

function DetailList({ title, items }: { title: string; items: string[] }) {
  return (
    <div>
      <div className="mb-2 text-sm font-semibold text-slate-800">{title}</div>
      <ul className="space-y-1 text-sm text-slate-600">
        {items.length > 0 ? items.map((item) => <li key={item}>• {item}</li>) : <li className="text-slate-400">분석 근거 없음</li>}
      </ul>
    </div>
  );
}

function priorityLabel(priority: string) {
  return priority === "HIGH" ? "높음" : priority === "MEDIUM" ? "보통" : "낮음";
}

function categoryLabel(category: string) {
  if (category === "REQUIRED_MISSING") return "필수 역량";
  if (category === "PREFERRED_GAP") return "우대 역량";
  return "장기 성장";
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
