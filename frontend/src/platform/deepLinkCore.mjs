const CANONICAL_APP_LINK_HOST = "careertuner.example.com";
const NATIVE_OAUTH_CALLBACK_PATH = "/auth/callback";
const NATIVE_SOCIAL_LINK_CALLBACK_PATH = "/profile/detail";
const ALLOWED_ROUTE_ROOTS = new Set([
  "",
  "home",
  "dashboard",
  "applications",
  "interview",
  "m",
  "mic-remote",
  "correction",
  "analysis",
  "planner",
  "career-roadmap",
  "certificates",
  "messenger",
  "community",
  "profile",
  "billing",
  "pricing",
  "settings",
  "features",
  "service",
  "support",
  "company",
  "jobs",
  "legal",
  "login",
  "auth",
  "notifications",
  "admin",
]);
const FORBIDDEN_AUTH_PARAMS = new Set(["accesstoken", "refreshtoken"]);
const SOCIAL_LINK_CALLBACK_PARAMS = new Set(["socialLinked", "socialMock", "socialLinkError"]);

function isValidNativeSocialLinkCallbackUrl(url) {
  if (url.hash) return false;
  const keys = [...url.searchParams.keys()];
  if (!keys.length || keys.some((key) => !SOCIAL_LINK_CALLBACK_PARAMS.has(key))) return false;
  if ([...SOCIAL_LINK_CALLBACK_PARAMS].some((key) => url.searchParams.getAll(key).length > 1)) return false;

  const linked = url.searchParams.get("socialLinked");
  const mockPresent = url.searchParams.has("socialMock");
  const mock = url.searchParams.get("socialMock");
  const error = url.searchParams.get("socialLinkError");
  if (linked) {
    return /^(?:KAKAO|NAVER|GOOGLE)$/.test(linked)
      && !error
      && (!mockPresent || mock === "1");
  }
  return (error === "social_login_cancelled" || error === "social_login_failed")
    && !mockPresent;
}

