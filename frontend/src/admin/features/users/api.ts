import { api, ApiError } from "@/app/lib/api";
import {
  adminListQueryString,
  type AdminListParams,
  type BulkActionResult,
  type PageResult,
} from "@/admin/components/grid";
import type {
  AdminUserDetail,
  AdminUserCreateRequest,
  AdminUserLoginHistoryRow,
  AdminUserRow,
  AdminUserStatus,
} from "./types";

export function createAdminUser(payload: AdminUserCreateRequest): Promise<AdminUserRow> {
  return api<AdminUserRow>("/admin/users", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

/** 공통 그리드 계약 목록(서버 페이징/정렬/검색/CLIENT 전량 모드). */
export async function getAdminUsersPage(params: AdminListParams): Promise<PageResult<AdminUserRow>> {
  try {
    return await api<PageResult<AdminUserRow>>(`/admin/users/page?${adminListQueryString(params).toString()}`, {
      method: "GET",
    });
  } catch (error) {
    // 데모/목 모드에는 그리드 전용 mock 이 없어 기존 목록 mock 으로 동일 계약을 재현한다.
    if (error instanceof ApiError && error.code === "DEMO_UNAVAILABLE") {
      return demoUsersPage(params);
    }
    throw error;
  }
}

/** 회원 상태 일괄 변경 — 본인 계정 포함 시 서버가 거부한다. */
export async function bulkUpdateAdminUserStatus(
  ids: number[],
  params: { status: AdminUserStatus; reason?: string; blockedUntil?: string | null },
): Promise<BulkActionResult> {
  try {
    return await api<BulkActionResult>(`/admin/users/bulk/status`, {
      method: "POST",
      body: JSON.stringify({
        ids,
        params: {
          status: params.status,
          reason: params.reason ?? "",
          blockedUntil: params.blockedUntil ?? "",
        },
      }),
    });
  } catch (error) {
    // 데모/목 모드: 단건 상태 변경 mock 을 반복 호출해 일괄 계약을 재현한다.
    if (error instanceof ApiError && error.code === "DEMO_UNAVAILABLE") {
      let updated = 0;
      for (const id of ids) {
        try {
          await updateAdminUserStatus(id, {
            status: params.status,
            reason: params.reason,
            blockedUntil: params.blockedUntil ?? null,
          });
          updated += 1;
        } catch {
          // 데모 데이터에 없는 id 는 건너뛴다.
        }
      }
      return { requested: ids.length, updated, skipped: ids.length - updated };
    }
    throw error;
  }
}

/** 데모/목 모드 전용 — 목록 mock(/admin/users)을 받아 그리드 계약(검색/정렬/페이징)을 로컬로 재현한다. */
async function demoUsersPage(params: AdminListParams): Promise<PageResult<AdminUserRow>> {
  const all = await getAdminUsers({
    status: params.filters?.status,
    role: params.filters?.role,
    limit: 200,
  });
  const keyword = (params.keyword ?? "").trim().toLowerCase();
  let rows = all;
  if (keyword) {
    rows = rows.filter((row) => {
      if (params.searchType === "email") return row.email.toLowerCase().includes(keyword);
      if (params.searchType === "name") return row.name.toLowerCase().includes(keyword);
      return row.email.toLowerCase().includes(keyword) || row.name.toLowerCase().includes(keyword);
    });
  }
  if (params.dateFrom) rows = rows.filter((row) => row.createdAt.slice(0, 10) >= params.dateFrom!);
  if (params.dateTo) rows = rows.filter((row) => row.createdAt.slice(0, 10) <= params.dateTo!);

  const sortBy = params.sortBy ?? "createdAt";
  const factor = params.sortDir === "ASC" ? 1 : -1;
  rows = [...rows].sort((a, b) => {
    const left = (a as unknown as Record<string, unknown>)[sortBy];
    const right = (b as unknown as Record<string, unknown>)[sortBy];
    if (typeof left === "number" && typeof right === "number") return factor * (left - right);
    return factor * String(left ?? "").localeCompare(String(right ?? ""), "ko", { numeric: true });
  });

  const size = params.mode === "CLIENT" ? rows.length || 1 : params.size ?? 20;
  const page = params.mode === "CLIENT" ? 1 : Math.max(params.page ?? 1, 1);
  const totalPages = Math.max(1, Math.ceil(rows.length / size));
  const safePage = Math.min(page, totalPages);
  const start = (safePage - 1) * size;
  return {
    items: rows.slice(start, start + size),
    total: rows.length,
    page: safePage,
    size,
    totalPages,
  };
}

export function getAdminUsers(params: {
  keyword?: string;
  status?: string;
  role?: string;
  limit?: number;
} = {}): Promise<AdminUserRow[]> {
  const search = new URLSearchParams();
  if (params.keyword) search.set("keyword", params.keyword);
  if (params.status) search.set("status", params.status);
  if (params.role) search.set("role", params.role);
  search.set("limit", String(params.limit ?? 50));
  return api<AdminUserRow[]>(`/admin/users?${search.toString()}`, { method: "GET" });
}

export function getAdminUserDetail(id: number): Promise<AdminUserDetail> {
  return api<AdminUserDetail>(`/admin/users/${id}`, { method: "GET" });
}

/** 전체 로그인 이력(상세에 포함된 요약보다 더 많은 건수)을 별도로 조회한다. */
export function getAdminUserLoginHistory(id: number, limit = 100): Promise<AdminUserLoginHistoryRow[]> {
  return api<AdminUserLoginHistoryRow[]>(`/admin/users/${id}/login-history?limit=${limit}`, { method: "GET" });
}

export function updateAdminUserStatus(
  id: number,
  payload: { status: AdminUserStatus; reason?: string; memo?: string; blockedUntil?: string | null },
): Promise<AdminUserRow> {
  return api<AdminUserRow>(`/admin/users/${id}/status`, {
    method: "PATCH",
    body: JSON.stringify(payload),
  });
}

export function softDeleteAdminUser(id: number, reason?: string): Promise<AdminUserRow> {
  const search = new URLSearchParams();
  if (reason?.trim()) search.set("reason", reason.trim());
  const query = search.size > 0 ? `?${search.toString()}` : "";
  return api<AdminUserRow>(`/admin/users/${id}${query}`, { method: "DELETE" });
}

export function bulkSoftDeleteAdminUsers(ids: number[], reason?: string): Promise<BulkActionResult> {
  return api<BulkActionResult>("/admin/users/bulk-delete", {
    method: "POST",
    body: JSON.stringify({ ids, params: { reason: reason?.trim() ?? "" } }),
  });
}
