/**
 * 네이티브 셸 초기화 (docs/planning/모바일 고려.md §5/§8).
 * Capacitor 앱에서만 동작 — 상태바·스플래시·키보드·하드웨어 뒤로가기를 설정한다.
 * 웹/PWA 에서는 isNativeApp()=false 라 전부 건너뛰어 무해하다.
 * 플러그인은 하드 import 하지 않고 런타임 window.Capacitor.Plugins 로 접근한다(haptics/push 와 동일 패턴).
 */
import { isNativeApp, nativePlugin } from "./capacitor";

let initialized = false;

interface CapStatusBar {
  setStyle: (opts: { style: string }) => Promise<void>;
  setBackgroundColor: (opts: { color: string }) => Promise<void>;
  setOverlaysWebView: (opts: { overlay: boolean }) => Promise<void>;
}
interface CapSplash {
  hide: (opts?: { fadeOutDuration?: number }) => Promise<void>;
}
interface CapKeyboard {
  setResizeMode: (opts: { mode: string }) => Promise<void>;
  setAccessoryBarVisible?: (opts: { isVisible: boolean }) => Promise<void>;
}
interface CapApp {
  addListener: (event: string, cb: (data: { canGoBack?: boolean }) => void) => void;
  exitApp: () => Promise<void>;
}

/** 현재 다크모드 여부(테마 토글이 html.dark 를 토글) — 상태바 대비색 결정에 사용. */
function isDarkTheme(): boolean {
  return typeof document !== "undefined" && document.documentElement.classList.contains("dark");
}

/** 테마에 맞춰 상태바 색/글자 대비를 맞춘다. 다크: 어두운 배경+밝은 글자, 라이트: 흰 배경+어두운 글자. */
export function syncStatusBarTheme(): void {
  if (!isNativeApp()) return;
  try {
    const sb = nativePlugin<CapStatusBar>("StatusBar");
    if (!sb) return;
    const dark = isDarkTheme();
    // Capacitor Style: LIGHT=밝은 배경(어두운 글자), DARK=어두운 배경(밝은 글자).
    void sb.setStyle({ style: dark ? "DARK" : "LIGHT" });
    void sb.setBackgroundColor({ color: dark ? "#0f172a" : "#ffffff" });
  } catch {
    /* 상태바는 보조라 실패해도 무시 */
  }
}

/** 앱 시작 시 1회 호출. 네이티브가 아니면 즉시 반환. */
export function initNativeShell(): void {
  if (initialized || !isNativeApp()) return;
  initialized = true;

  // 상태바: 웹뷰가 상태바 밑으로 깔리지 않게 오버레이를 끄고, 테마색을 맞춘다.
  try {
    const sb = nativePlugin<CapStatusBar>("StatusBar");
    if (sb) void sb.setOverlaysWebView({ overlay: false });
  } catch {
    /* no-op */
  }
  syncStatusBarTheme();

  // 스플래시: 첫 페인트 후 숨김(빈 화면 깜빡임 방지).
  try {
    const splash = nativePlugin<CapSplash>("SplashScreen");
    if (splash) void splash.hide({ fadeOutDuration: 200 });
  } catch {
    /* no-op */
  }

  // 키보드: 입력창 포커스 시 웹뷰 리사이즈, iOS 보조 액세서리 바 숨김.
  try {
    const kb = nativePlugin<CapKeyboard>("Keyboard");
    if (kb) {
      void kb.setResizeMode({ mode: "native" });
      void kb.setAccessoryBarVisible?.({ isVisible: false });
    }
  } catch {
    /* no-op */
  }

  // 하드웨어 뒤로가기(Android): 갈 곳이 있으면 뒤로, 루트면 앱 종료.
  try {
    const app = nativePlugin<CapApp>("App");
    if (app) {
      app.addListener("backButton", ({ canGoBack }) => {
        const goBack = canGoBack ?? (typeof window !== "undefined" && window.history.length > 1);
        if (goBack) {
          window.history.back();
        } else {
          void app.exitApp();
        }
      });
    }
  } catch {
    /* no-op */
  }
}
