import { api } from "@/app/lib/api";
import type { AiModelChoice } from "@/app/components/ai/ModelPicker";
import type {
  CorrectionCreateRequest,
  CorrectionInterviewSource,
  CorrectionResponse,
  CorrectionType,
  CorrectionWarmupResponse,
} from "../types/correction";

export function warmupCorrectionModel() {
  return api<CorrectionWarmupResponse>("/corrections/warmup", {
    method: "POST",
  });
}

export function createCorrection(request: CorrectionCreateRequest, model: AiModelChoice = "AUTO") {
  // model 은 첨삭 provider 만 선택(AUTO=현행 폴백). 선택 실패 시 하위 tier 로 폴백.
  const query = model && model !== "AUTO" ? `?model=${model}` : "";
  return api<CorrectionResponse>(`/corrections${query}`, {
    method: "POST",
    body: JSON.stringify(request),
  });
}

export function listCorrections(params: {
  applicationCaseId?: number;
  correctionType?: CorrectionType;
  limit?: number;
} = {}) {
  const search = new URLSearchParams();
  if (params.applicationCaseId !== undefined) {
    search.set("applicationCaseId", String(params.applicationCaseId));
  }
  if (params.correctionType) {
    search.set("correctionType", params.correctionType);
  }
  if (params.limit !== undefined) {
    search.set("limit", String(params.limit));
  }
  const query = search.toString();
  return api<CorrectionResponse[]>(`/corrections${query ? `?${query}` : ""}`, { method: "GET" });
}

export function getCorrection(id: number) {
  return api<CorrectionResponse>(`/corrections/${id}`, { method: "GET" });
}

export function getInterviewAnswerCorrectionSource(answerId: number) {
  return api<CorrectionInterviewSource>(`/corrections/sources/interview-answers/${answerId}`, { method: "GET" });
}

export function deleteCorrection(id: number) {
  return api<void>(`/corrections/${id}`, { method: "DELETE" });
}
