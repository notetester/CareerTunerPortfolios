/**
 * 네이티브 셸 초기화 (docs/planning/모바일 고려.md §5/§8).
 * Capacitor 앱에서만 동작 — 상태바·스플래시·키보드·하드웨어 뒤로가기를 설정한다.
 * 웹/PWA 에서는 isNativeApp()=false 라 전부 건너뛰어 무해하다.
 * 공식 Capacitor 플러그인을 직접 import해 번들 등록과 네이티브 브리지 호출을 보장한다.
 */
import { App } from "@capacitor/app";
import { Keyboard, KeyboardResize } from "@capacitor/keyboard";
import { SplashScreen } from "@capacitor/splash-screen";
import { StatusBar, Style } from "@capacitor/status-bar";
import { isNativeApp } from "./capacitor";

let initialized = false;

/** 현재 다크모드 여부(테마 토글이 html.dark 를 토글) — 상태바 대비색 결정에 사용. */
function isDarkTheme(): boolean {
  return typeof document !== "undefined" && document.documentElement.classList.contains("dark");
}

/** 테마에 맞춰 상태바 색/글자 대비를 맞춘다. 다크: 어두운 배경+밝은 글자, 라이트: 흰 배경+어두운 글자. */
export function syncStatusBarTheme(): void {
  if (!isNativeApp()) return;
  try {
    const dark = isDarkTheme();
    // Capacitor Style: LIGHT=밝은 배경(어두운 글자), DARK=어두운 배경(밝은 글자).
    void StatusBar.setStyle({ style: dark ? Style.Dark : Style.Light }).catch(() => {});
    void StatusBar.setBackgroundColor({ color: dark ? "#050506" : "#ffffff" }).catch(() => {}); // 다크 = Linear Modern 딥블랙(데스크탑 v2와 통일)
  } catch {
    /* 상태바는 보조라 실패해도 무시 */
  }
}

/** 앱 시작 시 1회 호출. 네이티브가 아니면 즉시 반환. */
export function initNativeShell(): void {
  if (initialized || !isNativeApp()) return;
  initialized = true;

  // 광고 등 플랫폼 타겟 소비자(AdSlot 의 __CT_PLATFORM__)가 네이티브 앱을 식별하도록 전역 플래그를 세팅한다
  // — 미세팅이면 앱이 항상 platform=WEB 으로 요청해 APP 타겟 광고가 어디서도 노출되지 않는다.
  (globalThis as { __CT_PLATFORM__?: string }).__CT_PLATFORM__ = "APP";

  // 상태바: 웹뷰가 상태바 밑으로 깔리지 않게 오버레이를 끄고, 테마색을 맞춘다.
  try {
    void StatusBar.setOverlaysWebView({ overlay: false }).catch(() => {});
  } catch {
    /* no-op */
  }
  syncStatusBarTheme();

  // 스플래시: 첫 페인트 후 숨김(빈 화면 깜빡임 방지).
  try {
    void SplashScreen.hide({ fadeOutDuration: 200 }).catch(() => {});
  } catch {
    /* no-op */
  }

  // 키보드: 입력창 포커스 시 웹뷰 리사이즈, iOS 보조 액세서리 바 숨김.
  try {
    void Keyboard.setResizeMode({ mode: KeyboardResize.Native }).catch(() => {});
    void Keyboard.setAccessoryBarVisible({ isVisible: false }).catch(() => {});
  } catch {
    /* no-op */
  }

  // 하드웨어 뒤로가기(Android): 갈 곳이 있으면 뒤로, 루트면 앱 종료.
  try {
    void App.addListener("backButton", ({ canGoBack }) => {
      const goBack = canGoBack ?? (typeof window !== "undefined" && window.history.length > 1);
      if (goBack) {
        window.history.back();
      } else {
        void App.exitApp().catch(() => {});
      }
    }).catch(() => {});
  } catch {
    /* no-op */
  }
}
