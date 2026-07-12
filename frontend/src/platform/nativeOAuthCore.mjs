const PENDING_KEY = "careertuner.nativeOAuth.pending";
const PENDING_TTL_MS = 10 * 60 * 1000;
const VERIFIER_BYTES = 64;

export function base64UrlEncode(bytes) {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

export async function createPkcePair(cryptoImpl = globalThis.crypto) {
  if (!cryptoImpl?.getRandomValues || !cryptoImpl?.subtle) {
    throw new Error("안전한 로그인 코드를 생성할 수 없는 환경입니다.");
  }
  const random = new Uint8Array(VERIFIER_BYTES);
  cryptoImpl.getRandomValues(random);
  const verifier = base64UrlEncode(random);
  const digest = await cryptoImpl.subtle.digest("SHA-256", new TextEncoder().encode(verifier));
  return {
    verifier,
    challenge: base64UrlEncode(new Uint8Array(digest)),
  };
}

export function savePendingNativeOAuth(storage, pending) {
  storage.setItem(PENDING_KEY, JSON.stringify(pending));
}

export function clearPendingNativeOAuth(storage) {
  storage.removeItem(PENDING_KEY);
}

export function readPendingNativeOAuth(storage, now = Date.now()) {
  const raw = storage.getItem(PENDING_KEY);
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw);
    if (
      (parsed?.provider !== "google" && parsed?.provider !== "kakao" && parsed?.provider !== "naver")
      || typeof parsed.verifier !== "string"
      || !/^[A-Za-z0-9_-]{43,128}$/.test(parsed.verifier)
      || typeof parsed.createdAt !== "number"
      || !Number.isFinite(parsed.createdAt)
      || parsed.createdAt > now + 30_000
      || now - parsed.createdAt > PENDING_TTL_MS
    ) {
      storage.removeItem(PENDING_KEY);
      return null;
    }
    return parsed;
  } catch {
    storage.removeItem(PENDING_KEY);
    return null;
  }
}

function clearPendingNativeOAuthIfCurrent(storage, verifier) {
  const current = readPendingNativeOAuth(storage);
  if (current?.verifier !== verifier) return false;
  clearPendingNativeOAuth(storage);
  return true;
}

/**
 * 동일 handoffCode 콜백은 한 번만 교환한다. callback code 자체가 공격자 입력일 수 있으므로
 * verifier는 성공했을 때만 제거하고 모든 실패에서는 TTL 안에 안전하게 재시도한다.
 */
export function createNativeOAuthExchangeCoordinator(
  storage,
  exchange,
) {
  let inFlight = null;

  return function exchangePending(handoffCode, now = Date.now()) {
    if (!/^[A-Za-z0-9_-]{43}$/.test(handoffCode)) {
      return Promise.reject(new Error("로그인 인계 코드가 올바르지 않습니다."));
    }
    if (inFlight) {
      if (inFlight.handoffCode === handoffCode) return inFlight.promise;
      return Promise.reject(new Error("다른 소셜 로그인 인계 요청을 처리하고 있습니다."));
    }

    const pending = readPendingNativeOAuth(storage, now);
    if (!pending) {
      return Promise.reject(new Error("소셜 로그인 요청이 만료되었습니다. 로그인 화면에서 다시 시작해 주세요."));
    }

    const entry = { handoffCode, promise: null };
    entry.promise = Promise.resolve()
      .then(() => exchange(handoffCode, pending.verifier))
      .then((result) => {
        if (!clearPendingNativeOAuthIfCurrent(storage, pending.verifier)) {
          throw new Error("소셜 로그인 요청이 취소되었거나 새 요청으로 대체되었습니다.");
        }
        return result;
      })
      .finally(() => {
        if (inFlight === entry) inFlight = null;
      });
    inFlight = entry;
    return entry.promise;
  };
}

export function validateAuthorizationUrl(provider, rawUrl) {
  try {
    const url = new URL(rawUrl);
    if (url.protocol !== "https:" || url.username || url.password || url.port) return null;
    const host = url.hostname.toLowerCase();
    if (provider === "kakao" && host === "kauth.kakao.com" && url.pathname === "/oauth/authorize") {
      return url.toString();
    }
    if (provider === "naver" && host === "nid.naver.com" && url.pathname === "/oauth2.0/authorize") {
      return url.toString();
    }
    if (provider === "google" && host === "accounts.google.com" && url.pathname === "/o/oauth2/v2/auth") {
      return url.toString();
    }
    return null;
  } catch {
    return null;
  }
}

export const nativeOAuthPendingKey = PENDING_KEY;
export const nativeOAuthPendingTtlMs = PENDING_TTL_MS;
