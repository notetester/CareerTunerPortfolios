/**
 * 플랫폼 감지 어댑터 (docs/planning/모바일 고려.md §5.3).
 * 웹/PWA/Capacitor(네이티브 WebView)를 구분해 플랫폼별 분기를 한 곳에 모은다.
 * 네이티브 플러그인은 하드 import 하지 않고 런타임에 window.Capacitor.Plugins 로 접근해,
 * 플러그인 미설치 환경(웹/PWA)에서도 무해하게 동작한다.
 */
import { Capacitor } from "@capacitor/core";

export function isNativeApp(): boolean {
  try {
    return Capacitor.isNativePlatform();
  } catch {
    return false;
  }
}

export function platformName(): "web" | "ios" | "android" {
  try {
    const p = Capacitor.getPlatform();
    return p === "ios" || p === "android" ? p : "web";
  } catch {
    return "web";
  }
}

/** 설치형(standalone) 실행 여부 — PWA 홈화면 실행 또는 네이티브 앱. */
export function isStandalone(): boolean {
  if (isNativeApp()) return true;
  if (typeof window === "undefined") return false;
  const mql = window.matchMedia?.("(display-mode: standalone)");
  // iOS Safari 는 navigator.standalone 사용.
  return Boolean(mql?.matches) || (window.navigator as unknown as { standalone?: boolean }).standalone === true;
}

/** 런타임에 주입된 Capacitor 네이티브 플러그인을 안전하게 가져온다(미설치 시 undefined). */
export function nativePlugin<T = unknown>(name: string): T | undefined {
  const cap = (window as unknown as { Capacitor?: { Plugins?: Record<string, unknown> } }).Capacitor;
  return cap?.Plugins?.[name] as T | undefined;
}
