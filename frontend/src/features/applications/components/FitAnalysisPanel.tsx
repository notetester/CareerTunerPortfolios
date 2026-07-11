import { useState } from "react";
import { Link } from "react-router";
import { AlertCircle, Check, CheckCircle2, Database, ShieldAlert, ShieldCheck, SlidersHorizontal, Target } from "lucide-react";
import { Badge } from "@/app/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Progress } from "@/app/components/ui/progress";
import type {
  FitAnalysisConfidence,
  FitAnalysisDetail,
  FitApplyDecision,
  FitConditionMatch,
  FitGapRecommendation,
  FitScoreBreakdown,
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
          const requiredUnmet = conditionMatrix.filter(
            (row) => row.conditionType === "REQUIRED" && row.matchStatus === "UNMET",
          );
          const tone = scoreTone(analysis.fitScore);

          // min-w-0: 그리드 자식이 내부 테이블(min-w-[420px])의 최소폭으로 부풀어 모바일 페이지가 가로로 넘치는 것 방지
          // — 카드가 뷰포트에 맞춰지면 테이블은 자체 overflow-x-auto 래퍼 안에서 스크롤된다.
          return (
            <Card key={analysis.id} className="min-w-0 border border-slate-200 bg-card">
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

                {/* 필수 조건 미충족 경고: 점수와 별개로 지원 전 반드시 확인할 항목을 먼저 보여준다. */}
                {requiredUnmet.length > 0 && (
                  <div className="rounded-lg border border-red-200 bg-red-50 p-3">
                    <div className="flex items-center gap-1.5 text-sm font-semibold text-red-800">
                      <AlertCircle className="size-4" />
                      필수 조건 {requiredUnmet.length}개 미충족
                    </div>
                    <p className="mt-1 text-xs leading-5 text-red-700">
                      이 공고는 {requiredUnmet.map((row) => row.condition).join(", ")} 을(를) 필수로 요구하지만 현재
                      프로필에서 확인되지 않습니다. 지원 전 유사 경험을 프로필·지원서에 명확히 작성하거나 보완을 권장합니다.
                    </p>
                  </div>
                )}

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
                <FitImpactSimulator currentScore={analysis.fitScore ?? 0} rows={conditionMatrix} />

                <SkillList title="매칭된 역량" icon="match" items={matchedSkills} />
                <SkillList title="부족한 역량" icon="gap" items={missingSkills} />
                <DetailList title="점수 산정 근거" items={scoreBasis} />
                <ScoreBreakdownCard items={analysis.scoreBreakdown ?? []} score={analysis.fitScore ?? 0} />
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

                <SourceSnapshotViewer snapshot={analysis.sourceSnapshot} />

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

function ScoreBreakdownCard({ items, score }: { items: FitScoreBreakdown[]; score: number }) {
  if (items.length === 0) return null;
  return (
    <div className="rounded-lg border border-blue-100 bg-blue-50/40 p-3">
      <div className="mb-2 flex items-center justify-between text-sm font-semibold text-blue-900">
        <span>숫자로 보는 점수 구성</span>
        <span>{items.reduce((sum, item) => sum + item.earned, 0)} / {score}점 반영</span>
      </div>
      <div className="space-y-2">
        {items.map((item) => (
          <div key={item.key}>
            <div className="flex items-center justify-between text-xs">
              <span className="font-medium text-slate-700">{item.label}</span>
              <span className="font-bold text-blue-700">{item.earned}/{item.maximum}</span>
            </div>
            <Progress value={item.maximum === 0 ? 0 : Math.round((item.earned / item.maximum) * 100)} className="mt-1 h-1.5" />
            <div className="mt-0.5 text-[11px] text-slate-400">{item.explanation}</div>
          </div>
        ))}
      </div>
    </div>
  );
}

interface SourceSnapshot {
  jobAnalysisId: number | null;
  jobPostingRevision: number | null;
  jobAnalysisCreatedAt: string | null;
  profileUpdatedAt: string | null;
  requiredSkills?: string[];
  profileSkills?: string[];
}

/** 분석 기준 뷰어: 이 분석이 어떤 입력(공고 분석 버전·프로필 시점)으로 만들어졌는지 펼쳐 본다. */
function SourceSnapshotViewer({ snapshot }: { snapshot: string | null }) {
  const parsed = parseJsonValue<SourceSnapshot | null>(snapshot, null);
  if (!parsed) return null;

  const rows = [
    { label: "공고 분석", value: parsed.jobAnalysisId != null ? `#${parsed.jobAnalysisId} (공고 v${parsed.jobPostingRevision ?? 1})` : "분석 전" },
    { label: "공고 분석 시점", value: formatSnapshotTime(parsed.jobAnalysisCreatedAt) },
    { label: "프로필 갱신 시점", value: formatSnapshotTime(parsed.profileUpdatedAt) },
    { label: "비교 입력", value: `요구 역량 ${parsed.requiredSkills?.length ?? 0}개 · 보유 기술 ${parsed.profileSkills?.length ?? 0}개` },
  ];

  return (
    <details className="rounded-lg border border-slate-100">
      <summary className="cursor-pointer px-3 py-2 text-xs font-semibold text-slate-600 hover:text-slate-900">
        이 분석은 어떤 데이터를 기준으로 만들어졌나요?
      </summary>
      <div className="grid gap-1.5 border-t border-slate-100 px-3 py-2.5 text-xs text-slate-500">
        {rows.map((row) => (
          <div key={row.label} className="flex justify-between gap-3">
            <span className="font-medium text-slate-600">{row.label}</span>
            <span>{row.value}</span>
          </div>
        ))}
        <p className="mt-1 text-[11px] leading-4 text-slate-400">
          공고 분석이나 프로필이 이 시점 이후 바뀌었다면 적합도 재분석을 실행해 최신 기준으로 갱신하세요.
        </p>
      </div>
    </details>
  );
}

function formatSnapshotTime(value: string | null | undefined) {
  if (!value) return "정보 없음";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString("ko-KR");
}

/** 분석 신뢰도 배지. HIGH는 차분하게, MEDIUM/LOW는 경고 톤으로 표시한다. */
function ConfidenceBadge({ confidence }: { confidence: FitAnalysisConfidence }) {
  const styles =
    confidence.level === "HIGH"
      ? { className: "bg-emerald-50 text-emerald-700", label: "신뢰도 높음", Icon: ShieldCheck }
      : confidence.level === "MEDIUM"
        ? { className: "bg-amber-50 text-amber-700", label: "신뢰도 보통", Icon: ShieldAlert }
        : { className: "bg-red-50 text-red-600", label: "신뢰도 낮음", Icon: ShieldAlert };
  // 레벨과 숫자를 함께 표기("신뢰도 보통 · 72점"). score 가 없는 과거 데이터는 레벨만 표시.
  return (
    <span className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[11px] font-semibold ${styles.className}`}>
      <styles.Icon className="size-3" />
      {styles.label}
      {typeof confidence.score === "number" && <span className="opacity-80">· {confidence.score}점</span>}
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

/**
 * 스펙 보완 시뮬레이터. 저장된 조건 매트릭스를 이용한 결정적 추정치이며,
 * 실제 점수는 프로필을 보완한 뒤 재분석해야 확정된다.
 */
function FitImpactSimulator({ currentScore, rows }: { currentScore: number; rows: FitConditionMatch[] }) {
  const candidates = rows.filter((row) => row.matchStatus !== "MET").slice(0, 6);
  const [selected, setSelected] = useState<string[]>([]);
  if (candidates.length === 0) return null;

  const selectedRows = candidates.filter((row) => selected.includes(`${row.conditionType}:${row.condition}`));
  const estimatedBoost = selectedRows.reduce((sum, row) => {
    const requiredWeight = row.conditionType === "REQUIRED" ? 8 : 4;
    return sum + (row.matchStatus === "PARTIAL" ? Math.ceil(requiredWeight / 2) : requiredWeight);
  }, 0);
  const estimatedScore = Math.min(100, currentScore + estimatedBoost);

  const toggle = (row: FitConditionMatch) => {
    const key = `${row.conditionType}:${row.condition}`;
    setSelected((current) => current.includes(key) ? current.filter((item) => item !== key) : [...current, key]);
  };

  return (
    <div className="rounded-lg border border-indigo-100 bg-indigo-50/60 p-3">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <div className="flex items-center gap-1.5 text-sm font-semibold text-indigo-900">
            <SlidersHorizontal className="size-4 text-indigo-600" />
            스펙 보완 시뮬레이터
          </div>
          <p className="mt-1 text-xs leading-5 text-indigo-700">보완할 조건을 골라 예상 점수 변화를 확인하세요.</p>
        </div>
        <div className="text-right">
          <div className="text-xs text-indigo-500">예상 적합도</div>
          <div className="text-lg font-black text-indigo-700">
            {estimatedScore}점
            {estimatedBoost > 0 && <span className="ml-1 text-xs font-semibold text-green-600">+{estimatedBoost}</span>}
          </div>
        </div>
      </div>
      <Progress value={estimatedScore} className="mt-2 h-1.5" />
      <div className="mt-3 flex flex-wrap gap-1.5">
        {candidates.map((row) => {
          const key = `${row.conditionType}:${row.condition}`;
          const active = selected.includes(key);
          return (
            <button
              key={key}
              type="button"
              onClick={() => toggle(row)}
              className={`rounded-full border px-2.5 py-1 text-xs font-semibold transition-colors ${
                active
                  ? "border-indigo-300 bg-indigo-600 text-white"
                  : "border-indigo-200 bg-card text-indigo-700 hover:bg-indigo-100"
              }`}
            >
              {active ? <>보완 가정 <Check className="inline size-3 align-[-1px]" /> </> : "+ "}{row.condition}
            </button>
          );
        })}
      </div>
      <p className="mt-2 text-[11px] leading-4 text-indigo-500">
        조건 유형별 가중치로 계산한 참고 추정치입니다. 실제 점수는 프로필에 근거를 등록하고 적합도 재분석을 실행해 확인하세요.
      </p>
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
    <Card className={`border ${tone === "error" ? "border-red-200 bg-red-50" : "border-slate-200 bg-card"}`}>
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
