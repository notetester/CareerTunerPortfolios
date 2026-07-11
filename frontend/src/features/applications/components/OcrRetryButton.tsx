import type { ApplicationSourceType } from "../types/applicationCase";
import { getModelOptions } from "../api/modelOptionsApi";
import { ProviderPickerButton } from "./ProviderPickerButton";

/** OCR 재추출은 파일 공고(PDF/IMAGE)에만 적용된다 — URL/TEXT/MANUAL 은 버튼 자체를 노출하지 않는다. */
export function isOcrRetrySource(sourceType: ApplicationSourceType): boolean {
  return sourceType === "PDF" || sourceType === "IMAGE";
}

interface OcrRetryButtonProps {
  /** 최신 추출의 sourceType(지원 건의 일반 sourceType 이 아니라 실제 추출 기준). */
  sourceType: ApplicationSourceType;
  retrying?: boolean;
  disabled?: boolean;
  onRetry: (ocrProvider: string) => void;
  label?: string;
  size?: "sm" | "default";
  variant?: "default" | "outline" | "ghost" | "destructive";
  className?: string;
}

/**
 * 실패한 공고 추출을 사용자가 고른 OCR 모델로 재추출한다(strict: 단일 provider, 교차 폴백 없음).
 * 공통 {@link ProviderPickerButton} 에 OCR 단계 선택지(model-options.ocr)와 파일 공고 가드만 얹은 얇은 래퍼다.
 */
export function OcrRetryButton({
  sourceType,
  retrying = false,
  disabled = false,
  onRetry,
  label = "다시 추출",
  size = "sm",
  variant = "outline",
  className,
}: OcrRetryButtonProps) {
  if (!isOcrRetrySource(sourceType)) {
    return null;
  }

  return (
    <ProviderPickerButton
      loadOptions={async () => (await getModelOptions(sourceType)).ocr?.options ?? []}
      onSelect={onRetry}
      label={label}
      menuLabel="재추출 OCR 모델 선택"
      emptyText="선택 가능한 OCR 모델이 없습니다."
      pending={retrying}
      disabled={disabled}
      size={size}
      variant={variant}
      className={className}
    />
  );
}
