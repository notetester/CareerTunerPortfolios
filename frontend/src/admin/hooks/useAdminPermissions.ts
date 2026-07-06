import { useEffect, useState } from "react";
import { api } from "@/app/lib/api";

/**
 * 관리자 실효 권한 조회 훅 (GET /api/admin/me/permissions).
 *
 * 사이드바/메뉴 노출 제어 전용 — 서버 인터셉터(@RequireAdminPermission)가 항상 최종 방어선이다.
 * AdminShell 이 페이지마다 마운트되므로 모듈 캐시로 세션당 1회만 조회한다
 * (권한 변경은 재로그인/새로고침 시 반영 — 60초 서버 캐시와 동일한 완화 기준).
 */

export interface AdminMePermissions {
  role: string;
  superAdmin: boolean;
  permissions: string[];
}

let cached: AdminMePermissions | null = null;
let inflight: Promise<AdminMePermissions> | null = null;

function fetchPermissions(): Promise<AdminMePermissions> {
  if (cached) return Promise.resolve(cached);
  if (!inflight) {
    inflight = api<AdminMePermissions>("/admin/me/permissions", { method: "GET" })
      .then((data) => {
        cached = data;
        return data;
      })
      .finally(() => {
        inflight = null;
      });
  }
  return inflight;
}

/** 로그아웃/계정 전환 시 캐시 초기화용(필요한 곳에서 호출). */
export function clearAdminPermissionsCache() {
  cached = null;
}

/**
 * @param enabled 관리자 계정일 때만 true 로 — 일반 사용자는 호출하지 않는다.
 * @returns 로딩 중/실패면 null (호출부는 role 기반 기본 동작으로 폴백).
 */
export function useAdminPermissions(enabled: boolean): AdminMePermissions | null {
  const [data, setData] = useState<AdminMePermissions | null>(cached);

  useEffect(() => {
    if (!enabled) return;
    let cancelled = false;
    fetchPermissions()
      .then((result) => {
        if (!cancelled) setData(result);
      })
      .catch(() => {
        // 조회 실패 시 null 유지 — 사이드바는 role 기본 노출로 폴백(서버 검증은 별도)
      });
    return () => {
      cancelled = true;
    };
  }, [enabled]);

  return enabled ? data : null;
}
