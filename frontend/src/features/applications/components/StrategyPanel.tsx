import { Link } from "react-router";
import { FileText, Map, MessageSquare, Target } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import type { FitAnalysisDetail, FitGapRecommendation } from "@/features/analysis/types/fitAnalysis";
import { parseJsonList, parseJsonValue, scoreTone } from "@/features/analysis/types/fitAnalysis";

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
          const matched = parseJsonList(analysis.matchedSkills);
          const gaps = parseJsonValue<FitGapRecommendation[]>(analysis.gapRecommendations, []);
          const essayPoints = essaySuggestions(matched, gaps);
          const interviewTopics = interviewSuggestions(matched, gaps);
          const phases = strategyPhases(analysis.fitScore ?? 0, actions, matched, gaps, interviewTopics);

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
                <PhasedActionPlan phases={phases} />

                {/* 자기소개서에 "어떤 내용을 넣을지"만 제안한다(문장 첨삭은 E 담당 첨삭 기능 사용). */}
                {essayPoints.length > 0 && (
                  <div className="rounded-lg border border-slate-100 p-3">
                    <div className="flex items-center gap-1.5 text-sm font-semibold text-slate-800">
                      <FileText className="size-4 text-teal-600" />
                      자기소개서에 담으면 좋은 내용
                    </div>
                    <ul className="mt-1.5 space-y-1 text-xs leading-5 text-slate-600">
                      {essayPoints.map((point) => (
                        <li key={point}>• {point}</li>
                      ))}
                    </ul>
                  </div>
                )}

                {/* 면접 질문 생성은 D 담당 면접 기능에서 — 여기서는 준비 주제만 제안한다. */}
                {interviewTopics.length > 0 && (
                  <div className="rounded-lg border border-slate-100 p-3">
                    <div className="flex items-center gap-1.5 text-sm font-semibold text-slate-800">
                      <MessageSquare className="size-4 text-purple-600" />
                      면접 대비 우선 주제
                    </div>
                    <ol className="mt-1.5 space-y-1 text-xs leading-5 text-slate-600">
                      {interviewTopics.map((topic, index) => (
                        <li key={topic}>{index + 1}. {topic}</li>
                      ))}
                    </ol>
                  </div>
                )}

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

/** 자기소개서 보완 포인트: 강점은 구체화, 필수 미충족은 유사 경험 명시를 제안한다(결정적 파생). */
function essaySuggestions(matched: string[], gaps: FitGapRecommendation[]): string[] {
  const points: string[] = [];
  if (matched.length > 0) {
    points.push(`${matched.slice(0, 2).join(", ")} 프로젝트에서 맡은 역할과 정량 성과(개선 수치·기여도)를 구체적으로 서술`);
  }
  gaps
    .filter((gap) => gap.category === "REQUIRED_MISSING")
    .slice(0, 2)
    .forEach((gap) => points.push(`${gap.skill} 관련 유사 경험이나 학습 계획을 명확히 언급해 필수 요건 공백을 보완`));
  if (matched.length > 1) {
    points.push("팀 프로젝트에서의 협업 방식과 문제 해결 과정을 1개 사례로 정리");
  }
  return points.slice(0, 4);
}

/** 면접 대비 우선 주제: 강점 검증 + 부족 역량 보완 계획 답변을 주제로 제안한다(질문 생성은 D 담당). */
function interviewSuggestions(matched: string[], gaps: FitGapRecommendation[]): string[] {
  const topics: string[] = [];
  matched.slice(0, 2).forEach((skill) => topics.push(`${skill} 실무 경험 — 선택 이유와 문제 해결 사례`));
  gaps
    .filter((gap) => gap.priority === "HIGH")
    .slice(0, 2)
    .forEach((gap) => topics.push(`${gap.skill} 미경험에 대한 보완 계획과 학습 진행 상황`));
  return topics.slice(0, 4);
}

interface StrategyPhase {
  label: string;
  description: string;
  actions: string[];
  tone: string;
}

/** 지원 전략을 즉시 판단 → 지원 전 보완 → 면접 대비 세 단계로 재구성한다. */
function strategyPhases(
  score: number,
  actions: string[],
  matched: string[],
  gaps: FitGapRecommendation[],
  interviewTopics: string[],
): StrategyPhase[] {
  const highGaps = gaps.filter((gap) => gap.priority === "HIGH").slice(0, 2);
  const immediate =
    score >= 70
      ? ["지원 일정을 확정하고 마감 전 제출 준비를 시작합니다."]
      : ["핵심 부족 역량 보완 전까지 지원 여부를 한 번 더 검토합니다."];
  if (matched.length > 0) immediate.push(`${matched.slice(0, 2).join(", ")} 경험을 이번 지원의 대표 강점으로 고정합니다.`);

  const beforeApply = actions.length > 0
    ? actions.slice(0, 3)
    : highGaps.length > 0
      ? highGaps.map((gap) => `${gap.skill} 보완 결과물과 학습 기록을 준비합니다.`)
      : ["포트폴리오와 자기소개서에 분석 결과를 반영합니다."];

  const interview = interviewTopics.length > 0
    ? interviewTopics.slice(0, 3).map((topic) => `${topic} 답변을 STAR 구조로 정리합니다.`)
    : ["강점 경험과 부족 역량 보완 계획을 각각 1분 답변으로 준비합니다."];

  return [
    { label: "즉시 판단", description: "오늘 결정할 지원 방향", actions: immediate, tone: "border-blue-100 bg-blue-50 text-blue-900" },
    { label: "지원 전 보완", description: "제출 전 완료할 실행 항목", actions: beforeApply, tone: "border-amber-100 bg-amber-50 text-amber-900" },
    { label: "면접 대비", description: "면접에서 검증될 우선 주제", actions: interview, tone: "border-purple-100 bg-purple-50 text-purple-900" },
  ];
}

function PhasedActionPlan({ phases }: { phases: StrategyPhase[] }) {
  return (
    <div>
      <div className="mb-2 flex items-center gap-1.5 text-sm font-semibold text-slate-800">
        <Target className="size-4 text-blue-600" />
        3단계 지원 액션 플랜
      </div>
      <div className="grid gap-2">
        {phases.map((phase) => (
          <div key={phase.label} className={`rounded-lg border p-3 ${phase.tone}`}>
            <div className="text-sm font-bold">{phase.label}</div>
            <div className="mt-0.5 text-[11px] opacity-70">{phase.description}</div>
            <ul className="mt-1.5 space-y-1 text-xs leading-5">
              {phase.actions.map((action) => <li key={action}>• {action}</li>)}
            </ul>
          </div>
        ))}
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
