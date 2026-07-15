export interface ServerPreset {
  value: string;
  label: string;
  /** null = 빌드 기본값, undefined = 직접 입력. */
  url: string | null | undefined;
}

export const SERVER_PRESETS: ServerPreset[] = [
  { value: "default", label: "기본값 (빌드 설정 사용)", url: null },
  { value: "emulator", label: "Android 에뮬레이터 (호스트 PC)", url: "http://10.0.2.2:8080/api" },
  { value: "local-web", label: "로컬 웹 브라우저", url: "http://localhost:8080/api" },
  { value: "tailscale", label: "Tailscale (개발 PC 원격)", url: "https://localhost/api" },
  { value: "aws", label: "AWS 통합 배포", url: "https://careertuner.example.com/api" },
  { value: "custom", label: "직접 입력", url: undefined },
];

export interface ServerOverrideResult {
  override: string | null;
  error: string | null;
}

function isLoopbackHost(hostname: string): boolean {
  const host = hostname.toLowerCase();
  if (host === "localhost" || host.endsWith(".localhost") || host === "[::1]") return true;

  const parts = host.split(".").map(Number);
  if (parts.length !== 4 || parts.some((part) => !Number.isInteger(part) || part < 0 || part > 255)) {
    return false;
  }
  return parts[0] === 127;
}

function isPrivateNetworkHost(hostname: string): boolean {
  const host = hostname.toLowerCase();
  if (/^\[(?:fc|fd|fe8|fe9|fea|feb)/.test(host)) return true;
  const parts = host.split(".").map(Number);
  if (parts.length !== 4 || parts.some((part) => !Number.isInteger(part) || part < 0 || part > 255)) {
    return false;
  }
  return parts[0] === 10
    || (parts[0] === 192 && parts[1] === 168)
    || (parts[0] === 172 && parts[1] >= 16 && parts[1] <= 31);
}

export function initialServerPreset(override: string | null): string {
  if (!override) return "default";
  const matched = SERVER_PRESETS.find((preset) => preset.url === override);
  return matched ? matched.value : "custom";
}

export function resolveServerOverride(
  presetValue: string,
  customUrl: string,
  allowPrivateHttp = false,
): ServerOverrideResult {
  const preset = SERVER_PRESETS.find((item) => item.value === presetValue);
  if (!preset) return { override: null, error: "알 수 없는 서버 프리셋입니다." };
  if (preset.url === null) return { override: null, error: null };

  const raw = (preset.url ?? customUrl).trim();
  if (!raw) return { override: null, error: "서버 주소를 입력해 주세요." };

  try {
    const parsed = new URL(raw);
    if (parsed.protocol !== "http:" && parsed.protocol !== "https:") {
      return { override: null, error: "서버 주소는 http 또는 https URL이어야 합니다." };
    }
    if (parsed.username || parsed.password) {
      return { override: null, error: "인증 정보가 포함된 서버 주소는 저장할 수 없습니다." };
    }
    if (parsed.search || parsed.hash) {
      return { override: null, error: "서버 주소에는 query 또는 fragment를 포함할 수 없습니다." };
    }
    const normalizedPath = parsed.pathname.replace(/\/+$/, "") || "/";
    if (normalizedPath !== "/api") {
      return { override: null, error: "서버 주소는 /api 경로까지 포함해야 합니다." };
    }
    if (parsed.protocol === "http:"
      && !isLoopbackHost(parsed.hostname)
      && !(allowPrivateHttp && isPrivateNetworkHost(parsed.hostname))) {
      return {
        override: null,
        error: allowPrivateHttp
          ? "공개 서버는 HTTPS 주소만 사용할 수 있습니다."
          : "평문 사설망 HTTP는 개발 빌드에서만 사용할 수 있습니다.",
      };
    }
    parsed.pathname = "/api";
    return { override: parsed.toString().replace(/\/+$/, ""), error: null };
  } catch {
    return { override: null, error: "올바른 서버 URL을 입력해 주세요." };
  }
}

export function serverOverrideChanged(current: string | null, next: string | null): boolean {
  const normalize = (value: string | null) => value?.trim().replace(/\/+$/, "") || null;
  return normalize(current) !== normalize(next);
}
