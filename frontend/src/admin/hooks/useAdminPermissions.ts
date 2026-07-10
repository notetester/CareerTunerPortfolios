import { useEffect, useState } from "react";
import { api } from "@/app/lib/api";
import { subscribeTokenStore } from "@/app/lib/tokenStore";
import type { AdminPermissionStatus } from "@/admin/auth/adminAccess";

/**
 * 관리자 실효 권한 조회 훅 (GET /api/admin/me/permissions).
 * 캐시는 사용자 id+role 단위이며 token 교체/폐기 시 전부 버린다.
 */
export interface AdminMePermissions {
  role: string;
  superAdmin: boolean;
  permissions: string[];
}

export interface AdminPermissionsState {
  status: AdminPermissionStatus;
  data: AdminMePermissions | null;
}

interface KeyedState extends AdminPermissionsState {
  key: string | null;
}

const cache = new Map<string, AdminMePermissions>();
const inflight = new Map<string, Promise<AdminMePermissions>>();
let cacheEpoch = 0;

function permissionKey(userId: number | null, role: string | null): string | null {
  if (!Number.isSafeInteger(userId) || userId == null || userId <= 0 || !role) return null;
  return `${userId}:${role}`;
}

function fetchPermissions(key: string, expectedRole: string): Promise<AdminMePermissions> {
  const cached = cache.get(key);
  if (cached) return Promise.resolve(cached);
  const pending = inflight.get(key);
  if (pending) return pending;

  const requestEpoch = cacheEpoch;
  const request = api<AdminMePermissions>("/admin/me/permissions", { method: "GET" })
    .then((data) => {
      if (
        data.role !== expectedRole
        || !Array.isArray(data.permissions)
        || data.permissions.some((permission) => typeof permission !== "string")
      ) {
        throw new Error("관리자 권한 응답이 현재 사용자와 일치하지 않습니다.");
      }
      if (cacheEpoch === requestEpoch) cache.set(key, data);
      return data;
    })
    .finally(() => {
      if (inflight.get(key) === request) inflight.delete(key);
    });
  inflight.set(key, request);
  return request;
}

export function clearAdminPermissionsCache(): void {
  cacheEpoch += 1;
  cache.clear();
  inflight.clear();
}

// 로그인 계정 전환, refresh token 교체, 로그아웃 모두 이전 계정 권한을 재사용하지 않는다.
subscribeTokenStore(() => clearAdminPermissionsCache());

export function useAdminPermissions(
  userId: number | null,
  role: string | null,
  enabled: boolean,
): AdminPermissionsState {
  const key = permissionKey(userId, role);
  const [tokenRevision, setTokenRevision] = useState(0);
  const [state, setState] = useState<KeyedState>(() => {
    const cached = key ? cache.get(key) : null;
    if (enabled && key && cached) return { key, status: "ready", data: cached };
    return { key, status: enabled && key ? "loading" : "idle", data: null };
  });

  // 같은 userId라도 access token이 교체되면 이전 요청 결과를 버리고 새 세션으로 다시 조회한다.
  useEffect(() => subscribeTokenStore(() => {
    setTokenRevision((current) => current + 1);
  }), []);

  useEffect(() => {
    if (!enabled || !key || !role) {
      setState({ key, status: "idle", data: null });
      return;
    }

    const cached = cache.get(key);
    if (cached) {
      setState({ key, status: "ready", data: cached });
      return;
    }

    let cancelled = false;
    setState({ key, status: "loading", data: null });
    fetchPermissions(key, role)
      .then((data) => {
        if (!cancelled) setState({ key, status: "ready", data });
      })
      .catch(() => {
        if (!cancelled) setState({ key, status: "error", data: null });
      });
    return () => {
      cancelled = true;
    };
  }, [enabled, key, role, tokenRevision]);

  if (state.key !== key) {
    return { status: enabled && key ? "loading" : "idle", data: null };
  }
  return { status: state.status, data: state.data };
}
