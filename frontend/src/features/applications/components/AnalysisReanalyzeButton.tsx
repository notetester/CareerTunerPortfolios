import type { ApplicationSourceType } from "../types/applicationCase";
import { getModelOptions } from "../api/modelOptionsApi";
import { ProviderPickerButton } from "./ProviderPickerButton";

type AnalysisStage = "jobAnalysis" | "companyAnalysis";

interface AnalysisReanalyzeButtonProps {
  /** 어느 분석 단계의 provider 선택지를 쓸지. model-options 의 jobAnalysis / companyAnalysis. */
  stage: AnalysisStage;
  /** model-options 조회에 필요한 sourceType(분석 선택지는 sourceType 과 무관하나 API 계약상 전달). */
  sourceType: ApplicationSourceType;
  onReanalyze: (provider: string) => void;
  pending?: boolean;
  disabled?: boolean;
  label?: string;
  size?: "sm" | "default";
  variant?: "default" | "outline" | "ghost" | "destructive";
  className?: string;
}

/**
 * 공고·기업 분석을 사용자가 고른 provider 로 strict 재분석한다(단일 provider, 교차 폴백·self-rules 없음).
 * OCR 재추출과 달리 파일 공고 가드가 없다(분석은 sourceType 무관). 공통 {@link ProviderPickerButton} 래퍼.
 */
export function AnalysisReanalyzeButton({
  stage,
  sourceType,
  onReanalyze,
  pending = false,
  disabled = false,
  label = "AI 재분석",
  size = "sm",
  variant = "outline",
  className,
}: AnalysisReanalyzeButtonProps) {
  return (
    <ProviderPickerButton
      loadOptions={async () => {
        const opts = await getModelOptions(sourceType);
        return (stage === "jobAnalysis" ? opts.jobAnalysis : opts.companyAnalysis)?.options ?? [];
      }}
      onSelect={onReanalyze}
      label={label}
      menuLabel="재분석 모델 선택"
      emptyText="선택 가능한 분석 모델이 없습니다."
      pending={pending}
      disabled={disabled}
      size={size}
      variant={variant}
      className={className}
    />
  );
}
