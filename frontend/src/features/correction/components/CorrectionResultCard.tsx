import { useEffect, useRef, useState } from "react";
import { Check, ClipboardCopy, Database, FileCheck2, ListChecks, Lightbulb, ShieldAlert } from "lucide-react";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import type { CorrectionResponse } from "../types/correction";

interface CorrectionResultCardProps {
  result: CorrectionResponse;
}

export function CorrectionResultCard({ result }: CorrectionResultCardProps) {
  const [copied, setCopied] = useState(false);
  const [copyError, setCopyError] = useState(false);
  const copyResetTimer = useRef<number | null>(null);
  const provenance = parseSourceSnapshot(result.sourceSnapshot);

  useEffect(() => {
    setCopied(false);
    setCopyError(false);
    return () => {
      if (copyResetTimer.current !== null) window.clearTimeout(copyResetTimer.current);
    };
  }, [result.id]);

  const copyImprovedText = async () => {
    if (!result.improvedText) return;
    try {
      await navigator.clipboard.writeText(result.improvedText);
      setCopied(true);
      setCopyError(false);
      if (copyResetTimer.current !== null) window.clearTimeout(copyResetTimer.current);
      copyResetTimer.current = window.setTimeout(() => setCopied(false), 1800);
    } catch {
      setCopyError(true);
    }
  };

  return (
    <Card className="border border-slate-200 bg-card">
      <CardHeader className="gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <CardTitle className="flex items-center gap-2 text-lg">
            <FileCheck2 className="size-5 text-emerald-600" />
            첨삭 결과
          </CardTitle>
          <p className="mt-1 text-xs text-slate-500">{formatDate(result.createdAt)}</p>
        </div>
        <div className="flex items-center gap-2">
          <Badge className="bg-emerald-100 text-emerald-700">{result.status}</Badge>
          <Button type="button" size="sm" variant="outline" onClick={copyImprovedText} disabled={!result.improvedText}>
            {copied ? <Check className="size-4 text-emerald-600" /> : <ClipboardCopy className="size-4" />}
            {copied ? "복사됨" : "개선문 복사"}
          </Button>
        </div>
      </CardHeader>
      <CardContent className="space-y-5">
        {copyError && <p className="text-sm text-red-600">클립보드에 복사하지 못했습니다.</p>}

        <div className="grid gap-4 xl:grid-cols-2">
          <TextPanel title="원문" text={result.originalText} tone="bg-slate-50" />
          <TextPanel title="개선문" text={result.improvedText || "개선문이 생성되지 않았습니다."} tone="bg-emerald-50/60" />
        </div>

        {provenance && (
          <section className="rounded-lg border border-violet-200 bg-violet-50/60 p-4">
            <h3 className="flex items-center gap-2 text-sm font-bold text-violet-900">
              <Database className="size-4" /> 입력 출처
            </h3>
            <p className="mt-2 text-sm text-violet-800">
              {provenance.fitAnalysisId ? `적합도 분석 #${provenance.fitAnalysisId}의 부족 역량·전략 반영` : "적합도 분석 없이 프로필·공고 맥락으로 생성"}
              {provenance.answerId ? ` · 면접 답변 #${provenance.answerId}` : ""}
            </p>
            {provenance.missingSkills.length > 0 && (
              <p className="mt-1 text-xs text-violet-700">부족 역량: {provenance.missingSkills.join(", ")}</p>
            )}
          </section>
        )}

        {result.summary && (
          <section className="rounded-lg border border-blue-100 bg-blue-50/60 p-4">
            <h3 className="flex items-center gap-2 text-sm font-bold text-slate-900">
              <ListChecks className="size-4 text-blue-600" />
              첨삭 요약
            </h3>
            <p className="mt-2 whitespace-pre-wrap break-words text-sm leading-6 text-slate-700">{result.summary}</p>
          </section>
        )}

        <div className="grid gap-4 md:grid-cols-3">
          <ResultList title="확인할 점" items={result.issues} icon={ShieldAlert} iconClass="text-rose-600" />
          <ResultList title="변경 이유" items={result.changeReasons} icon={ListChecks} iconClass="text-indigo-600" />
          <ResultList title="추천 표현" items={result.suggestions} icon={Lightbulb} iconClass="text-amber-600" />
        </div>
      </CardContent>
    </Card>
  );
}

function TextPanel({ title, text, tone }: { title: string; text: string; tone: string }) {
  return (
    <section className={`min-w-0 rounded-lg border border-slate-200 p-4 ${tone}`}>
      <h3 className="text-xs font-bold text-slate-500">{title}</h3>
      <p className="mt-2 whitespace-pre-wrap break-words text-sm leading-7 text-slate-800">{text}</p>
    </section>
  );
}

function ResultList({
  title,
  items,
  icon: Icon,
  iconClass,
}: {
  title: string;
  items: string[];
  icon: typeof ShieldAlert;
  iconClass: string;
}) {
  return (
    <section className="rounded-lg border border-slate-200 bg-card p-4">
      <h3 className="flex items-center gap-2 text-sm font-bold text-slate-900">
        <Icon className={`size-4 ${iconClass}`} />
        {title}
      </h3>
      {items.length > 0 ? (
        <ul className="mt-3 space-y-2">
          {items.map((item, index) => (
            <li key={`${item}-${index}`} className="flex gap-2 text-sm leading-5 text-slate-600">
              <span className="mt-2 size-1.5 shrink-0 rounded-full bg-slate-400" />
              <span className="min-w-0 break-words">{item}</span>
            </li>
          ))}
        </ul>
      ) : (
        <p className="mt-3 text-sm text-slate-400">별도 항목이 없습니다.</p>
      )}
    </section>
  );
}

function formatDate(value: string | null) {
  if (!value) return "생성 시각 없음";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "생성 시각 없음" : date.toLocaleString("ko-KR");
}

function parseSourceSnapshot(raw: string | null | undefined) {
  if (!raw) return null;
  try {
    const value = JSON.parse(raw) as Record<string, unknown>;
    const fit = value.fitAnalysis && typeof value.fitAnalysis === "object"
      ? value.fitAnalysis as Record<string, unknown>
      : null;
    const interview = value.interviewAnswer && typeof value.interviewAnswer === "object"
      ? value.interviewAnswer as Record<string, unknown>
      : null;
    return {
      fitAnalysisId: typeof fit?.fitAnalysisId === "number" ? fit.fitAnalysisId : null,
      answerId: typeof interview?.answerId === "number" ? interview.answerId : null,
      missingSkills: Array.isArray(fit?.missingSkills)
        ? fit.missingSkills.filter((item): item is string => typeof item === "string")
        : [],
    };
  } catch {
    return null;
  }
}
