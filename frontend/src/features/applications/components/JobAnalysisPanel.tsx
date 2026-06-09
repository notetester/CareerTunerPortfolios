import { useEffect, useState } from "react";
import { BarChart3, Loader2, PlayCircle } from "lucide-react";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Input } from "@/app/components/ui/input";
import { Textarea } from "@/app/components/ui/textarea";
import type { BAnalysisFailureLog, JobAnalysis, JobAnalysisReviewRequest } from "../types/analysis";
import {
  formatJsonArrayForTextarea,
  getDifficultyLabel,
  parseJsonStringArray,
  serializeTextareaList,
} from "../types/analysis";
import { AnalysisFailureNotice } from "./AnalysisFailureNotice";
import { AnalysisStructuredText } from "./AnalysisStructuredText";

interface JobAnalysisPanelProps {
  analysis: JobAnalysis | null;
  history: JobAnalysis[];
  loading: boolean;
  generating: boolean;
  error: string | null;
  failures: BAnalysisFailureLog[];
  onGenerate(): Promise<JobAnalysis | null>;
  onReview(analysisId: number, request: JobAnalysisReviewRequest): Promise<JobAnalysis | null>;
}

function formatDateTime(value: string): string {
  return new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

function SkillList({ title, value }: { title: string; value: string | null }) {
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

export function JobAnalysisPanel({
  analysis,
  history,
  loading,
  generating,
  error,
  failures,
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
  }, [analysis]);

  const setField = (key: keyof typeof form, value: string) => {
    setForm((current) => ({ ...current, [key]: value }));
  };

  const handleReview = async () => {
    if (!analysis) return;
    await onReview(analysis.id, {
      ...form,
      requiredSkills: serializeTextareaList(form.requiredSkills),
      preferredSkills: serializeTextareaList(form.preferredSkills),
      confirmed: true,
    });
  };

  return (
    <Card className="border-slate-200 bg-white">
      <CardHeader className="gap-3">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
          <div>
            <CardTitle className="flex items-center gap-2 text-lg font-bold text-slate-900">
              <BarChart3 className="size-5 text-blue-600" />
              공고 분석
            </CardTitle>
            {analysis ? (
              <p className="mt-1 text-xs text-slate-500">
                최근 분석: {formatDateTime(analysis.createdAt)}
                {analysis.jobPostingRevision ? ` · 공고 rev ${analysis.jobPostingRevision}` : ""}
                {analysis.confirmedAt ? ` · 확정 ${formatDateTime(analysis.confirmedAt)}` : ""}
              </p>
            ) : (
              <p className="mt-1 text-xs text-slate-500">분석 결과 없음</p>
            )}
          </div>
          <Button
            type="button"
            size="sm"
            className="bg-blue-600 text-white hover:bg-blue-700"
            disabled={loading || generating}
            onClick={() => void onGenerate()}
          >
            {generating ? <Loader2 className="size-4 animate-spin" /> : <PlayCircle className="size-4" />}
            {analysis ? "AI 재분석" : "AI 분석 실행"}
          </Button>
        </div>
      </CardHeader>
      <CardContent className="space-y-4">
        {!loading && (
          <AnalysisFailureNotice
            failures={failures}
            featureType="JOB_ANALYSIS"
          />
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

            <div className="space-y-3 rounded-lg border border-slate-200 bg-slate-50 p-4">
              <div className="text-sm font-semibold text-slate-900">사용자 확인/수정</div>
              <div className="grid gap-3 md:grid-cols-3">
                <Input value={form.employmentType} onChange={(event) => setField("employmentType", event.target.value)} placeholder="고용 형태" />
                <Input value={form.experienceLevel} onChange={(event) => setField("experienceLevel", event.target.value)} placeholder="경력 수준" />
                <Input value={form.difficulty} onChange={(event) => setField("difficulty", event.target.value)} placeholder="EASY/NORMAL/HARD" />
              </div>
              <div className="grid gap-3 md:grid-cols-2">
                <Textarea value={form.requiredSkills} onChange={(event) => setField("requiredSkills", event.target.value)} className="min-h-24 bg-white" placeholder={"필수 역량을 한 줄에 하나씩 입력"} />
                <Textarea value={form.preferredSkills} onChange={(event) => setField("preferredSkills", event.target.value)} className="min-h-24 bg-white" placeholder={"우대 역량을 한 줄에 하나씩 입력"} />
              </div>
              <div className="grid gap-3 md:grid-cols-2">
                <Textarea value={form.duties} onChange={(event) => setField("duties", event.target.value)} className="min-h-28 bg-white" placeholder="주요 업무" />
                <Textarea value={form.qualifications} onChange={(event) => setField("qualifications", event.target.value)} className="min-h-28 bg-white" placeholder="자격 요건" />
              </div>
              <Textarea value={form.summary} onChange={(event) => setField("summary", event.target.value)} className="min-h-24 bg-white" placeholder="요약" />
              <Button type="button" className="bg-slate-900 text-white hover:bg-slate-800" disabled={generating} onClick={() => void handleReview()}>
                {generating && <Loader2 className="size-4 animate-spin" />}
                수정 내용 저장 및 확정
              </Button>
            </div>
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
          <div className="rounded-lg border border-slate-200 bg-white">
            <div className="border-b border-slate-100 px-3 py-2 text-xs font-semibold text-slate-500">분석 이력</div>
            <div className="divide-y divide-slate-100">
              {history.map((item) => (
                <div key={item.id} className="flex flex-wrap items-center gap-3 px-3 py-2 text-xs text-slate-600">
                  <span className="font-semibold text-slate-900">#{item.id}</span>
                  <span>공고 rev {item.jobPostingRevision ?? "-"}</span>
                  <span>{formatDateTime(item.createdAt)}</span>
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
