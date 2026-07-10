import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router";
import { BarChart3, Eye, Loader2, Pencil } from "lucide-react";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { AiChargeCostBadge } from "@/features/billing/components/AiChargeCostBadge";
import { Input } from "@/app/components/ui/input";
import { Textarea } from "@/app/components/ui/textarea";
import type {
  AmbiguousConditionRow,
  BAnalysisFailureLog,
  EvidenceRow,
  JobAnalysis,
  JobAnalysisReviewRequest,
} from "../types/analysis";
import {
  formatJsonArrayForTextarea,
  getDifficultyLabel,
  parseAmbiguousConditionRows,
  parseEvidenceRows,
  parseJsonStringArray,
  serializeAmbiguousConditionRows,
  serializeEvidenceRows,
  serializeTextareaList,
} from "../types/analysis";
import type { ApplicationSourceType } from "../types/applicationCase";
import { formatKoreaDateTime } from "../utils/dateFormat";
import { AnalysisFailureNotice } from "./AnalysisFailureNotice";
import { AnalysisReanalyzeButton } from "./AnalysisReanalyzeButton";
import { AnalysisStructuredText } from "./AnalysisStructuredText";
import { StructuredRowsEditor, type StructuredRowsEditorField } from "./StructuredRowsEditor";

interface JobAnalysisPanelProps {
  analysis: JobAnalysis | null;
  history: JobAnalysis[];
  mode: "view" | "edit";
  viewHref: string;
  editHref: string;
  loading: boolean;
  generating: boolean;
  reviewSaving: boolean;
  error: string | null;
  reviewError: string | null;
  failures: BAnalysisFailureLog[];
  latestJobPostingRevision: number | null;
  /** model-options 조회용(분석 선택지는 sourceType 무관하나 API 계약상 전달). */
  sourceType: ApplicationSourceType;
  onGenerate(provider: string): Promise<JobAnalysis | null>;
  onReview(analysisId: number, request: JobAnalysisReviewRequest): Promise<JobAnalysis | null>;
}

function SkillList({ title, value }: { title: string; value: string | null }) {
  const items = parseJsonStringArray(value);

  return (
    <div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
      <div className="text-xs font-semibold text-slate-500">{title}</div>
      {items.length > 0 ? (
        <div className="mt-2 flex flex-wrap gap-1.5">
          {items.map((item) => (
            <span key={item} className="rounded-full bg-card px-2.5 py-1 text-xs font-semibold text-slate-700 shadow-sm">
              {item}
            </span>
          ))}
        </div>
      ) : (
        <div className="mt-2 text-sm text-slate-400">미정</div>
      )}
    </div>
  );
}

type JobStructuredRows = {
  evidence: EvidenceRow[];
  ambiguousConditions: AmbiguousConditionRow[];
};

const EMPTY_EVIDENCE_ROW: EvidenceRow = { field: "", quote: "" };
const EMPTY_AMBIGUOUS_CONDITION_ROW: AmbiguousConditionRow = { condition: "", assumption: "" };

const EVIDENCE_EDITOR_FIELDS: readonly StructuredRowsEditorField<EvidenceRow>[] = [
  { key: "field", label: "항목", placeholder: "예: 자격 요건" },
  { key: "quote", label: "인용문", placeholder: "공고 문구를 입력" },
];

const AMBIGUOUS_CONDITION_EDITOR_FIELDS: readonly StructuredRowsEditorField<AmbiguousConditionRow>[] = [
  { key: "condition", label: "조건", placeholder: "해석이 필요한 조건" },
  { key: "assumption", label: "가정", placeholder: "사용자 가정" },
];

function getHistoryLabel(index: number) {
  return index === 0 ? "최신 분석" : `이전 분석 ${index}`;
}

