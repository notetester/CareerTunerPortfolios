import { api } from "@/app/lib/api";
import type {
  AdminCompanyAnalysisMetadataUpdateRequest,
  AdminCompanyAnalysisQueryParams,
  AdminCompanyAnalysisRow,
  AdminCompanyAnalysisSummaryResponse,
} from "./types";

type QueryParamValue = string | number | boolean | null | undefined;

function appendQueryParam(search: URLSearchParams, key: string, value: QueryParamValue): void {
  if (value === undefined || value === null) return;
  if (typeof value === "string") {
    const trimmed = value.trim();
    if (!trimmed) return;
    search.set(key, trimmed);
    return;
  }
  if (typeof value === "number" && !Number.isFinite(value)) return;
  search.set(key, String(value));
}

function buildQueryString(entries: Array<[string, QueryParamValue]>): string {
  const search = new URLSearchParams();
  for (const [key, value] of entries) {
    appendQueryParam(search, key, value);
  }
  const query = search.toString();
  return query ? `?${query}` : "";
}

export function getAdminCompanyAnalyses(limit?: number | null): Promise<AdminCompanyAnalysisRow[]>;
export function getAdminCompanyAnalyses(
  params?: AdminCompanyAnalysisQueryParams,
): Promise<AdminCompanyAnalysisRow[]>;
export function getAdminCompanyAnalyses(
  paramsOrLimit?: AdminCompanyAnalysisQueryParams | number | null,
): Promise<AdminCompanyAnalysisRow[]> {
  const params: AdminCompanyAnalysisQueryParams =
    typeof paramsOrLimit === "number" || paramsOrLimit === null ? { limit: paramsOrLimit } : paramsOrLimit ?? {};
  const query = buildQueryString([
    ["keyword", params.keyword],
    ["sourceType", params.sourceType],
    ["industry", params.industry],
    ["confirmed", params.confirmed],
    ["hasMemo", params.hasMemo],
    ["checked", params.checked],
    ["refreshDue", params.refreshDue],
    ["applicationCaseId", params.applicationCaseId],
    ["userId", params.userId],
    ["createdFrom", params.createdFrom],
    ["createdTo", params.createdTo],
    ["sort", params.sort],
    ["limit", params.limit],
    ["offset", params.offset],
  ]);
  return api<AdminCompanyAnalysisRow[]>(`/admin/company-analysis${query}`, { method: "GET" });
}

export function getAdminCompanyAnalysisSummary(
  params: AdminCompanyAnalysisQueryParams = {},
): Promise<AdminCompanyAnalysisSummaryResponse> {
  const query = buildQueryString([
    ["keyword", params.keyword],
    ["sourceType", params.sourceType],
    ["industry", params.industry],
    ["confirmed", params.confirmed],
    ["hasMemo", params.hasMemo],
    ["checked", params.checked],
    ["refreshDue", params.refreshDue],
    ["applicationCaseId", params.applicationCaseId],
    ["userId", params.userId],
    ["createdFrom", params.createdFrom],
    ["createdTo", params.createdTo],
  ]);
  return api<AdminCompanyAnalysisSummaryResponse>(`/admin/company-analysis/summary${query}`, { method: "GET" });
}

export function updateAdminCompanyAnalysisMemo(analysisId: number, adminMemo: string): Promise<void> {
  return api<void>(`/admin/company-analysis/${analysisId}/memo`, {
    method: "PATCH",
    body: JSON.stringify({ adminMemo }),
  });
}

export function updateAdminCompanyAnalysisMetadata(
  analysisId: number,
  metadata: AdminCompanyAnalysisMetadataUpdateRequest,
): Promise<void> {
  return api<void>(`/admin/company-analysis/${analysisId}/metadata`, {
    method: "PATCH",
    body: JSON.stringify(metadata),
  });
}
