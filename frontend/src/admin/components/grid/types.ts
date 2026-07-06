import type { ReactNode } from "react";

/** 관리자 공통 그리드 계약 타입 — 백엔드 AdminListRequest/PageResult 와 1:1. */

export type AdminSortDir = "ASC" | "DESC";

/** SERVER=서버 페이징, CLIENT=상한(10000) 내 전량 로드 후 로컬 정렬/페이징. */
export type AdminListMode = "SERVER" | "CLIENT";

export interface PageResult<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
  totalPages: number;
}

export interface AdminListParams {
  keyword?: string;
  searchType?: string;
  /** enum 필터 — 서버에는 filters[key]=value 로 전달된다. */
  filters?: Record<string, string>;
  dateFrom?: string;
  dateTo?: string;
  sortBy?: string;
  sortDir?: AdminSortDir;
  page?: number;
  size?: number;
  mode?: AdminListMode;
}

export interface BulkActionResult {
  requested: number;
  updated: number;
  skipped: number;
}

/** 그리드 컬럼 정의. sortKey 는 서버 정렬 화이트리스트 키(기본은 key 그대로). */
export interface AdminColumn<T> {
  key: string;
  label: string;
  sortable?: boolean;
  render?: (row: T) => ReactNode;
  /** CLIENT 모드 로컬 정렬용 값 접근자. 없으면 row[key] 를 사용한다. */
  clientSortValue?: (row: T) => string | number | boolean | null | undefined;
  headerClassName?: string;
  cellClassName?: string;
}

export interface AdminSearchTypeOption {
  value: string;
  label: string;
}

export interface AdminFilterOption {
  value: string;
  label: string;
}

export interface AdminFilterDef {
  key: string;
  label: string;
  options: AdminFilterOption[];
}

export type AdminExportFormat = "csv" | "excel";
export type AdminExportScope = "all" | "search" | "selected" | "page";

/** AdminListParams → 백엔드 쿼리스트링 직렬화(필터는 filters[key] 형태). */
export function adminListQueryString(params: AdminListParams): URLSearchParams {
  const sp = new URLSearchParams();
  if (params.keyword) sp.set("keyword", params.keyword);
  if (params.searchType) sp.set("searchType", params.searchType);
  for (const [key, value] of Object.entries(params.filters ?? {})) {
    if (value) sp.set(`filters[${key}]`, value);
  }
  if (params.dateFrom) sp.set("dateFrom", params.dateFrom);
  if (params.dateTo) sp.set("dateTo", params.dateTo);
  if (params.sortBy) sp.set("sortBy", params.sortBy);
  if (params.sortDir) sp.set("sortDir", params.sortDir);
  if (params.page) sp.set("page", String(params.page));
  if (params.size) sp.set("size", String(params.size));
  if (params.mode) sp.set("mode", params.mode);
  return sp;
}
