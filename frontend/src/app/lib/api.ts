import { clearTokens, getAccessToken, getRefreshToken, setTokens } from "./tokenStore";
import { MOCK_UNHANDLED, resolveMock } from "./mock";

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

// API 베이스 경로.
//  - 기본: 상대경로 "/api" (웹: Vite 프록시 → :8080, 배포: 동일 출처 백엔드)
//  - VITE_API_BASE_URL 지정 시 그 절대 URL 사용 (예: 모바일 앱이 PC LAN 백엔드를 가리킬 때
//    VITE_API_BASE_URL=http://192.168.0.10:8080/api — 백엔드 CORS 허용 필요)
const BASE = ((import.meta.env.VITE_API_BASE_URL as string | undefined)?.replace(/\/+$/, "")) || "/api";

// 데모/목 모드: 백엔드 없이 동작(자체완결 APK·GitHub Pages 데모). 등록된 mock 핸들러로 응답한다.
const USE_MOCK = import.meta.env.VITE_USE_MOCK === "true";

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

  // 데모/목 모드: 네트워크 대신 mock 레지스트리로 응답. 미등록 엔드포인트는 "데모 미제공" 에러.
  if (USE_MOCK) {
    const mocked = await resolveMock(path, options);
    if (mocked !== MOCK_UNHANDLED) return mocked as T;
    throw new ApiError("데모 모드에서는 제공되지 않는 데이터입니다.", "DEMO_UNAVAILABLE", 501);
  }

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
