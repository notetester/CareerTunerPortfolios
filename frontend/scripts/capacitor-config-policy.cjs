const { isIP } = require("node:net");

const APP_CONFIG = Object.freeze({
  appId: "com.careertuner.app",
  appName: "CareerTuner",
  webDir: "dist",
});

function envValue(env, name) {
  const value = env?.[name];
  return typeof value === "string" ? value.trim() : "";
}

function resolveSyncMode(env) {
  const mode = envValue(env, "CAP_SYNC_MODE") || "release";
  if (mode !== "release" && mode !== "debug") {
    throw new Error(`CAP_SYNC_MODE는 release 또는 debug여야 합니다: ${mode}`);
  }
  return mode;
}

function resolveCleartextOptIn(env) {
  const value = envValue(env, "CAP_ALLOW_CLEARTEXT").toLowerCase();
  if (!value) return false;
  if (value === "true") return true;
  if (value === "false") return false;
  throw new Error("CAP_ALLOW_CLEARTEXT는 true 또는 false여야 합니다.");
}

function isPrivateIpv4(hostname) {
  const parts = hostname.split(".").map(Number);
  if (parts.length !== 4 || parts.some((part) => !Number.isInteger(part) || part < 0 || part > 255)) {
    return false;
  }

  const [first, second] = parts;
  return first === 10
    || first === 127
    || (first === 169 && second === 254)
    || (first === 172 && second >= 16 && second <= 31)
    || (first === 192 && second === 168)
    || (first === 100 && second >= 64 && second <= 127);
}

function isPrivateIpv6(hostname) {
  const normalized = hostname.toLowerCase();
  return normalized === "::1"
    || normalized.startsWith("fc")
    || normalized.startsWith("fd")
    || /^fe[89ab]/.test(normalized);
}

function isLocalDevelopmentHost(hostname) {
  const normalized = hostname.replace(/^\[|\]$/g, "").toLowerCase();
  if (normalized === "localhost"
    || normalized.endsWith(".localhost")
    || normalized.endsWith(".local")
    || normalized === "host.docker.internal") {
    return true;
  }

  const ipVersion = isIP(normalized);
  if (ipVersion === 4) return isPrivateIpv4(normalized);
  if (ipVersion === 6) return isPrivateIpv6(normalized);
  return false;
}

function parseDevelopmentServerUrl(rawUrl) {
  let parsed;
  try {
    parsed = new URL(rawUrl);
  } catch {
    throw new Error(`CAP_SERVER_URL이 올바른 URL이 아닙니다: ${rawUrl}`);
  }

  if (parsed.protocol !== "https:" && parsed.protocol !== "http:") {
    throw new Error("CAP_SERVER_URL은 http 또는 https URL이어야 합니다.");
  }
  if (parsed.username || parsed.password) {
    throw new Error("CAP_SERVER_URL에 사용자 인증 정보를 넣을 수 없습니다.");
  }
  if (parsed.hash) {
    throw new Error("CAP_SERVER_URL에 fragment를 넣을 수 없습니다.");
  }
  return parsed;
}

