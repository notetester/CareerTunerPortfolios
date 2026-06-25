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

/**
 * 앱 컨텍스트 판단 — 네이티브 앱이거나, 웹에서 ?home/?ob 로 앱을 미리보는 세션.
 * 웹 브라우저로도 앱 동작(로고·네비 분기 등)을 확인할 수 있게 한다.
 */
export function isAppContext(): boolean {
  if (isNativeApp()) return true;
  try {
    return sessionStorage.getItem("ct.appPreview") === "1";
  } catch {
    return false;
  }
}

/**
 * "홈으로" 가는 경로 — 앱은 검색창 메인(AppHome), 웹은 대시보드 홈.
 * 로고·"홈으로" 버튼 등 홈 진입점은 전부 이걸 써서 앱/웹을 자동 분기한다.
 */
export function homePath(): string {
  return isAppContext() ? "/?home" : "/home";
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
