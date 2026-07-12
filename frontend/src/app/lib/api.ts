import { apiBase } from "./apiBase";
import { applyFrontendClientHeader } from "./apiClientCore.mjs";
import { isNativeApp } from "@/platform/capacitor";
import {
  clearTokensIfUnchanged,
  getTokenStoreSnapshot,
  isTokenStoreSnapshotCurrent,
  setTokensIfUnchanged,
  subscribeTokenStore,
  type TokenStoreSnapshot,
} from "./tokenStore";
import {
  activateOutageFallbackIfConfirmed,
  isNetworkOutageError,
  isOutageFallbackActive,
  isOutageStatus,
  shouldUseMockData,
} from "./outageFallback";

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

// API 베이스 경로는 apiBase() 단일 소스를 사용한다(app/lib/apiBase.ts).
//  - 우선순위: (네이티브 앱/dev 의) 런타임 오버라이드 → VITE_API_BASE_URL → 상대경로 "/api"
//  - 런타임 오버라이드가 바뀔 수 있어 상수 캐시 없이 매 요청 시 평가한다.

type RequestResult<T> =
  | { kind: "real"; response: Response }
  | { kind: "mock"; value: T };

let mockModulePromise: Promise<typeof import("./mock")> | null = null;

function loadMockModule(): Promise<typeof import("./mock")> {
  mockModulePromise ??= import("./mock");
  return mockModulePromise;
}

function canFallbackSameRequest(init: RequestInit): boolean {
  const method = (init.method ?? "GET").toUpperCase();
  return method === "GET" || method === "HEAD";
}

function demoUnavailableMessage(kind: "data" | "file"): string {
  const prefix = isOutageFallbackActive() ? "장애 체험 모드" : "데모 모드";
  return kind === "file"
    ? `${prefix}에서는 제공되지 않는 파일입니다.`
    : `${prefix}에서는 제공되지 않는 데이터입니다.`;
}

async function resolveMockData<T>(path: string, options: RequestInit): Promise<T> {
  const mock = await loadMockModule();
  const value = await mock.resolveMock(path, options);
  if (value === mock.MOCK_FORBIDDEN) {
    throw new ApiError("관리자 권한이 필요한 데모 요청입니다.", "FORBIDDEN", 403);
  }
  if (value !== mock.MOCK_UNHANDLED) return value as T;
  throw new ApiError(demoUnavailableMessage("data"), "DEMO_UNAVAILABLE", 501);
}

async function resolveMockBlob(path: string, options: RequestInit): Promise<Blob> {
  const mock = await loadMockModule();
  const value = await mock.resolveMock(path, options);
  if (value === mock.MOCK_FORBIDDEN) {
    throw new ApiError("관리자 권한이 필요한 데모 요청입니다.", "FORBIDDEN", 403);
  }
  if (value === mock.MOCK_UNHANDLED) {
    throw new ApiError(demoUnavailableMessage("file"), "DEMO_UNAVAILABLE", 501);
  }
  if (value instanceof Blob) return value;
  throw new ApiError("데모 파일 응답 형식이 올바르지 않습니다.", "DEMO_INVALID_RESPONSE", 500);
}

/** 실제 요청을 우선한다. 장애가 확인되면 조회만 즉시 mock으로 대체하고 mutation은 첫 시도를 실패 처리한다. */
async function requestRealFirst<T>(
  url: string,
  init: RequestInit,
  resolveFallback: () => Promise<T>,
): Promise<RequestResult<T>> {
  if (shouldUseMockData()) {
    return { kind: "mock", value: await resolveFallback() };
  }

  try {
    const response = await fetch(url, init);
    if (isOutageStatus(response.status) && await activateOutageFallbackIfConfirmed()) {
      if (canFallbackSameRequest(init)) {
        return { kind: "mock", value: await resolveFallback() };
      }
      throw createOutageMutationUncertainError();
    }
    return { kind: "real", response };
  } catch (error) {
    if (isNetworkOutageError(error) && await activateOutageFallbackIfConfirmed()) {
      if (canFallbackSameRequest(init)) {
        return { kind: "mock", value: await resolveFallback() };
      }
      throw createOutageMutationUncertainError();
    }
    throw error;
  }
}

export function createOutageMutationUncertainError(): ApiError {
  return new ApiError(
    "운영 서비스가 요청을 처리했는지 확인할 수 없어 안전을 위해 결과를 반영하지 않았습니다. 장애 체험 모드에서 다시 시도해 주세요.",
    "OUTAGE_MUTATION_UNCERTAIN",
    503,
  );
}