export function JobAnalysisPanel({
  analysis,
  history,
  mode,
  viewHref,
  editHref,
  loading,
  generating,
  reviewSaving,
  error,
  reviewError,
  failures,
  latestJobPostingRevision,
  sourceType,
  onGenerate,
  onReview,
}: JobAnalysisPanelProps) {
  const [form, setForm] = useState({
    employmentType: "",
    experienceLevel: "",
    requiredSkills: "",
    preferredSkills: "",
    duties: "",
    qualifications: "",
    difficulty: "",
    summary: "",
  });
  const [structuredRows, setStructuredRows] = useState<JobStructuredRows>({
    evidence: [],
    ambiguousConditions: [],
  });
  const [structuredFieldEdited, setStructuredFieldEdited] = useState({
    evidence: false,
    ambiguousConditions: false,
  });
  const [reviewSuccess, setReviewSuccess] = useState<string | null>(null);

  useEffect(() => {
    setForm({
      employmentType: analysis?.employmentType ?? "",
      experienceLevel: analysis?.experienceLevel ?? "",
      requiredSkills: formatJsonArrayForTextarea(analysis?.requiredSkills),
      preferredSkills: formatJsonArrayForTextarea(analysis?.preferredSkills),
      duties: analysis?.duties ?? "",
      qualifications: analysis?.qualifications ?? "",
      difficulty: analysis?.difficulty ?? "",
      summary: analysis?.summary ?? "",
    });
    setStructuredRows({
      evidence: parseEvidenceRows(analysis?.evidence),
      ambiguousConditions: parseAmbiguousConditionRows(analysis?.ambiguousConditions),
    });
    setStructuredFieldEdited({
      evidence: false,
      ambiguousConditions: false,
    });
  }, [analysis]);

  const setField = (key: keyof typeof form, value: string) => {
    setForm((current) => ({ ...current, [key]: value }));
    setReviewSuccess(null);
  };

  const setStructuredField = <Key extends keyof JobStructuredRows>(key: Key, rows: JobStructuredRows[Key]) => {
    setStructuredRows((current) => ({ ...current, [key]: rows }));
    setStructuredFieldEdited((current) => ({ ...current, [key]: true }));
    setReviewSuccess(null);
  };

  const isDirty = useMemo(() => {
    if (!analysis) return false;

    return (
      form.employmentType !== (analysis.employmentType ?? "") ||
      form.experienceLevel !== (analysis.experienceLevel ?? "") ||
      form.requiredSkills !== formatJsonArrayForTextarea(analysis.requiredSkills) ||
      form.preferredSkills !== formatJsonArrayForTextarea(analysis.preferredSkills) ||
      form.duties !== (analysis.duties ?? "") ||
      form.qualifications !== (analysis.qualifications ?? "") ||
      form.difficulty !== (analysis.difficulty ?? "") ||
      form.summary !== (analysis.summary ?? "") ||
      structuredFieldEdited.evidence ||
      structuredFieldEdited.ambiguousConditions
    );
  }, [analysis, form, structuredFieldEdited]);

  const isStale = Boolean(
    analysis &&
    latestJobPostingRevision !== null &&
    analysis.jobPostingRevision !== latestJobPostingRevision,
  );

  const handleGenerate = async (provider: string) => {
    if (
      isDirty &&
      !window.confirm("저장하지 않은 검토 수정 내용이 있습니다. 재분석을 진행하면 입력 중인 내용이 사라질 수 있습니다. 계속할까요?")
    ) {
      return;
    }

    setReviewSuccess(null);
    await onGenerate(provider);
  };

  const handleReview = async () => {
    if (!analysis) return;
    setReviewSuccess(null);
    const reviewed = await onReview(analysis.id, {
      ...form,
      requiredSkills: serializeTextareaList(form.requiredSkills),
      preferredSkills: serializeTextareaList(form.preferredSkills),
      evidence: structuredFieldEdited.evidence ? serializeEvidenceRows(structuredRows.evidence) : undefined,
      ambiguousConditions: structuredFieldEdited.ambiguousConditions
        ? serializeAmbiguousConditionRows(structuredRows.ambiguousConditions)
        : undefined,
      confirmed: true,
    });
    if (reviewed) {
      setReviewSuccess("수정 내용을 저장하고 확정했습니다.");
    }
  };

  return (
    <Card className="border-slate-200 bg-card">
      <CardHeader className="gap-3">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
          <div>
            <CardTitle className="flex items-center gap-2 text-lg font-bold text-slate-900">
              <BarChart3 className="size-5 text-blue-600" />
              공고 분석
              {isStale && (
                <span className="rounded-full border border-amber-200 bg-amber-50 px-2 py-0.5 text-xs font-semibold text-amber-700">
                  이전 공고 rev 기준
                </span>
              )}
            </CardTitle>
            {analysis ? (
              <p className="mt-1 text-xs text-slate-500">
                최근 분석: {formatKoreaDateTime(analysis.createdAt)}
                {analysis.jobPostingRevision ? ` · 공고 rev ${analysis.jobPostingRevision}` : ""}
                {analysis.confirmedAt ? ` · 확정 ${formatKoreaDateTime(analysis.confirmedAt)}` : ""}
              </p>
            ) : (
              <p className="mt-1 text-xs text-slate-500">분석 결과 없음</p>
            )}
            <p className="mt-1 text-xs text-slate-500">
              공고문 길이와 AI 응답 상태에 따라 분석 완료까지 시간이 걸릴 수 있습니다.
            </p>
          </div>
          <div className="flex flex-wrap gap-2 sm:justify-end">
            <AiChargeCostBadge featureType="JOB_ANALYSIS" />
            {analysis && mode === "view" && (
              <Button asChild size="sm" variant="outline">
                <Link to={editHref}>
                  <Pencil className="size-4" />
                  수정 화면
                </Link>
              </Button>
            )}
            {analysis && mode === "edit" && (
              <Button asChild size="sm" variant="outline">
                <Link to={viewHref}>
                  <Eye className="size-4" />
                  조회 화면
                </Link>
              </Button>
            )}
            <AnalysisReanalyzeButton
              stage="jobAnalysis"
              sourceType={sourceType}
              onReanalyze={(provider) => void handleGenerate(provider)}
              pending={generating}
              disabled={loading || reviewSaving}
              label={analysis ? "AI 재분석" : "AI 분석 실행"}
              variant="default"
              className="bg-blue-600 text-white hover:bg-blue-700"
            />
          </div>
        </div>
      </CardHeader>
      <CardContent className="space-y-4">
        {!loading && (
          <AnalysisFailureNotice
            failures={failures}
            featureType="JOB_ANALYSIS"
            retryAction={
              <AnalysisReanalyzeButton
                stage="jobAnalysis"
                sourceType={sourceType}
                onReanalyze={(provider) => void handleGenerate(provider)}
                pending={generating}
                disabled={loading || reviewSaving}
                label="공고 분석 다시 시도"
              />
            }
          />
        )}

        {isStale && (
          <div className="flex flex-col gap-3 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800 sm:flex-row sm:items-center sm:justify-between">
            <span>
              현재 분석은 공고 rev {analysis?.jobPostingRevision ?? "-"} 기준입니다. 최신 공고 rev {latestJobPostingRevision} 기준으로 다시 분석할 수 있습니다.
            </span>
            <AnalysisReanalyzeButton
              stage="jobAnalysis"
              sourceType={sourceType}
              onReanalyze={(provider) => void handleGenerate(provider)}
              pending={generating}
              disabled={loading || reviewSaving}
              label="최신 공고로 재분석"
              className="border-amber-300 bg-card text-amber-800 hover:bg-amber-100"
            />
          </div>
        )}

        {loading ? (
          <div className="h-64 animate-pulse rounded-lg bg-slate-100" />
        ) : analysis ? (
          <>
            <div className="grid gap-3 md:grid-cols-3">
              <div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
                <div className="text-xs font-semibold text-slate-500">고용 형태</div>
                <div className="mt-1 text-sm font-bold text-slate-900">{analysis.employmentType ?? "미정"}</div>
              </div>
              <div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
                <div className="text-xs font-semibold text-slate-500">경력 수준</div>
                <div className="mt-1 text-sm font-bold text-slate-900">{analysis.experienceLevel ?? "미정"}</div>
              </div>
              <div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
                <div className="text-xs font-semibold text-slate-500">난이도</div>
                <div className="mt-1 text-sm font-bold text-slate-900">{getDifficultyLabel(analysis.difficulty)}</div>
              </div>
            </div>

            <div className="grid gap-3 md:grid-cols-2">
              <SkillList title="필수 역량" value={analysis.requiredSkills} />
              <SkillList title="우대 역량" value={analysis.preferredSkills} />
            </div>

            <div className="grid gap-3 md:grid-cols-2">
              <div className="rounded-lg border border-slate-200 p-4">
                <div className="text-sm font-semibold text-slate-900">주요 업무</div>
                <p className="mt-2 whitespace-pre-line text-sm leading-6 text-slate-600">{analysis.duties ?? "내용 없음"}</p>
              </div>
              <div className="rounded-lg border border-slate-200 p-4">
                <div className="text-sm font-semibold text-slate-900">자격 요건</div>
                <p className="mt-2 whitespace-pre-line text-sm leading-6 text-slate-600">{analysis.qualifications ?? "내용 없음"}</p>
              </div>
            </div>

            <div className="rounded-lg border border-blue-100 bg-blue-50 p-4">
              <div className="text-sm font-semibold text-blue-950">요약</div>
              <p className="mt-2 whitespace-pre-line text-sm leading-6 text-blue-900">{analysis.summary ?? "내용 없음"}</p>
            </div>

            <div className="grid gap-3 md:grid-cols-2">
              <AnalysisStructuredText title="근거" value={analysis.evidence} />
              <AnalysisStructuredText title="모호한 조건" value={analysis.ambiguousConditions} />
            </div>

            {mode === "edit" && (
              <div className="space-y-3 rounded-lg border border-slate-200 bg-slate-50 p-4">
                <div className="flex flex-wrap items-center gap-2 text-sm font-semibold text-slate-900">
                  사용자 확인/수정
                  {isDirty && (
                    <span className="rounded-full bg-amber-100 px-2 py-0.5 text-xs font-semibold text-amber-700">
                      저장되지 않은 수정 있음
                    </span>
                  )}
                </div>
                <div className="grid gap-3 md:grid-cols-3">
                  <Input value={form.employmentType} onChange={(event) => setField("employmentType", event.target.value)} placeholder="고용 형태" />
                  <Input value={form.experienceLevel} onChange={(event) => setField("experienceLevel", event.target.value)} placeholder="경력 수준" />
                  <Input value={form.difficulty} onChange={(event) => setField("difficulty", event.target.value)} placeholder="EASY/NORMAL/HARD" />
                </div>
                <div className="grid gap-3 md:grid-cols-2">
                  <Textarea value={form.requiredSkills} onChange={(event) => setField("requiredSkills", event.target.value)} className="min-h-24 bg-card" placeholder={"필수 역량을 한 줄에 하나씩 입력"} />
                  <Textarea value={form.preferredSkills} onChange={(event) => setField("preferredSkills", event.target.value)} className="min-h-24 bg-card" placeholder={"우대 역량을 한 줄에 하나씩 입력"} />
                </div>
                <div className="grid gap-3 md:grid-cols-2">
                  <Textarea value={form.duties} onChange={(event) => setField("duties", event.target.value)} className="min-h-28 bg-card" placeholder="주요 업무" />
                  <Textarea value={form.qualifications} onChange={(event) => setField("qualifications", event.target.value)} className="min-h-28 bg-card" placeholder="자격 요건" />
                </div>
                <Textarea value={form.summary} onChange={(event) => setField("summary", event.target.value)} className="min-h-24 bg-card" placeholder="요약" />
                <div className="grid gap-3 md:grid-cols-2">
                  <StructuredRowsEditor
                    title="근거"
                    rows={structuredRows.evidence}
                    fields={EVIDENCE_EDITOR_FIELDS}
                    emptyRow={EMPTY_EVIDENCE_ROW}
                    onChange={(rows) => setStructuredField("evidence", rows)}
                  />
                  <StructuredRowsEditor
                    title="모호한 조건"
                    rows={structuredRows.ambiguousConditions}
                    fields={AMBIGUOUS_CONDITION_EDITOR_FIELDS}
                    emptyRow={EMPTY_AMBIGUOUS_CONDITION_ROW}
                    onChange={(rows) => setStructuredField("ambiguousConditions", rows)}
                  />
                </div>
                {reviewSuccess && (
                  <div className="rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-700">
                    {reviewSuccess}
                  </div>
                )}
                {reviewError && (
                  <div className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
                    {reviewError}
                  </div>
                )}
                <Button type="button" className="bg-foreground text-background hover:bg-foreground/90" disabled={generating || reviewSaving} onClick={() => void handleReview()}>
                  {reviewSaving && <Loader2 className="size-4 animate-spin" />}
                  수정 내용 저장 및 확정
                </Button>
              </div>
            )}
          </>
        ) : (
          <div className="rounded-lg border border-dashed border-slate-300 bg-slate-50 p-6">
            <div className="text-sm font-semibold text-slate-800">분석 결과 없음</div>
            <p className="mt-2 text-sm leading-6 text-slate-500">
              공고문을 먼저 저장한 뒤 AI 분석을 실행하면 고용 형태, 경력 수준, 요구 역량, 주요 업무가 저장됩니다.
            </p>
          </div>
        )}

        {error && (
          <div className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
            {error}
          </div>
        )}

        {history.length > 0 && (
          <div className="rounded-lg border border-slate-200 bg-card">
            <div className="border-b border-slate-100 px-3 py-2 text-xs font-semibold text-slate-500">분석 이력</div>
            <div className="divide-y divide-slate-100">
              {history.map((item, index) => (
                <div key={item.id} className="flex flex-wrap items-center gap-3 px-3 py-2 text-xs text-slate-600">
                  <span className="font-semibold text-slate-900">{getHistoryLabel(index)}</span>
                  <span>공고 rev {item.jobPostingRevision ?? "-"}</span>
                  <span>{formatKoreaDateTime(item.createdAt)}</span>
                  <span>{item.confirmedAt ? "확정" : "미확정"}</span>
                  <span className="max-w-md truncate text-slate-400">{item.summary ?? "요약 없음"}</span>
                </div>
              ))}
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
