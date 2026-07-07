import { api, ApiError } from "@/app/lib/api";
import {
  adminListQueryString,
  type AdminListParams,
  type PageResult,
} from "@/admin/components/grid";
import { getAdminUserLoginHistory, getAdminUsers } from "@/admin/features/users/api";
import type { AdminLoginAuditRow } from "./types";

/** 로그인 감사 목록 — 공통 그리드 계약(조회 전용). */
export async function getAdminLoginAuditPage(params: AdminListParams): Promise<PageResult<AdminLoginAuditRow>> {
  try {
    return await api<PageResult<AdminLoginAuditRow>>(
      `/admin/audit/logins?${adminListQueryString(params).toString()}`,
      { method: "GET" },
    );
  } catch (error) {
    // 데모/목 모드에는 전역 로그인 감사 mock 이 없어 회원별 이력 mock 을 합쳐 재현한다.
    if (error instanceof ApiError && error.code === "DEMO_UNAVAILABLE") {
      return demoLoginAuditPage(params);
    }
    throw error;
  }
}

/** 데모/목 모드 전용 — 회원 목록 + 회원별 로그인 이력 mock 을 평탄화해 그리드 계약을 재현한다. */
async function demoLoginAuditPage(params: AdminListParams): Promise<PageResult<AdminLoginAuditRow>> {
  const users = await getAdminUsers({ limit: 50 });
  const histories = await Promise.all(
    users.map(async (user) => {
      try {
        const items = await getAdminUserLoginHistory(user.id, 100);
        return items.map<AdminLoginAuditRow>((item) => ({
          id: item.id,
          userId: item.userId,
          userEmail: user.email,
          userName: user.name,
          eventType: item.eventType,
          authProvider: item.authProvider,
          loginMethod: item.loginMethod,
          loginIdentifier: item.loginIdentifier,
          success: item.success,
          failReason: item.failReason,
          ipAddress: item.ipAddress,
          userAgent: item.userAgent,
          requestUri: item.requestUri,
          createdAt: item.createdAt,
        }));
      } catch {
        return [];
      }
    }),
  );

  let rows = histories.flat();

  const keyword = (params.keyword ?? "").trim().toLowerCase();
  if (keyword) {
    rows = rows.filter((row) => {
      const email = (row.userEmail ?? "").toLowerCase();
      const identifier = (row.loginIdentifier ?? "").toLowerCase();
      const ip = (row.ipAddress ?? "").toLowerCase();
      if (params.searchType === "email") return email.includes(keyword);
      if (params.searchType === "identifier") return identifier.includes(keyword);
      if (params.searchType === "ip") return ip.includes(keyword);
      return email.includes(keyword) || identifier.includes(keyword) || ip.includes(keyword);
    });
  }
  const eventType = params.filters?.eventType;
  if (eventType) rows = rows.filter((row) => row.eventType === eventType);
  const authProvider = params.filters?.authProvider;
  if (authProvider) rows = rows.filter((row) => row.authProvider === authProvider);
  const result = params.filters?.result;
  if (result === "SUCCESS") rows = rows.filter((row) => row.success);
  if (result === "FAIL") rows = rows.filter((row) => !row.success);
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
