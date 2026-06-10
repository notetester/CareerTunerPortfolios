import { AlertTriangle, Sparkles } from "lucide-react";

interface AiResultBadgeProps {
  /** career_analysis_run / fit_analysis 의 status: SUCCESS | FALLBACK | FAILED */
  status?: string | null;
}

/**
 * AI 결과 표기 구분(디자인 분석 §4.3): AI가 생성한 결과에는 "AI 제안"을 표시하고,
 * 실 AI 호출이 실패해 대체(mock/fallback) 결과가 내려온 경우 "확인 필요"를 함께 표시한다.
 * 공고 원문·사용자 입력에서 직접 확인된 값(확인됨)은 각 화면이 원천 데이터 옆에 별도로 표기한다.
 */
export function AiResultBadge({ status }: AiResultBadgeProps) {
  const needsReview = status === "FALLBACK" || status === "FAILED";
  return (
    <span className="inline-flex items-center gap-1 align-middle">
      <span
        className="inline-flex items-center gap-1 rounded-full border border-blue-200 bg-blue-50 px-2 py-0.5 text-[11px] font-semibold text-blue-700"
        title="입력 데이터를 바탕으로 AI가 생성한 추천입니다. 최종 판단 전에 근거를 확인하세요."
      >
        <Sparkles className="size-3" />
        AI 제안
      </span>
      {needsReview && (
        <span
          className="inline-flex items-center gap-1 rounded-full border border-amber-200 bg-amber-50 px-2 py-0.5 text-[11px] font-semibold text-amber-700"
          title="실제 AI 호출이 실패해 대체 결과가 표시되고 있습니다. 재생성 후 다시 확인하세요."
        >
          <AlertTriangle className="size-3" />
          확인 필요
        </span>
      )}
    </span>
  );
}
