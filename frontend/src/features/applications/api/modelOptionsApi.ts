import { api } from "@/app/lib/api";
import type { ApplicationSourceType } from "../types/applicationCase";

/**
 * 지원 건 단계별(OCR/공고분석/기업분석) 모델 선택지. 백엔드 ModelOptionsResponse 그대로.
 * 값(selectable·reason·displayName·actualModel)은 UI 에서 추측하지 말고 응답을 그대로 사용한다.
 */
export interface ProviderOption {
  provider: string;
  displayName: string;
  selectable: boolean;
  reason: string | null;
  actualModel: string | null;
  autoFallbackIncluded: boolean | null;
}

export interface StageOptions {
  recommendedDefault: string | null;
  options: ProviderOption[];
}

export interface ModelOptions {
  ocr: StageOptions | null;
  jobAnalysis: StageOptions | null;
  companyAnalysis: StageOptions | null;
}

/**
 * 재추출 OCR 모델 선택지 조회. sourceType 은 PDF|IMAGE 만 OCR 선택지를 준다(그 외는 ocr=null).
 */
export function getModelOptions(sourceType: ApplicationSourceType): Promise<ModelOptions> {
  const params = new URLSearchParams({ sourceType });
  return api<ModelOptions>(`/application-cases/model-options?${params}`, { method: "GET" });
}
