import { Link } from "react-router";
import { AlertCircle, CheckCircle2, Database, ShieldAlert, ShieldCheck, Target } from "lucide-react";
import { Badge } from "@/app/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Progress } from "@/app/components/ui/progress";
import type {
  FitAnalysisConfidence,
  FitAnalysisDetail,
  FitApplyDecision,
  FitConditionMatch,
  FitGapRecommendation,
} from "@/features/analysis/types/fitAnalysis";
import { parseJsonList, parseJsonValue, scoreBandDescription, scoreTone } from "@/features/analysis/types/fitAnalysis";
import { AiResultBadge } from "@/features/analysis/components/AiResultBadge";
import { FitAnalysisProgress } from "@/features/analysis/components/FitAnalysisProgress";

interface FitAnalysisPanelProps {
  analyses: FitAnalysisDetail[];
  loading: boolean;
  /** 적합도 분석 생성/재생성 요청이 진행 중일 때 단계별 진행 상태를 보여준다(디자인 분석 §9). */
  generating?: boolean;
  error: string | null;
}

export function FitAnalysisPanel({ analyses, loading, generating = false, error }: FitAnalysisPanelProps) {
  return (
    <div className="space-y-5">
      <div>
        <h2 className="text-lg font-bold text-slate-900">내 스펙과 공고 비교</h2>
        <p className="mt-1 text-sm text-slate-500">지원 건별 최신 적합도 분석을 기준으로 강점과 부족 역량을 확인합니다.</p>
      </div>

      {generating && <FitAnalysisProgress />}
      {!generating && loading && <StateCard title="적합도 분석을 불러오는 중입니다." />}
      {error && <StateCard title={error} tone="error" />}
      {!generating && !loading && !error && analyses.length === 0 && (
        <StateCard title="아직 적합도 분석 결과가 없습니다." description="공고문 분석을 먼저 실행하면 지원 건별 비교 결과가 표시됩니다." />
      )}

      <div className="grid gap-4 lg:grid-cols-2">
        {analyses.map((analysis) => {
          const matchedSkills = parseJsonList(analysis.matchedSkills);
          const missingSkills = parseJsonList(analysis.missingSkills);
          const scoreBasis = parseJsonList(analysis.scoreBasis);
          const gaps = parseJsonValue<FitGapRecommendation[]>(analysis.gapRecommendations, []);
          const conditionMatrix = parseJsonValue<FitConditionMatch[]>(analysis.conditionMatrix, []);
          const confidence = parseJsonValue<FitAnalysisConfidence | null>(analysis.analysisConfidence, null);
          const decision = parseJsonValue<FitApplyDecision | null>(analysis.applyDecision, null);
          const tone = scoreTone(analysis.fitScore);

          return (
            <Card key={analysis.id} className="border border-slate-200 bg-white">
              <CardHeader className="pb-3">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <CardTitle className="text-base">{analysis.application.companyName}</CardTitle>
                    <p className="mt-1 text-sm text-slate-500">{analysis.application.jobTitle}</p>
                    <div className="mt-1.5 flex flex-wrap items-center gap-1.5">
                      <AiResultBadge status={analysis.status} />
                      {confidence && <ConfidenceBadge confidence={confidence} />}
                    </div>
                  </div>
                  {/* 점수+구간 하이브리드 표기(기획 §8.6) */}
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
                  {/* 점수 구간 설명: 숫자만이 아니라 구간의 의미를 함께 안내한다. */}
                  <p className="mt-1.5 text-xs leading-5 text-slate-500">{scoreBandDescription(analysis.fitScore)}</p>
                </div>

                {decision && <ApplyDecisionCard decision={decision} />}

                {/* 분석 신뢰도가 낮으면 점수보다 입력 보강을 먼저 안내한다. */}
                {confidence && confidence.level !== "HIGH" && confidence.reasons.length > 0 && (
                  <div className="rounded-lg border border-amber-200 bg-amber-50 p-3">
                    <div className="flex items-center gap-1.5 text-sm font-semibold text-amber-800">
                      <ShieldAlert className="size-4" />
                      분석 신뢰도 {confidence.level === "LOW" ? "낮음" : "보통"}
                    </div>
                    <ul className="mt-1.5 space-y-1 text-xs leading-5 text-amber-700">
                      {confidence.reasons.map((reason) => (
                        <li key={reason}>• {reason}</li>
                      ))}
                    </ul>
                  </div>
                )}

                <ConditionMatrixTable rows={conditionMatrix} />

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

/** 분석 신뢰도 배지. HIGH는 차분하게, MEDIUM/LOW는 경고 톤으로 표시한다. */
function ConfidenceBadge({ confidence }: { confidence: FitAnalysisConfidence }) {
  const styles =
    confidence.level === "HIGH"
      ? { className: "bg-emerald-50 text-emerald-700", label: "신뢰도 높음", Icon: ShieldCheck }
      : confidence.level === "MEDIUM"
        ? { className: "bg-amber-50 text-amber-700", label: "신뢰도 보통", Icon: ShieldAlert }
        : { className: "bg-red-50 text-red-600", label: "신뢰도 낮음", Icon: ShieldAlert };
  return (
    <span className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[11px] font-semibold ${styles.className}`}>
      <styles.Icon className="size-3" />
      {styles.label}
    </span>
  );
}

/** "지원해도 되는가?" 최종 판단 카드. */
function ApplyDecisionCard({ decision }: { decision: FitApplyDecision }) {
  const meta =
    decision.decision === "APPLY"
      ? { label: "지원 가능", border: "border-green-200", bg: "bg-green-50", text: "text-green-800", sub: "text-green-700" }
      : decision.decision === "HOLD"
        ? { label: "지원 보류 권장", border: "border-red-200", bg: "bg-red-50", text: "text-red-800", sub: "text-red-700" }
        : { label: "보완 후 지원 권장", border: "border-amber-200", bg: "bg-amber-50", text: "text-amber-800", sub: "text-amber-700" };

  return (
    <div className={`rounded-lg border ${meta.border} ${meta.bg} p-3.5`}>
      <div className={`flex items-center gap-1.5 text-sm font-bold ${meta.text}`}>
        <Target className="size-4" />
        판단: {meta.label}
      </div>
      {decision.reasons.length > 0 && (
        <ul className={`mt-2 space-y-1 text-xs leading-5 ${meta.sub}`}>
          {decision.reasons.map((reason) => (
            <li key={reason}>• {reason}</li>
          ))}
        </ul>
      )}
      {decision.actions.length > 0 && (
        <div className="mt-2.5">
          <div className={`text-xs font-semibold ${meta.text}`}>추천 행동</div>
          <ol className={`mt-1 space-y-1 text-xs leading-5 ${meta.sub}`}>
            {decision.actions.map((action, index) => (
              <li key={action}>{index + 1}. {action}</li>
            ))}
          </ol>
        </div>
      )}
    </div>
  );
}

/** 요구조건-스펙 비교 매트릭스. 공고 조건별 보유 여부와 근거를 표로 보여준다. */
function ConditionMatrixTable({ rows }: { rows: FitConditionMatch[] }) {
  if (rows.length === 0) return null;

  return (
    <div>
      <div className="mb-2 flex items-center gap-1.5 text-sm font-semibold text-slate-800">
        <CheckCircle2 className="size-4 text-blue-600" />
        요구조건-스펙 비교
      </div>
      <div className="overflow-x-auto rounded-lg border border-slate-100">
        <table className="w-full min-w-[420px] text-left text-xs">
          <thead className="bg-slate-50 text-slate-500">
            <tr>
              <th className="px-3 py-2 font-semibold">공고 요구사항</th>
              <th className="px-3 py-2 font-semibold">구분</th>
              <th className="px-3 py-2 font-semibold">판정</th>
              <th className="px-3 py-2 font-semibold">근거</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {rows.map((row) => (
              <tr key={`${row.conditionType}-${row.condition}`}>
                <td className="px-3 py-2 font-medium text-slate-800">{row.condition}</td>
                <td className="px-3 py-2 text-slate-500">{row.conditionType === "REQUIRED" ? "필수" : "우대"}</td>
                <td className="px-3 py-2">
                  <span className={`rounded-full px-2 py-0.5 font-semibold ${matchStatusTone(row.matchStatus)}`}>
                    {matchStatusLabel(row.matchStatus)}
                  </span>
                </td>
                <td className="px-3 py-2 text-slate-500">{row.evidence}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function matchStatusLabel(status: string) {
  return status === "MET" ? "충족" : status === "PARTIAL" ? "부분 충족" : "미충족";
}

function matchStatusTone(status: string) {
  if (status === "MET") return "bg-green-50 text-green-700";
  if (status === "PARTIAL") return "bg-amber-50 text-amber-700";
  return "bg-red-50 text-red-600";
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
