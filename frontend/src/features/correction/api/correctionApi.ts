import { api } from "@/app/lib/api";
import type {
  CorrectionCreateRequest,
  CorrectionResponse,
  CorrectionType,
  CorrectionWarmupResponse,
} from "../types/correction";

export function warmupCorrectionModel() {
  return api<CorrectionWarmupResponse>("/corrections/warmup", {
    method: "POST",
  });
}

export function createCorrection(request: CorrectionCreateRequest) {
  return api<CorrectionResponse>("/corrections", {
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
