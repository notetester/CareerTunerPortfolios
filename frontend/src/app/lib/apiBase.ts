/**
 * API 베이스 URL 단일 소스.
 * 우선순위: (네이티브 앱/개발 모드의) 런타임 오버라이드 → VITE_API_BASE_URL → 상대경로 "/api".
 * 런타임 오버라이드는 APK 재빌드 없이 환경(로컬/테일스케일/AWS)을 바꿀 수 있게 한다 —
 * 설정 화면의 "서버 주소" 입력이 이 값을 기록한다.
 */
import { isNativeApp } from "@/platform/capacitor";
import { clearTokens } from "./tokenStore";
import { resolveServerOverride } from "@/features/settings/lib/serverAddress";

const OVERRIDE_KEY = "ct.apiBase";

function normalize(url: string): string {
  return url.trim().replace(/\/+$/, "");
}

function allowPrivateHttp(): boolean {
  return import.meta.env.DEV || import.meta.env.VITE_ALLOW_PRIVATE_HTTP === "true";
}

/** 저장된 런타임 오버라이드(없으면 null). 네이티브 앱 또는 dev 웹에서만 유효. */
export function apiBaseOverride(): string | null {
  if (!isNativeApp() && !import.meta.env.DEV) return null;
  try {
    const stored = localStorage.getItem(OVERRIDE_KEY);
    if (!stored?.trim()) return null;
    const validated = resolveServerOverride("custom", stored, allowPrivateHttp());
    if (validated.error || !validated.override) {
      // 이전 버전이 저장한 평문/비정상 주소로 기존 JWT가 전송되기 전에 함께 폐기한다.
      localStorage.removeItem(OVERRIDE_KEY);
      clearTokens();
      return null;
    }
    if (validated.override !== stored) localStorage.setItem(OVERRIDE_KEY, validated.override);
    return validated.override;
  } catch {
    return null;
  }
}

/** 런타임 오버라이드 설정(null/빈값 = 해제). 다음 요청부터 즉시 반영된다. */
export function setApiBaseOverride(url: string | null): void {
  try {
    if (url && url.trim()) {
      const validated = resolveServerOverride("custom", url, allowPrivateHttp());
      if (validated.error || !validated.override) {
        localStorage.removeItem(OVERRIDE_KEY);
        clearTokens();
        return;
      }
      localStorage.setItem(OVERRIDE_KEY, validated.override);
    } else {
      localStorage.removeItem(OVERRIDE_KEY);
    }
  } catch {
    /* 저장 불가 환경은 무시 */
  }
}

/** 현재 유효한 API 베이스. 오버라이드가 바뀔 수 있어 매 호출 시 평가한다. */
export function apiBase(): string {
  const override = apiBaseOverride();
  if (override) return override;
  const env = import.meta.env.VITE_API_BASE_URL as string | undefined;
  return env ? normalize(env) : "/api";
}
