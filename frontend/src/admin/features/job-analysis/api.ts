import { api } from "@/app/lib/api";
import type {
  AdminAiUsageLogRow,
  AdminBUsageLogQueryParams,
  AdminBUsageSummaryResponse,
  AdminJobAnalysisQueryParams,
  AdminJobAnalysisRow,
  AdminJobAnalysisSummaryResponse,
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

function normalizeLimitParams(
  paramsOrLimit: AdminJobAnalysisQueryParams | number | null | undefined,
): AdminJobAnalysisQueryParams {
  return typeof paramsOrLimit === "number" || paramsOrLimit === null
    ? { limit: paramsOrLimit }
    : paramsOrLimit ?? {};
}

export function getAdminJobAnalyses(limit?: number | null): Promise<AdminJobAnalysisRow[]>;
export function getAdminJobAnalyses(params?: AdminJobAnalysisQueryParams): Promise<AdminJobAnalysisRow[]>;
export function getAdminJobAnalyses(
  paramsOrLimit?: AdminJobAnalysisQueryParams | number | null,
): Promise<AdminJobAnalysisRow[]> {
  const params = normalizeLimitParams(paramsOrLimit);
  const query = buildQueryString([
    ["keyword", params.keyword],
    ["difficulty", params.difficulty],
    ["confirmed", params.confirmed],
    ["hasMemo", params.hasMemo],
    ["applicationCaseId", params.applicationCaseId],
    ["userId", params.userId],
    ["createdFrom", params.createdFrom],
    ["createdTo", params.createdTo],
    ["sort", params.sort],
    ["limit", params.limit],
    ["offset", params.offset],
  ]);
  return api<AdminJobAnalysisRow[]>(`/admin/job-analysis${query}`, { method: "GET" });
}

export function getAdminJobAnalysisSummary(
  params: AdminJobAnalysisQueryParams = {},
): Promise<AdminJobAnalysisSummaryResponse> {
  const query = buildQueryString([
    ["keyword", params.keyword],
    ["difficulty", params.difficulty],
    ["confirmed", params.confirmed],
    ["hasMemo", params.hasMemo],
    ["applicationCaseId", params.applicationCaseId],
    ["userId", params.userId],
    ["createdFrom", params.createdFrom],
    ["createdTo", params.createdTo],
  ]);
  return api<AdminJobAnalysisSummaryResponse>(`/admin/job-analysis/summary${query}`, { method: "GET" });
}

export function getAdminBUsageLogs(limit?: number | null): Promise<AdminAiUsageLogRow[]>;
export function getAdminBUsageLogs(params?: AdminBUsageLogQueryParams): Promise<AdminAiUsageLogRow[]>;
export function getAdminBUsageLogs(
  paramsOrLimit?: AdminBUsageLogQueryParams | number | null,
): Promise<AdminAiUsageLogRow[]> {
  const params: AdminBUsageLogQueryParams =
    typeof paramsOrLimit === "number" || paramsOrLimit === null ? { limit: paramsOrLimit } : paramsOrLimit ?? {};
  const query = buildQueryString([
    ["featureType", params.featureType],
    ["status", params.status],
    ["keyword", params.keyword],
    ["applicationCaseId", params.applicationCaseId],
    ["userId", params.userId],
    ["model", params.model],
    ["createdFrom", params.createdFrom],
    ["createdTo", params.createdTo],
    ["sort", params.sort],
    ["limit", params.limit],
    ["offset", params.offset],
  ]);
  return api<AdminAiUsageLogRow[]>(`/admin/ai-usage/b${query}`, { method: "GET" });
}

export function getFilteredAdminBUsageLogs(params: AdminBUsageLogQueryParams = {}): Promise<AdminAiUsageLogRow[]> {
  return getAdminBUsageLogs(params);
}

export function getAdminBUsageSummary(
  params: AdminBUsageLogQueryParams = {},
): Promise<AdminBUsageSummaryResponse> {
  const query = buildQueryString([
    ["featureType", params.featureType],
    ["status", params.status],
    ["keyword", params.keyword],
    ["applicationCaseId", params.applicationCaseId],
    ["userId", params.userId],
    ["model", params.model],
    ["createdFrom", params.createdFrom],
    ["createdTo", params.createdTo],
    ["sort", params.sort],
    ["limit", params.limit],
    ["offset", params.offset],
  ]);
  return api<AdminBUsageSummaryResponse>(`/admin/ai-usage/b/summary${query}`, { method: "GET" });
}

export function updateAdminJobAnalysisMemo(analysisId: number, adminMemo: string): Promise<void> {
  return api<void>(`/admin/job-analysis/${analysisId}/memo`, {
    method: "PATCH",
    body: JSON.stringify({ adminMemo }),
  });
}
