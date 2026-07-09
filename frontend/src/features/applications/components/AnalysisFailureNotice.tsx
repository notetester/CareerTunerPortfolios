import { AlertCircle, Loader2, RefreshCw } from "lucide-react";
import type { BAnalysisFailureLog } from "../types/analysis";
import { formatKoreaDateTime } from "../utils/dateFormat";

interface AnalysisFailureNoticeProps {
  failures: BAnalysisFailureLog[];
  featureType: string;
  onRetry?: () => void;
  retrying?: boolean;
  retryLabel?: string;
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

function getFallbackMessage(featureType: string): string {
  if (featureType === "COMPANY_RESEARCH") {
    return "기업 분석 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.";
  }
  if (featureType === "JOB_ANALYSIS") {
    return "공고 분석 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.";
  }
  return "AI 분석 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.";
}

function isTechnicalMessage(message: string): boolean {
  const lower = message.toLowerCase();
  return (
    lower.includes("### error") ||
    lower.includes("sql:") ||
    lower.includes("com.mysql") ||
    lower.includes("org.springframework") ||
    lower.includes("statement cancelled") ||
    lower.includes("timeoutexception")
  );
}

function displayMessage(failure: BAnalysisFailureLog, featureType: string): string {
  const message = failure.errorMessage?.trim();
  if (!message) return getFallbackMessage(featureType);
  if (message.length > 300 || isTechnicalMessage(message)) {
    return getFallbackMessage(featureType);
  }
  return message;
}

export function AnalysisFailureNotice({
  failures,
  featureType,
  onRetry,
  retrying = false,
  retryLabel = "다시 시도",
}: AnalysisFailureNoticeProps) {
  const failure = findVisibleFailure(failures, featureType);
  if (!failure) return null;

  return (
    <div className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-900">
      <div className="flex flex-wrap items-center gap-2 font-semibold">
        <AlertCircle className="size-4" />
        <span>최근 실패</span>
        <span className="text-xs font-medium text-amber-700">{formatKoreaDateTime(failure.createdAt)}</span>
        {onRetry && (
          <button
            type="button"
            className="inline-flex items-center gap-1.5 rounded-md border border-amber-300 bg-card px-2 py-1 text-xs font-semibold text-amber-800 hover:bg-amber-100 disabled:cursor-not-allowed disabled:opacity-60"
            disabled={retrying}
            onClick={onRetry}
          >
            {retrying ? <Loader2 className="size-3 animate-spin" /> : <RefreshCw className="size-3" />}
            {retryLabel}
          </button>
        )}
        <span className="rounded-full bg-card px-2 py-0.5 text-xs text-amber-700">재시도 가능</span>
      </div>
      <p className="mt-1 whitespace-pre-line leading-6">{displayMessage(failure, featureType)}</p>
    </div>
  );
}