function createCapacitorConfig(env = process.env) {
  const mode = resolveSyncMode(env);
  const rawServerUrl = envValue(env, "CAP_SERVER_URL");
  const cleartextOptIn = resolveCleartextOptIn(env);

  if (mode === "release" && rawServerUrl) {
    throw new Error("release 동기화에는 CAP_SERVER_URL을 사용할 수 없습니다.");
  }
  if (mode === "release" && cleartextOptIn) {
    throw new Error("release 동기화에는 CAP_ALLOW_CLEARTEXT=true를 사용할 수 없습니다.");
  }
  if (mode === "debug" && cleartextOptIn && !rawServerUrl) {
    throw new Error("CAP_ALLOW_CLEARTEXT=true에는 CAP_SERVER_URL이 필요합니다.");
  }

  let serverUrl;
  let cleartext = false;
  if (rawServerUrl) {
    const parsed = parseDevelopmentServerUrl(rawServerUrl);
    if (parsed.protocol === "http:") {
      if (mode !== "debug" || !cleartextOptIn) {
        throw new Error("HTTP 개발 서버는 debug 모드와 CAP_ALLOW_CLEARTEXT=true가 모두 필요합니다.");
      }
      if (!isLocalDevelopmentHost(parsed.hostname)) {
        throw new Error(`HTTP 개발 서버는 로컬/LAN/Tailscale 주소만 허용합니다: ${parsed.hostname}`);
      }
      cleartext = true;
    } else if (cleartextOptIn) {
      throw new Error("HTTPS 개발 서버에는 CAP_ALLOW_CLEARTEXT=true를 설정하지 마세요.");
    }
    serverUrl = parsed.toString();
  }

  return {
    ...APP_CONFIG,
    server: {
      androidScheme: "https",
      cleartext,
      ...(serverUrl ? { url: serverUrl } : {}),
    },
    android: {
      // HTTPS 문서가 HTTP 리소스를 조용히 불러오는 경로는 debug에서도 열지 않는다.
      allowMixedContent: false,
      // 릴리스 WebView의 DOM·토큰을 USB/ADB 디버거에 노출하지 않는다.
      // 개발 프로필에서만 명시적으로 열어 Capacitor의 빌드 유형 추론에 의존하지 않는다.
      webContentsDebuggingEnabled: mode === "debug",
    },
  };
}

function assertGeneratedCapacitorConfig(config, options = {}) {
  const mode = options.mode || "release";
  if (!config || typeof config !== "object" || Array.isArray(config)) {
    throw new Error("생성된 Capacitor 설정이 JSON 객체가 아닙니다.");
  }

  const server = config.server;
  const android = config.android;
  if (!server || typeof server !== "object" || Array.isArray(server)) {
    throw new Error("생성된 Capacitor 설정에 server 항목이 없습니다.");
  }
  if (!android || typeof android !== "object" || Array.isArray(android)) {
    throw new Error("생성된 Capacitor 설정에 android 항목이 없습니다.");
  }
  if (server.androidScheme !== "https") {
    throw new Error(`Android WebView scheme은 https여야 합니다: ${server.androidScheme ?? "<missing>"}`);
  }
  if (android.allowMixedContent !== false) {
    throw new Error("Android WebView mixed content는 false여야 합니다.");
  }
  if (android.webContentsDebuggingEnabled !== (mode === "debug")) {
    throw new Error(
      mode === "release"
        ? "release Android WebView 원격 디버깅은 false여야 합니다."
        : "debug Android WebView 원격 디버깅은 true여야 합니다.",
    );
  }
  if (Object.hasOwn(server, "allowNavigation")
    && (!Array.isArray(server.allowNavigation) || server.allowNavigation.length > 0)) {
    throw new Error("WebView allowNavigation 예외는 허용하지 않습니다.");
  }

  if (mode === "release") {
    if (server.cleartext !== false) {
      throw new Error("release Capacitor 설정의 cleartext는 false여야 합니다.");
    }
    if (Object.hasOwn(server, "url")) {
      throw new Error("release Capacitor 설정에는 외부 server.url이 없어야 합니다.");
    }
    return config;
  }

  if (mode !== "debug") {
    throw new Error(`검사할 동기화 모드가 올바르지 않습니다: ${mode}`);
  }
  if (typeof server.url === "string" && server.url.trim()) {
    const parsed = parseDevelopmentServerUrl(server.url);
    if (parsed.protocol === "http:") {
      if (server.cleartext !== true || !isLocalDevelopmentHost(parsed.hostname)) {
        throw new Error("debug HTTP server.url은 로컬 주소와 cleartext=true가 필요합니다.");
      }
    } else if (server.cleartext !== false) {
      throw new Error("debug HTTPS server.url의 cleartext는 false여야 합니다.");
    }
  } else if (server.cleartext !== false) {
    throw new Error("server.url이 없는 debug 설정의 cleartext는 false여야 합니다.");
  }
  return config;
}

module.exports = {
  assertGeneratedCapacitorConfig,
  createCapacitorConfig,
  isLocalDevelopmentHost,
  resolveSyncMode,
};
