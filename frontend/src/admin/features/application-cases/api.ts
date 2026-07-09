import { api } from "@/app/lib/api";
import type {
  AdminApplicationCaseDetail,
  AdminApplicationCaseQueryParams,
  AdminApplicationCaseRow,
  AdminApplicationCaseSummaryResponse,
} from "./types";
import type { ApplicationStatus } from "@/features/applications/types/applicationCase";

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

export function getAdminApplicationCases(
  params: AdminApplicationCaseQueryParams = {},
): Promise<AdminApplicationCaseRow[]> {
  const query = buildQueryString([
    ["keyword", params.keyword],
    ["status", params.status],
    ["includeArchived", params.includeArchived],
    ["includeDeleted", params.includeDeleted],
    ["sourceType", params.sourceType],
    ["favorite", params.favorite],
    ["createdFrom", params.createdFrom],
    ["createdTo", params.createdTo],
    ["deadlineFrom", params.deadlineFrom],
    ["deadlineTo", params.deadlineTo],
    ["analysisState", params.analysisState],
    ["sort", params.sort],
    ["limit", params.limit],
    ["offset", params.offset],
  ]);
  return api<AdminApplicationCaseRow[]>(`/admin/application-cases${query}`, { method: "GET" });
}

export function getAdminApplicationCaseSummary(
  params: AdminApplicationCaseQueryParams = {},
): Promise<AdminApplicationCaseSummaryResponse> {
  const query = buildQueryString([
    ["keyword", params.keyword],
    ["status", params.status],
    ["includeArchived", params.includeArchived],
    ["includeDeleted", params.includeDeleted],
    ["sourceType", params.sourceType],
    ["favorite", params.favorite],
    ["createdFrom", params.createdFrom],
    ["createdTo", params.createdTo],
    ["deadlineFrom", params.deadlineFrom],
    ["deadlineTo", params.deadlineTo],
    ["analysisState", params.analysisState],
  ]);
  return api<AdminApplicationCaseSummaryResponse>(`/admin/application-cases/summary${query}`, { method: "GET" });
}

export function getAdminApplicationCaseDetail(id: number): Promise<AdminApplicationCaseDetail> {
  return api<AdminApplicationCaseDetail>(`/admin/application-cases/${id}`, { method: "GET" });
}

export function updateAdminApplicationCaseStatus(
  id: number,
  status: ApplicationStatus,
  memo: string,
): Promise<AdminApplicationCaseRow> {
  return api<AdminApplicationCaseRow>(`/admin/application-cases/${id}/status`, {
    method: "PATCH",
    body: JSON.stringify({ status, memo }),
  });
}
