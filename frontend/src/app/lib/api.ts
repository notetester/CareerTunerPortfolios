import { clearTokens, getAccessToken, getRefreshToken, setTokens } from "./tokenStore";

/** 백엔드 공통 응답 envelope. */
export interface ApiEnvelope<T> {
  success: boolean;
  code: string;
  message?: string;
  data?: T;
}

export class ApiError extends Error {
  code: string;
  status: number;
  constructor(message: string, code: string, status: number) {
    super(message);
    this.name = "ApiError";
    this.code = code;
    this.status = status;
  }
}

const BASE = "/api"; // Vite 프록시 → http://localhost:8080

function buildHeaders(options: RequestInit, withAuth: boolean): Headers {
  const headers = new Headers(options.headers ?? {});
  const body = options.body;
  const isFormData = typeof FormData !== "undefined" && body instanceof FormData;
  if (body && !isFormData && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  if (withAuth) {
    const token = getAccessToken();
    if (token) headers.set("Authorization", `Bearer ${token}`);
  }
  return headers;
}

// 동시 401에 대해 refresh 가 한 번만 일어나도록 단일 프라미스로 공유한다.
let refreshPromise: Promise<boolean> | null = null;

function tryRefresh(): Promise<boolean> {
  if (refreshPromise) return refreshPromise;
  const refreshToken = getRefreshToken();
  if (!refreshToken) return Promise.resolve(false);
  refreshPromise = (async () => {
    try {
      const res = await fetch(`${BASE}/auth/refresh`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ refreshToken }),
      });
      const env = (await res.json().catch(() => null)) as ApiEnvelope<{
        accessToken: string;
        refreshToken: string;
      }> | null;
      if (res.ok && env?.success && env.data) {
        setTokens({ accessToken: env.data.accessToken, refreshToken: env.data.refreshToken });
        return true;
      }
      clearTokens();
      return false;
    } catch {
      return false;
    } finally {
      refreshPromise = null;
    }
  })();
  return refreshPromise;
}

/** ApiResponse 를 풀어 data 를 반환. 실패 시 ApiError 를 던진다. */
export async function api<T = unknown>(
  path: string,
  options: RequestInit = {},
  config: { auth?: boolean } = {},
): Promise<T> {
  const withAuth = config.auth ?? true;
  let res = await fetch(`${BASE}${path}`, { ...options, headers: buildHeaders(options, withAuth) });

  if (res.status === 401 && withAuth && getRefreshToken()) {
    const refreshed = await tryRefresh();
    if (refreshed) {
      res = await fetch(`${BASE}${path}`, { ...options, headers: buildHeaders(options, true) });
    }
  }

  const env = (await res.json().catch(() => null)) as ApiEnvelope<T> | null;
  if (!res.ok || !env || env.success === false) {
    throw new ApiError(env?.message ?? `요청에 실패했습니다 (${res.status})`, env?.code ?? "ERROR", res.status);
  }
  return env.data as T;
}
