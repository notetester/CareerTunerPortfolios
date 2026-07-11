export type NativeOAuthProvider = "google" | "kakao" | "naver";

export interface PendingNativeOAuth {
  provider: NativeOAuthProvider;
  verifier: string;
  createdAt: number;
}

export interface StorageLike {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
  removeItem(key: string): void;
}

export function base64UrlEncode(bytes: Uint8Array): string;
export function createPkcePair(cryptoImpl?: Crypto): Promise<{ verifier: string; challenge: string }>;
export function savePendingNativeOAuth(storage: StorageLike, pending: PendingNativeOAuth): void;
export function clearPendingNativeOAuth(storage: StorageLike): void;
export function readPendingNativeOAuth(storage: StorageLike, now?: number): PendingNativeOAuth | null;
export function createNativeOAuthExchangeCoordinator<T>(
  storage: StorageLike,
  exchange: (handoffCode: string, handoffVerifier: string) => Promise<T>,
): (handoffCode: string, now?: number) => Promise<T>;
export function validateAuthorizationUrl(provider: NativeOAuthProvider, rawUrl: string): string | null;
export const nativeOAuthPendingKey: string;
export const nativeOAuthPendingTtlMs: number;
