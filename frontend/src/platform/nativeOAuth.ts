import { Browser } from "@capacitor/browser";
import { api } from "@/app/lib/api";
import type { SocialProvider, TokenResponse } from "@/app/auth/AuthContext";
import {
  clearPendingNativeOAuth,
  createPkcePair,
  createNativeOAuthExchangeCoordinator,
  nativeOAuthPendingTtlMs,
  readPendingNativeOAuth,
  savePendingNativeOAuth,
  validateAuthorizationUrl,
  type NativeOAuthProvider,
} from "./nativeOAuthCore.mjs";

let pendingCleanupTimer: number | null = null;
let exchangeCoordinator: ((handoffCode: string) => Promise<TokenResponse>) | null = null;
let pendingExchangeCallers = 0;

interface NativeOAuthStartResponse {
  authorizationUrl: string;
}

function pendingStorage(): Storage {
  return window.localStorage;
}

function requireNoPendingExchange(): void {
  if (pendingExchangeCallers > 0) {
    throw new Error("이전 소셜 로그인 응답을 처리하고 있습니다. 잠시 후 다시 시도해 주세요.");
  }
}

function stopCleanupTimerWhenPendingIsGone(): void {
  if (pendingCleanupTimer === null || readPendingNativeOAuth(pendingStorage())) return;
  window.clearTimeout(pendingCleanupTimer);
  pendingCleanupTimer = null;
}

function pendingExchangeCoordinator(): (handoffCode: string) => Promise<TokenResponse> {
  if (exchangeCoordinator) return exchangeCoordinator;
  exchangeCoordinator = createNativeOAuthExchangeCoordinator<TokenResponse>(
    pendingStorage(),
    (handoffCode, handoffVerifier) => api<TokenResponse>(
      "/auth/oauth/native/exchange",
      {
        method: "POST",
        body: JSON.stringify({ handoffCode, handoffVerifier }),
      },
      { auth: false },
    ),
  );
  return exchangeCoordinator;
}

function schedulePendingCleanup(pending: { verifier: string; createdAt: number }): void {
  if (pendingCleanupTimer !== null) window.clearTimeout(pendingCleanupTimer);
  const remaining = Math.max(0, pending.createdAt + nativeOAuthPendingTtlMs - Date.now());
  pendingCleanupTimer = window.setTimeout(() => {
    const current = readPendingNativeOAuth(pendingStorage());
    if (current?.verifier === pending.verifier) clearPendingNativeOAuth(pendingStorage());
    pendingCleanupTimer = null;
  }, remaining + 100);
}

export function cancelPendingNativeOAuth(): void {
  if (pendingCleanupTimer !== null) window.clearTimeout(pendingCleanupTimer);
  pendingCleanupTimer = null;
  clearPendingNativeOAuth(pendingStorage());
}

export async function startNativeSocialLogin(provider: SocialProvider): Promise<void> {
  requireNoPendingExchange();
  const { verifier, challenge } = await createPkcePair();
  requireNoPendingExchange();
  const pending = { provider, verifier, createdAt: Date.now() } satisfies {
    provider: NativeOAuthProvider;
    verifier: string;
    createdAt: number;
  };
  // 외부 브라우저와 URL에는 노출하지 않는다. 앱 프로세스가 인증 중 종료돼도 콜백을
  // 이어받을 수 있도록 앱 WebView 저장소에 두되 TTL 타이머와 시작 시 정리로 짧게 유지한다.
  savePendingNativeOAuth(pendingStorage(), pending);
  schedulePendingCleanup(pending);

  try {
    const response = await api<NativeOAuthStartResponse>(
      `/auth/oauth/${provider}/native/start`,
      { method: "POST", body: JSON.stringify({ handoffChallenge: challenge }) },
      { auth: false },
    );
    const authorizationUrl = validateAuthorizationUrl(provider, response.authorizationUrl);
    if (!authorizationUrl) {
      throw new Error("소셜 로그인 제공자 주소를 확인할 수 없습니다.");
    }
    await Browser.open({ url: authorizationUrl });
  } catch (error) {
    cancelPendingNativeOAuth();
    throw error;
  }
}

export async function closeNativeOAuthBrowser(): Promise<void> {
  try {
    await Browser.close();
  } catch {
    // Android 시스템 브라우저는 앱 복귀 시 이미 닫혀 있을 수 있다.
  }
}

/** verifier는 URL에 노출하지 않고 성공 때만 제거한다. 공격자 code를 포함한 모든 실패는 TTL 내 재시도한다. */
export async function exchangePendingNativeOAuth(handoffCode: string): Promise<TokenResponse> {
  pendingExchangeCallers += 1;
  try {
    return await pendingExchangeCoordinator()(handoffCode);
  } finally {
    pendingExchangeCallers = Math.max(0, pendingExchangeCallers - 1);
    stopCleanupTimerWhenPendingIsGone();
  }
}

if (typeof window !== "undefined") {
  try {
    const pending = readPendingNativeOAuth(pendingStorage());
    if (pending) schedulePendingCleanup(pending);
  } catch {
    // 저장소가 비활성화된 웹 환경에서는 네이티브 로그인 시작 시 명시적으로 실패한다.
  }
}
