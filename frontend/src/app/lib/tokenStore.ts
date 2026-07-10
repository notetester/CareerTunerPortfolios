// JWT 토큰 보관소(localStorage). access/refresh 토큰을 한 키에 저장한다.
const KEY = "careertuner.auth";

export type TokenStoreEvent = "changed" | "cleared";
type TokenStoreListener = (event: TokenStoreEvent) => void;

const listeners = new Set<TokenStoreListener>();
let storageListenerInstalled = false;
let revision = 0;

export interface StoredTokens {
  accessToken: string;
  refreshToken: string;
}

export interface TokenStoreSnapshot {
  revision: number;
  tokens: StoredTokens | null;
}

export function getTokens(): StoredTokens | null {
  try {
    const raw = localStorage.getItem(KEY);
    return raw ? (JSON.parse(raw) as StoredTokens) : null;
  } catch {
    return null;
  }
}

export function getAccessToken(): string | null {
  return getTokens()?.accessToken ?? null;
}

export function getRefreshToken(): string | null {
  return getTokens()?.refreshToken ?? null;
}

export function setTokens(tokens: StoredTokens): void {
  localStorage.setItem(KEY, JSON.stringify(tokens));
  revision += 1;
  publish("changed");
}

export function clearTokens(): void {
  localStorage.removeItem(KEY);
  revision += 1;
  publish("cleared");
}

/** 비동기 인증 작업이 시작된 세션을 식별한다. */
export function getTokenStoreSnapshot(): TokenStoreSnapshot {
  installStorageListener();
  return { revision, tokens: getTokens() };
}

/** 다른 로그인·로그아웃·토큰 갱신이 끼어들지 않았는지 값과 revision을 함께 확인한다. */
export function isTokenStoreSnapshotCurrent(expected: TokenStoreSnapshot): boolean {
  if (revision !== expected.revision) return false;
  const current = getTokens();
  if (!current || !expected.tokens) return current === expected.tokens;
  return current.accessToken === expected.tokens.accessToken
    && current.refreshToken === expected.tokens.refreshToken;
}

export function setTokensIfUnchanged(expected: TokenStoreSnapshot, tokens: StoredTokens): boolean {
  if (!isTokenStoreSnapshotCurrent(expected)) return false;
  setTokens(tokens);
  return true;
}

export function clearTokensIfUnchanged(expected: TokenStoreSnapshot): boolean {
  if (!isTokenStoreSnapshotCurrent(expected)) return false;
  clearTokens();
  return true;
}

/** 같은 탭의 refresh 실패와 다른 탭의 로그인/로그아웃을 AuthContext·권한 캐시에 함께 알린다. */
export function subscribeTokenStore(listener: TokenStoreListener): () => void {
  listeners.add(listener);
  installStorageListener();
  return () => listeners.delete(listener);
}

function publish(event: TokenStoreEvent): void {
  listeners.forEach((listener) => listener(event));
}

function installStorageListener(): void {
  if (storageListenerInstalled || typeof window === "undefined") return;
  storageListenerInstalled = true;
  window.addEventListener("storage", (event) => {
    if (event.key !== KEY) return;
    revision += 1;
    publish(event.newValue ? "changed" : "cleared");
  });
}