function buildHeaders(options: RequestInit, accessToken: string | null): Headers {
  const headers = new Headers(options.headers ?? {});
  applyFrontendClientHeader(headers, isNativeApp());
  const body = options.body;
  const isFormData = typeof FormData !== "undefined" && body instanceof FormData;
  if (body && !isFormData && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  if (accessToken) headers.set("Authorization", `Bearer ${accessToken}`);
  return headers;
}

interface RefreshAttempt {
  snapshot: TokenStoreSnapshot;
  refreshToken: string;
  promise: Promise<TokenStoreSnapshot | null>;
}

interface CompletedRefresh {
  source: TokenStoreSnapshot;
  target: TokenStoreSnapshot;
}

// 같은 세션의 동시 401만 하나로 합친다. 세션이 바뀌면 이전 응답은 CAS에서 폐기한다.
let refreshAttempt: RefreshAttempt | null = null;
let completedRefresh: CompletedRefresh | null = null;

function sameSnapshot(a: TokenStoreSnapshot, b: TokenStoreSnapshot): boolean {
  return a.revision === b.revision
    && a.tokens?.accessToken === b.tokens?.accessToken
    && a.tokens?.refreshToken === b.tokens?.refreshToken;
}

function tryRefresh(snapshot: TokenStoreSnapshot): Promise<TokenStoreSnapshot | null> {
  const refreshToken = snapshot.tokens?.refreshToken;
  if (!refreshToken) return Promise.resolve(null);
  if (
    refreshAttempt
    && sameSnapshot(refreshAttempt.snapshot, snapshot)
    && refreshAttempt.refreshToken === refreshToken
  ) {
    return refreshAttempt.promise;
  }
  if (
    completedRefresh
    && sameSnapshot(completedRefresh.source, snapshot)
    && isTokenStoreSnapshotCurrent(completedRefresh.target)
  ) {
    return Promise.resolve(completedRefresh.target);
  }
  if (!isTokenStoreSnapshotCurrent(snapshot)) return Promise.resolve(null);

  const attempt: RefreshAttempt = {
    snapshot,
    refreshToken,
    promise: Promise.resolve(null),
  };
  attempt.promise = (async () => {
    try {
      const refreshBody = JSON.stringify({ refreshToken });
      const res = await fetch(`${apiBase()}/auth/refresh`, {
        method: "POST",
        headers: buildHeaders({ body: refreshBody }, null),
        body: refreshBody,
      });
      const env = (await res.json().catch(() => null)) as ApiEnvelope<{
        accessToken: string;
        refreshToken: string;
      }> | null;
      if (res.ok && env?.success && env.data) {
        const updated = setTokensIfUnchanged(snapshot, {
          accessToken: env.data.accessToken,
          refreshToken: env.data.refreshToken,
        });
        if (!updated) return null;
        const target = getTokenStoreSnapshot();
        completedRefresh = { source: snapshot, target };
        return target;
      }
      clearTokensIfUnchanged(snapshot);
      return null;
    } catch {
      return null;
    } finally {
      if (refreshAttempt === attempt) refreshAttempt = null;
    }
  })();
  refreshAttempt = attempt;
  return attempt.promise;
}

function assertAuthenticatedRequestStillCurrent(snapshot: TokenStoreSnapshot | null): void {
  if (snapshot && !isTokenStoreSnapshotCurrent(snapshot)) {
    throw new ApiError(
      "로그인 계정이 변경되어 이전 요청 결과를 폐기했습니다. 다시 시도해 주세요.",
      "AUTH_SESSION_CHANGED",
      409,
    );
  }
}

/** ApiResponse 를 풀어 data 를 반환. 실패 시 ApiError 를 던진다. */
export async function api<T = unknown>(
  path: string,
  options: RequestInit = {},
  config: { auth?: boolean } = {},
): Promise<T> {
  const withAuth = config.auth ?? true;
  let sessionSnapshot = withAuth ? getTokenStoreSnapshot() : null;
  const resolveFallback = () => resolveMockData<T>(path, options);
  let result = await requestRealFirst(
    `${apiBase()}${path}`,
    { ...options, headers: buildHeaders(options, sessionSnapshot?.tokens?.accessToken ?? null) },
    resolveFallback,
  );
  if (result.kind === "mock") {
    assertAuthenticatedRequestStillCurrent(sessionSnapshot);
    return result.value;
  }
  let res = result.response;

  if (res.status === 401 && sessionSnapshot?.tokens?.refreshToken) {
    const refreshedSnapshot = await tryRefresh(sessionSnapshot);
    if (refreshedSnapshot && isTokenStoreSnapshotCurrent(refreshedSnapshot)) {
      sessionSnapshot = refreshedSnapshot;
      result = await requestRealFirst(
        `${apiBase()}${path}`,
        { ...options, headers: buildHeaders(options, refreshedSnapshot.tokens?.accessToken ?? null) },
        resolveFallback,
      );
      if (result.kind === "mock") {
        assertAuthenticatedRequestStillCurrent(sessionSnapshot);
        return result.value;
      }
      res = result.response;
    }
  }

  const env = (await res.json().catch(() => null)) as ApiEnvelope<T> | null;
  assertAuthenticatedRequestStillCurrent(sessionSnapshot);
  if (!res.ok || !env || env.success === false) {
    throw new ApiError(env?.message ?? `요청에 실패했습니다 (${res.status})`, env?.code ?? "ERROR", res.status);
  }
  return env.data as T;
}

/** JSON envelope가 아닌 파일 바이트를 받는 API. mock 모드에서도 같은 route registry를 사용한다. */
export async function apiBlob(
  path: string,
  options: RequestInit = {},
  config: { auth?: boolean } = {},
): Promise<Blob> {
  const withAuth = config.auth ?? true;
  let sessionSnapshot = withAuth ? getTokenStoreSnapshot() : null;
  const resolveFallback = () => resolveMockBlob(path, options);
  let result = await requestRealFirst(
    `${apiBase()}${path}`,
    { ...options, headers: buildHeaders(options, sessionSnapshot?.tokens?.accessToken ?? null) },
    resolveFallback,
  );
  if (result.kind === "mock") {
    assertAuthenticatedRequestStillCurrent(sessionSnapshot);
    return result.value;
  }
  let res = result.response;
  if (res.status === 401 && sessionSnapshot?.tokens?.refreshToken) {
    const refreshedSnapshot = await tryRefresh(sessionSnapshot);
    if (refreshedSnapshot && isTokenStoreSnapshotCurrent(refreshedSnapshot)) {
      sessionSnapshot = refreshedSnapshot;
      result = await requestRealFirst(
        `${apiBase()}${path}`,
        { ...options, headers: buildHeaders(options, refreshedSnapshot.tokens?.accessToken ?? null) },
        resolveFallback,
      );
      if (result.kind === "mock") {
        assertAuthenticatedRequestStillCurrent(sessionSnapshot);
        return result.value;
      }
      res = result.response;
    }
  }
  assertAuthenticatedRequestStillCurrent(sessionSnapshot);
  if (!res.ok) {
    const env = (await res.json().catch(() => null)) as ApiEnvelope<unknown> | null;
    throw new ApiError(env?.message ?? `파일 요청에 실패했습니다 (${res.status})`, env?.code ?? "ERROR", res.status);
  }
  const blob = await res.blob();
  // 큰 파일 본문을 읽는 동안에도 계정이 바뀔 수 있다. 바이트 소비가 끝난 뒤
  // 다시 검증해 이전 계정 파일이 새 계정 화면에서 다운로드되지 않게 한다.
  assertAuthenticatedRequestStillCurrent(sessionSnapshot);
  return blob;
}

export interface AuthenticatedRawResponse {
  response: Response;
  /** 스트림의 각 이벤트를 UI에 반영하기 직전에 호출한다. */
  assertSessionCurrent(): void;
  /** 응답 본문 소비가 끝나면 계정 변경 감시기를 해제한다. */
  dispose(): void;
}

/**
 * JSON envelope가 아닌 SSE 같은 장기 응답용 인증 fetch다.
 * 중앙 401 갱신을 공유하고, 요청 도중 로그인 계정이 바뀌면 네트워크와 결과 소비를 함께 중단한다.
 */
export async function apiRaw(
  path: string,
  options: RequestInit = {},
  config: { auth?: boolean } = {},
): Promise<AuthenticatedRawResponse> {
  const withAuth = config.auth ?? true;
  let sessionSnapshot = withAuth ? getTokenStoreSnapshot() : null;
  const controller = new AbortController();
  const callerSignal = options.signal;
  const abortFromCaller = () => controller.abort(callerSignal?.reason);
  if (callerSignal?.aborted) abortFromCaller();
  else callerSignal?.addEventListener("abort", abortFromCaller, { once: true });
  const unsubscribe = withAuth
    ? subscribeTokenStore((event) => {
        // 동일 계정의 access-token 회전은 계속 진행하고, 실제 로그인 경계만 끊는다.
        if (event !== "refreshed") {
          controller.abort(new ApiError(
            "로그인 계정이 변경되어 이전 요청을 중단했습니다. 다시 시도해 주세요.",
            "AUTH_SESSION_CHANGED",
            409,
          ));
        }
      })
    : () => {};
  let disposed = false;
  const dispose = () => {
    if (disposed) return;
    disposed = true;
    unsubscribe();
    callerSignal?.removeEventListener("abort", abortFromCaller);
  };
  const assertSessionCurrent = () => {
    if (controller.signal.aborted) {
      const reason = controller.signal.reason;
      throw reason instanceof Error ? reason : new DOMException("Aborted", "AbortError");
    }
    assertAuthenticatedRequestStillCurrent(sessionSnapshot);
  };

  try {
    const request = (snapshot: TokenStoreSnapshot | null) => fetch(`${apiBase()}${path}`, {
      ...options,
      headers: buildHeaders(options, snapshot?.tokens?.accessToken ?? null),
      signal: controller.signal,
    });
    let response = await request(sessionSnapshot);
    if (response.status === 401 && sessionSnapshot?.tokens?.refreshToken) {
      const refreshedSnapshot = await tryRefresh(sessionSnapshot);
      if (refreshedSnapshot && isTokenStoreSnapshotCurrent(refreshedSnapshot)) {
        sessionSnapshot = refreshedSnapshot;
        response = await request(refreshedSnapshot);
      }
    }
    assertSessionCurrent();
    return { response, assertSessionCurrent, dispose };
  } catch (error) {
    dispose();
    if (controller.signal.aborted && controller.signal.reason instanceof Error) {
      throw controller.signal.reason;
    }
    throw error;
  }
}
