import { AlertCircle } from "lucide-react";
import type { BAnalysisFailureLog } from "../types/analysis";

interface AnalysisFailureNoticeProps {
  failures: BAnalysisFailureLog[];
  featureType: string;
}

function formatDateTime(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium", timeStyle: "short" }).format(date);
}

function findVisibleFailure(
  failures: BAnalysisFailureLog[],
  featureType: string,
): BAnalysisFailureLog | null {
  const sorted = failures
    .filter((failure) => failure.featureType === featureType)
    .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
  return sorted[0] ?? null;
}

export function AnalysisFailureNotice({
  failures,
  featureType,
}: AnalysisFailureNoticeProps) {
  const failure = findVisibleFailure(failures, featureType);
  if (!failure) return null;

  return (
    <div className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-900">
      <div className="flex flex-wrap items-center gap-2 font-semibold">
        <AlertCircle className="size-4" />
        <span>최근 실패</span>
        <span className="text-xs font-medium text-amber-700">{formatDateTime(failure.createdAt)}</span>
        <span className="rounded-full bg-white px-2 py-0.5 text-xs text-amber-700">재시도 가능</span>
      </div>
      <p className="mt-1 whitespace-pre-line leading-6">{failure.errorMessage ?? "AI 분석 실패"}</p>
    </div>
  );
}
