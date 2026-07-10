/**
 * 딥링크 어댑터 (docs/planning/모바일 고려.md §5/§8).
 * Capacitor App 플러그인의 appUrlOpen(실행 중 수신) + getLaunchUrl(콜드 스타트)로
 * careertuner:// 커스텀 스킴과 https://careertuner.kro.kr App Link 를 앱 내 경로로 변환해 navigate 한다.
 * 웹/PWA 에서는 isNativeApp()=false 라 전부 건너뛰어 무해하다.
 * 공식 App 플러그인을 직접 import해 번들 등록과 네이티브 브리지 호출을 보장한다.
 */
import { App } from "@capacitor/app";
import { isNativeApp } from "./capacitor";

/** 앱이 처리하는 App Link 호스트 — AndroidManifest 의 intent-filter 와 동일해야 한다. */
const APP_LINK_HOSTS = ["careertuner.kro.kr", "careertuner.kr", "www.careertuner.kr"];

/**
 * 딥링크 URL → 앱 내 경로 변환.
 * - careertuner://applications/3 → /applications/3
 * - https://careertuner.kro.kr/community?tab=hot#top → /community?tab=hot#top
 * 변환할 수 없는 URL(다른 호스트/스킴)은 null — 호출부가 무시한다.
 */
export function toAppPath(rawUrl: string): string | null {
  if (!rawUrl) return null;
  // 커스텀 스킴 — new URL 은 커스텀 스킴의 host/path 해석이 브라우저마다 달라 직접 파싱한다.
  const custom = /^careertuner:\/\/(.*)$/i.exec(rawUrl);
  if (custom) {
    const rest = custom[1].replace(/^\/+/, "");
    return `/${rest}`;
  }
  try {
    const url = new URL(rawUrl);
    if (url.protocol !== "https:" && url.protocol !== "http:") return null;
    if (!APP_LINK_HOSTS.includes(url.hostname.toLowerCase())) return null;
    return `${url.pathname}${url.search}${url.hash}` || "/";
  } catch {
    return null;
  }
}

let initialized = false;

/**
 * 앱 시작 시 1회 호출(라우터 준비 후). 네이티브가 아니면 즉시 반환.
 * navigate 는 앱 내 경로("/...")를 받는 콜백 — App.tsx 가 데이터 라우터의 navigate 를 넘긴다.
 */
export function initDeepLinks(navigate: (path: string) => void): void {
  if (initialized || !isNativeApp()) return;
  initialized = true;

  // 실행 중 딥링크 수신(백그라운드 → 포그라운드 포함).
  try {
    void App.addListener("appUrlOpen", ({ url }) => {
      const path = toAppPath(url ?? "");
      if (path) navigate(path);
    }).catch(() => {});
  } catch {
    /* 딥링크는 보조라 실패해도 무시 */
  }

  // 콜드 스타트 — 딥링크로 앱이 처음 실행된 경우 시작 URL 을 한 번 처리한다.
  try {
    void App
      .getLaunchUrl()
      .then((data) => {
        const path = toAppPath(data?.url ?? "");
        if (path) navigate(path);
      })
      .catch(() => {
        /* no-op */
      });
  } catch {
    /* no-op */
  }
}
