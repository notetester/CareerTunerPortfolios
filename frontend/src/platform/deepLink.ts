/**
 * 딥링크 어댑터 (docs/planning/모바일 고려.md §5/§8).
 * Capacitor App 플러그인의 appUrlOpen(실행 중 수신) + getLaunchUrl(콜드 스타트)로
 * 일반 앱 경로용 careertuner:// 커스텀 스킴과 네이티브 OAuth/소셜 계정 연결 결과 전용
 * https://careertuner.kro.kr verified App Link를 앱 내 경로로 변환해 navigate 한다.
 * 웹/PWA 에서는 isNativeApp()=false 라 전부 건너뛰어 무해하다.
 * 공식 App 플러그인을 직접 import해 번들 등록과 네이티브 브리지 호출을 보장한다.
 */
import { App } from "@capacitor/app";
import { isNativeApp, nativePlugin } from "./capacitor";
import {
  isNativeOAuthCallbackPath,
  isNativeSocialLinkCallbackPath,
  toAppPath as parseAppPath,
} from "./deepLinkCore.mjs";
import { closeNativeOAuthBrowser } from "./nativeOAuth";
import {
  initializeDeepLinkRuntime,
  type DeepLinkAppPlugin,
} from "./deepLinkRuntimeCore.mjs";

export { toAppPath } from "./deepLinkCore.mjs";

/**
 * 딥링크 URL → 앱 내 경로 변환.
 * - careertuner://applications/3 → /applications/3
 * - https://careertuner.kro.kr/auth/callback?handoffCode=... → /auth/callback?handoffCode=...
 * - https://careertuner.kro.kr/profile/detail?socialLinked=KAKAO → /profile/detail?socialLinked=KAKAO
 * 변환할 수 없는 URL(다른 호스트/스킴)은 null — 호출부가 무시한다.
 */
let initialized = false;

function routeDeepLink(rawUrl: string, navigate: (path: string) => void): void {
  const path = parseAppPath(rawUrl);
  if (!path) return;
  if (isNativeOAuthCallbackPath(path) || isNativeSocialLinkCallbackPath(path)) {
    void closeNativeOAuthBrowser();
  }
  navigate(path);
}

/**
 * 앱 시작 시 1회 호출(라우터 준비 후). 네이티브가 아니면 즉시 반환.
 * navigate 는 앱 내 경로("/...")를 받는 콜백 — App.tsx 가 데이터 라우터의 navigate 를 넘긴다.
 */
export function initDeepLinks(navigate: (path: string) => void): void {
  if (initialized || !isNativeApp()) return;
  initialized = true;

  // 실제 WebView에 등록된 플러그인을 우선 사용한다. 첫 렌더 시 bridge가 아직 준비되지 않았으면
  // 제한된 재시도로 실행 중 리스너와 콜드 시작 URL을 각각 복구한다.
  const runtimeApp = nativePlugin<DeepLinkAppPlugin>("App") ?? (App as unknown as DeepLinkAppPlugin);
  void initializeDeepLinkRuntime({
    appPlugin: runtimeApp,
    onUrl: (url) => routeDeepLink(url, navigate),
  }).catch(() => {
    // 예상하지 못한 초기화 실패는 다음 명시적 초기화 호출에서 복구할 수 있게 한다.
    initialized = false;
  });
}
