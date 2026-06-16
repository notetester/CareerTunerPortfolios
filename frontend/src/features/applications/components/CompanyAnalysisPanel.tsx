import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router";
import { Building2, Eye, Loader2, Pencil, PlayCircle } from "lucide-react";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Input } from "@/app/components/ui/input";
import { Textarea } from "@/app/components/ui/textarea";
import type {
  AiInferenceRow,
  BAnalysisFailureLog,
  CompanyAnalysis,
  CompanyAnalysisReviewRequest,
  VerifiedFactRow,
} from "../types/analysis";
import {
  formatJsonArrayForTextarea,
  parseAiInferenceRows,
  parseJsonStringArray,
  parseVerifiedFactRows,
  serializeAiInferenceRows,
  serializeTextareaList,
  serializeVerifiedFactRows,
} from "../types/analysis";
import { formatKoreaDateTime } from "../utils/dateFormat";
import { AnalysisFailureNotice } from "./AnalysisFailureNotice";
import { AnalysisStructuredText } from "./AnalysisStructuredText";
import { StructuredRowsEditor, type StructuredRowsEditorField } from "./StructuredRowsEditor";

interface CompanyAnalysisPanelProps {
  analysis: CompanyAnalysis | null;
  history: CompanyAnalysis[];
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
  onGenerate(): Promise<CompanyAnalysis | null>;
  onReview(analysisId: number, request: CompanyAnalysisReviewRequest): Promise<CompanyAnalysis | null>;
}