function containsUnsafeRawPath(rawUrl) {
  const pathPart = rawUrl.split(/[?#]/, 1)[0];
  return /[\\\u0000-\u001f\u007f]/.test(pathPart)
    || /(?:^|\/)(?:\.{2}|%2e(?:%2e|\.)|\.%2e)(?:\/|$)/i.test(pathPart)
    || /%2f|%5c/i.test(pathPart);
}

function isAllowedInternalPath(path) {
  if (!path.startsWith("/") || path.startsWith("//") || containsUnsafeRawPath(path)) return false;
  let url;
  try {
    url = new URL(path, "https://careertuner.invalid");
  } catch {
    return false;
  }
  if (url.origin !== "https://careertuner.invalid") return false;
  let decodedPath;
  try {
    decodedPath = decodeURIComponent(url.pathname);
  } catch {
    return false;
  }
  if (/[\\\u0000-\u001f\u007f]/.test(decodedPath)) return false;
  const root = decodedPath.split("/")[1]?.toLowerCase() ?? "";
  if (!ALLOWED_ROUTE_ROOTS.has(root)) return false;
  for (const key of url.searchParams.keys()) {
    if (FORBIDDEN_AUTH_PARAMS.has(key.toLowerCase())) return false;
  }
  if (url.pathname === NATIVE_OAUTH_CALLBACK_PATH) {
    if (url.hash) return false;
    const keys = [...url.searchParams.keys()];
    if (keys.some((key) => key !== "handoffCode" && key !== "error")) return false;
    if (url.searchParams.getAll("handoffCode").length > 1 || url.searchParams.getAll("error").length > 1) return false;
    const code = url.searchParams.get("handoffCode");
    const error = url.searchParams.get("error");
    if ((code ? 1 : 0) + (error ? 1 : 0) !== 1) return false;
    if (code && !/^[A-Za-z0-9_-]{43}$/.test(code)) return false;
    if (error && !/^[A-Za-z0-9_.-]{1,80}$/.test(error)) return false;
  }
  if (url.pathname === NATIVE_SOCIAL_LINK_CALLBACK_PATH && url.search) {
    if (!isValidNativeSocialLinkCallbackUrl(url)) return false;
  }
  return true;
}

function decodesToNativeOAuthCallback(path) {
  try {
    const url = new URL(path, "https://careertuner.invalid");
    return decodeURIComponent(url.pathname).toLowerCase() === NATIVE_OAUTH_CALLBACK_PATH;
  } catch {
    return false;
  }
}

function decodesToNativeSocialLinkCallback(path) {
  try {
    const url = new URL(path, "https://careertuner.invalid");
    return decodeURIComponent(url.pathname).toLowerCase() === NATIVE_SOCIAL_LINK_CALLBACK_PATH;
  } catch {
    return false;
  }
}

/** Manifest와 동일한 HTTPS 호스트 및 careertuner 스킴만 내부 라우트로 변환한다. */
export function toAppPath(rawUrl) {
  if (
    !rawUrl
    || rawUrl.length > 4096
    || containsUnsafeRawPath(rawUrl)
    || /[?#&](?:accessToken|refreshToken)=/i.test(rawUrl)
  ) return null;
  let candidate;
  if (rawUrl.slice(0, "careertuner://".length).toLowerCase() === "careertuner://") {
    // Android WebView는 비표준 스킴 URL의 hostname을 빈 문자열로 돌려줄 수 있다.
    // authority를 직접 분리하고, 최종 후보는 아래 공통 내부 경로 검증을 다시 거친다.
    const rest = rawUrl.slice("careertuner://".length).replace(/^\/+/, "");
    const suffixIndex = rest.search(/[/?#]/);
    const routeRoot = (suffixIndex < 0 ? rest : rest.slice(0, suffixIndex)).toLowerCase();
    const suffix = suffixIndex < 0 ? "" : rest.slice(suffixIndex);
    if (routeRoot.includes("@") || routeRoot.includes(":")) return null;
    candidate = `/${routeRoot}${suffix}`;
    // OAuth handoff는 OS 소유권 검증이 가능한 canonical HTTPS App Link로만 받는다.
    // 커스텀 스킴 callback을 허용하면 다른 앱이 code를 주입해 pending verifier를 소모할 수 있다.
    if (decodesToNativeOAuthCallback(candidate)) return null;
    // 소셜 연결 결과 query도 verified HTTPS 경로에서만 신뢰한다. query 없는 일반 profile 딥링크는 유지한다.
    if (candidate.includes("?") && decodesToNativeSocialLinkCallback(candidate)) return null;
  } else {
    let url;
    try {
      url = new URL(rawUrl);
    } catch {
      return null;
    }
    if (
      url.protocol !== "https:"
      || url.hostname.toLowerCase() !== CANONICAL_APP_LINK_HOST
      || url.username
      || url.password
      || url.port
    ) return null;
    // AndroidManifest의 두 exact verified App Link filter와 동일한 경로만 앱으로 라우팅한다.
    if (url.pathname === NATIVE_SOCIAL_LINK_CALLBACK_PATH) {
      if (!isValidNativeSocialLinkCallbackUrl(url)) return null;
    } else if (url.pathname !== NATIVE_OAUTH_CALLBACK_PATH) {
      return null;
    }
    candidate = `${url.pathname}${url.search}${url.hash}` || "/";
  }

  return isAllowedInternalPath(candidate) ? candidate : null;
}

export function isNativeOAuthCallbackPath(path) {
  if (!path) return false;
  try {
    const url = new URL(path, "https://careertuner.invalid");
    return url.origin === "https://careertuner.invalid"
      && url.pathname === "/auth/callback"
      && (url.searchParams.has("handoffCode") || url.searchParams.has("error"));
  } catch {
    return false;
  }
}

export function isNativeSocialLinkCallbackPath(path) {
  if (!path) return false;
  try {
    const url = new URL(path, "https://careertuner.invalid");
    return url.origin === "https://careertuner.invalid"
      && url.pathname === NATIVE_SOCIAL_LINK_CALLBACK_PATH
      && isValidNativeSocialLinkCallbackUrl(url);
  } catch {
    return false;
  }
}

export const canonicalAppLinkHost = CANONICAL_APP_LINK_HOST;
export const nativeOAuthCallbackPath = NATIVE_OAUTH_CALLBACK_PATH;
export const nativeSocialLinkCallbackPath = NATIVE_SOCIAL_LINK_CALLBACK_PATH;