function JsonList({ title, value }: { title: string; value: string | null }) {
  const items = parseJsonStringArray(value);

  return (
    <div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
      <div className="text-xs font-semibold text-slate-500">{title}</div>
      {items.length > 0 ? (
        <div className="mt-2 flex flex-wrap gap-1.5">
          {items.map((item) => (
            <span key={item} className="rounded-full bg-white px-2.5 py-1 text-xs font-semibold text-slate-700 shadow-sm">
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

type CompanyStructuredRows = {
  verifiedFacts: VerifiedFactRow[];
  aiInferences: AiInferenceRow[];
};

const EMPTY_VERIFIED_FACT_ROW: VerifiedFactRow = { fact: "", source: "" };
const EMPTY_AI_INFERENCE_ROW: AiInferenceRow = { inference: "", basis: "" };

const VERIFIED_FACT_EDITOR_FIELDS: readonly StructuredRowsEditorField<VerifiedFactRow>[] = [
  { key: "fact", label: "사실", placeholder: "검증된 사실" },
  { key: "source", label: "출처", placeholder: "확인 출처" },
];

const AI_INFERENCE_EDITOR_FIELDS: readonly StructuredRowsEditorField<AiInferenceRow>[] = [
  { key: "inference", label: "추론", placeholder: "AI 추론" },
  { key: "basis", label: "근거", placeholder: "추론 근거" },
];

function getHistoryLabel(index: number) {
  return index === 0 ? "최신 분석" : `이전 분석 ${index}`;
}

export function CompanyAnalysisPanel({
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
  onGenerate,
  onReview,
}: CompanyAnalysisPanelProps) {
  const [form, setForm] = useState({
    companySummary: "",
    recentIssues: "",
    industry: "",
    competitors: "",
    interviewPoints: "",
    sources: "",
  });
  const [structuredRows, setStructuredRows] = useState<CompanyStructuredRows>({
    verifiedFacts: [],
    aiInferences: [],
  });
  const [structuredFieldEdited, setStructuredFieldEdited] = useState({
    verifiedFacts: false,
    aiInferences: false,
  });
  const [reviewSuccess, setReviewSuccess] = useState<string | null>(null);

  useEffect(() => {
    setForm({
      companySummary: analysis?.companySummary ?? "",
      recentIssues: analysis?.recentIssues ?? "",
      industry: analysis?.industry ?? "",
      competitors: formatJsonArrayForTextarea(analysis?.competitors),
      interviewPoints: analysis?.interviewPoints ?? "",
      sources: formatJsonArrayForTextarea(analysis?.sources),
    });
    setStructuredRows({
      verifiedFacts: parseVerifiedFactRows(analysis?.verifiedFacts),
      aiInferences: parseAiInferenceRows(analysis?.aiInferences),
    });
    setStructuredFieldEdited({
      verifiedFacts: false,
      aiInferences: false,
    });
  }, [analysis]);

  const setField = (key: keyof typeof form, value: string) => {
    setForm((current) => ({ ...current, [key]: value }));
    setReviewSuccess(null);
  };

  const setStructuredField = <Key extends keyof CompanyStructuredRows>(key: Key, rows: CompanyStructuredRows[Key]) => {
    setStructuredRows((current) => ({ ...current, [key]: rows }));
    setStructuredFieldEdited((current) => ({ ...current, [key]: true }));
    setReviewSuccess(null);
  };

  const isDirty = useMemo(() => {
    if (!analysis) return false;

    return (
      form.companySummary !== (analysis.companySummary ?? "") ||
      form.recentIssues !== (analysis.recentIssues ?? "") ||
      form.industry !== (analysis.industry ?? "") ||
      form.competitors !== formatJsonArrayForTextarea(analysis.competitors) ||
      form.interviewPoints !== (analysis.interviewPoints ?? "") ||
      form.sources !== formatJsonArrayForTextarea(analysis.sources) ||
      structuredFieldEdited.verifiedFacts ||
      structuredFieldEdited.aiInferences
    );
  }, [analysis, form, structuredFieldEdited]);

  const isStale = Boolean(
    analysis &&
    latestJobPostingRevision !== null &&
    analysis.jobPostingRevision !== latestJobPostingRevision,
  );

  const handleGenerate = async () => {
    if (
      isDirty &&
      !window.confirm("저장하지 않은 검토 수정 내용이 있습니다. 재분석을 진행하면 입력 중인 내용이 사라질 수 있습니다. 계속할까요?")
    ) {
      return;
    }

    setReviewSuccess(null);
    await onGenerate();
  };

  const sourceMetadata = analysis
    ? [
        { label: "출처 유형", value: analysis.sourceType },
        { label: "확인 시각", value: analysis.checkedAt ? formatKoreaDateTime(analysis.checkedAt) : null },
        {
          label: "갱신 권장",
          value: analysis.refreshRecommendedAt ? formatKoreaDateTime(analysis.refreshRecommendedAt) : null,
        },
      ].filter((item): item is { label: string; value: string } => Boolean(item.value))
    : [];

  const handleReview = async () => {
    if (!analysis) return;
    setReviewSuccess(null);
    const reviewed = await onReview(analysis.id, {
      ...form,
      competitors: serializeTextareaList(form.competitors),
      sources: serializeTextareaList(form.sources),
      verifiedFacts: structuredFieldEdited.verifiedFacts
        ? serializeVerifiedFactRows(structuredRows.verifiedFacts)
        : undefined,
      aiInferences: structuredFieldEdited.aiInferences
        ? serializeAiInferenceRows(structuredRows.aiInferences)
        : undefined,
      confirmed: true,
    });
    if (reviewed) {
      setReviewSuccess("수정 내용을 저장하고 확정했습니다.");
    }
  };

  return (
    <Card className="border-slate-200 bg-white">
      <CardHeader className="gap-3">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
          <div>
            <CardTitle className="flex items-center gap-2 text-lg font-bold text-slate-900">
              <Building2 className="size-5 text-blue-600" />
              기업 분석
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
              외부 정보 확인과 AI 응답 상태에 따라 분석 완료까지 시간이 걸릴 수 있습니다.
            </p>
          </div>
          <div className="flex flex-wrap gap-2 sm:justify-end">
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
            <Button
              type="button"
              size="sm"
              className="bg-blue-600 text-white hover:bg-blue-700"
              disabled={loading || generating || reviewSaving}
              onClick={() => void handleGenerate()}
            >
              {generating ? <Loader2 className="size-4 animate-spin" /> : <PlayCircle className="size-4" />}
              {analysis ? "AI 재분석" : "AI 분석 실행"}
            </Button>
          </div>
        </div>
      </CardHeader>
      <CardContent className="space-y-4">
        {!loading && (
          <AnalysisFailureNotice
            failures={failures}
            featureType="COMPANY_RESEARCH"
            onRetry={() => void handleGenerate()}
            retrying={generating}
            retryLabel="기업 분석 다시 시도"
          />
        )}

        {isStale && (
          <div className="flex flex-col gap-3 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800 sm:flex-row sm:items-center sm:justify-between">
            <span>
              현재 분석은 공고 rev {analysis?.jobPostingRevision ?? "-"} 기준입니다. 최신 공고 rev {latestJobPostingRevision} 기준으로 다시 분석할 수 있습니다.
            </span>
            <Button
              type="button"
              size="sm"
              variant="outline"
              className="border-amber-300 bg-white text-amber-800 hover:bg-amber-100"
              disabled={loading || generating || reviewSaving}
              onClick={() => void handleGenerate()}
            >
              {generating ? <Loader2 className="size-4 animate-spin" /> : <PlayCircle className="size-4" />}
              최신 공고로 재분석
            </Button>
          </div>
        )}

        {loading ? (
          <div className="h-64 animate-pulse rounded-lg bg-slate-100" />
        ) : analysis ? (
          <>
            <div className="grid gap-3 md:grid-cols-[1fr_220px]">
              <div className="rounded-lg border border-blue-100 bg-blue-50 p-4">
                <div className="text-sm font-semibold text-blue-950">기업 요약</div>
                <p className="mt-2 whitespace-pre-line text-sm leading-6 text-blue-900">{analysis.companySummary ?? "내용 없음"}</p>
              </div>
              <div className="rounded-lg border border-slate-200 bg-slate-50 p-4">
                <div className="text-xs font-semibold text-slate-500">산업</div>
                <div className="mt-2 text-sm font-bold text-slate-900">{analysis.industry ?? "미정"}</div>
              </div>
            </div>

            {sourceMetadata.length > 0 && (
              <div className="grid gap-3 md:grid-cols-3">
                {sourceMetadata.map((item) => (
                  <div key={item.label} className="rounded-lg border border-slate-200 bg-slate-50 p-3">
                    <div className="text-xs font-semibold text-slate-500">{item.label}</div>
                    <div className="mt-1 text-sm font-bold text-slate-900">{item.value}</div>
                  </div>
                ))}
              </div>
            )}

            <div className="grid gap-3 md:grid-cols-2">
              <AnalysisStructuredText title="검증된 사실" value={analysis.verifiedFacts} />
              <AnalysisStructuredText title="AI 추론" value={analysis.aiInferences} />
            </div>

            <div className="rounded-lg border border-slate-200 p-4">
              <div className="text-sm font-semibold text-slate-900">최근 이슈/준비 관점</div>
              <p className="mt-2 whitespace-pre-line text-sm leading-6 text-slate-600">{analysis.recentIssues ?? "내용 없음"}</p>
            </div>

            <div className="grid gap-3 md:grid-cols-2">
              <JsonList title="경쟁/비교 기업" value={analysis.competitors} />
              <JsonList title="참고 소스" value={analysis.sources} />
            </div>

            <div className="rounded-lg border border-slate-200 p-4">
              <div className="text-sm font-semibold text-slate-900">면접 준비 포인트</div>
              <p className="mt-2 whitespace-pre-line text-sm leading-6 text-slate-600">{analysis.interviewPoints ?? "내용 없음"}</p>
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
                <Input value={form.industry} onChange={(event) => setField("industry", event.target.value)} placeholder="산업" className="bg-white" />
                <Textarea value={form.companySummary} onChange={(event) => setField("companySummary", event.target.value)} className="min-h-24 bg-white" placeholder="기업 요약" />
                <Textarea value={form.recentIssues} onChange={(event) => setField("recentIssues", event.target.value)} className="min-h-24 bg-white" placeholder="최근 이슈" />
                <div className="grid gap-3 md:grid-cols-2">
                  <StructuredRowsEditor
                    title="검증된 사실"
                    rows={structuredRows.verifiedFacts}
                    fields={VERIFIED_FACT_EDITOR_FIELDS}
                    emptyRow={EMPTY_VERIFIED_FACT_ROW}
                    onChange={(rows) => setStructuredField("verifiedFacts", rows)}
                  />
                  <StructuredRowsEditor
                    title="AI 추론"
                    rows={structuredRows.aiInferences}
                    fields={AI_INFERENCE_EDITOR_FIELDS}
                    emptyRow={EMPTY_AI_INFERENCE_ROW}
                    onChange={(rows) => setStructuredField("aiInferences", rows)}
                  />
                </div>
                <div className="grid gap-3 md:grid-cols-2">
                  <Textarea value={form.competitors} onChange={(event) => setField("competitors", event.target.value)} className="min-h-24 bg-white" placeholder="경쟁/비교 기업을 한 줄에 하나씩 입력" />
                  <Textarea value={form.sources} onChange={(event) => setField("sources", event.target.value)} className="min-h-24 bg-white" placeholder="참고 소스를 한 줄에 하나씩 입력" />
                </div>
                <Textarea value={form.interviewPoints} onChange={(event) => setField("interviewPoints", event.target.value)} className="min-h-24 bg-white" placeholder="면접 준비 포인트" />
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
                <Button type="button" className="bg-slate-900 text-white hover:bg-slate-800" disabled={generating || reviewSaving} onClick={() => void handleReview()}>
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
              공고문을 저장한 뒤 AI 분석을 실행하면 기업 요약, 산업, 이슈, 면접 준비 포인트가 저장됩니다.
            </p>
          </div>
        )}

        {error && (
          <div className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
            {error}
          </div>
        )}

        {history.length > 0 && (
          <div className="rounded-lg border border-slate-200 bg-white">
            <div className="border-b border-slate-100 px-3 py-2 text-xs font-semibold text-slate-500">분석 이력</div>
            <div className="divide-y divide-slate-100">
              {history.map((item, index) => (
                <div key={item.id} className="flex flex-wrap items-center gap-3 px-3 py-2 text-xs text-slate-600">
                  <span className="font-semibold text-slate-900">{getHistoryLabel(index)}</span>
                  <span>공고 rev {item.jobPostingRevision ?? "-"}</span>
                  <span>{formatKoreaDateTime(item.createdAt)}</span>
                  <span>{item.confirmedAt ? "확정" : "미확정"}</span>
                  <span className="max-w-md truncate text-slate-400">{item.companySummary ?? "요약 없음"}</span>
                </div>
              ))}
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
