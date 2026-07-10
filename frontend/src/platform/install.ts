/**
 * PWA 설치 안내 어댑터.
 * Android/Chrome 의 beforeinstallprompt 를 가로채 "홈 화면에 추가"를 사용자가 원할 때 띄운다.
 * iOS Safari 는 프로그램 설치 프롬프트가 없어 수동 안내(공유 → 홈 화면에 추가)를 보여준다.
 */
import { isStandalone, platformName } from "./capacitor";

interface BeforeInstallPromptEvent extends Event {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: "accepted" | "dismissed" }>;
}

let deferred: BeforeInstallPromptEvent | null = null;
const listeners = new Set<() => void>();

if (typeof window !== "undefined") {
  window.addEventListener("beforeinstallprompt", (e) => {
    e.preventDefault();
    deferred = e as BeforeInstallPromptEvent;
    listeners.forEach((cb) => cb());
  });
  window.addEventListener("appinstalled", () => {
    deferred = null;
    listeners.forEach((cb) => cb());
  });
}

export function subscribeInstall(cb: () => void): () => void {
  listeners.add(cb);
  return () => listeners.delete(cb);
}

/** Android/Chrome 처럼 프로그램 설치 프롬프트가 준비됐는지. */
export function canPromptInstall(): boolean {
  return deferred !== null && !isStandalone();
}

/** iOS Safari 처럼 수동 안내가 필요한 환경인지.
 * platformName() 은 네이티브 래퍼가 아닌 브라우저에선 항상 'web' 이라, 웹 iOS Safari 는 UA 로 판별한다
 * (기존 구현은 절대 참이 될 수 없는 데드 브랜치였음). */
export function needsManualInstall(): boolean {
  if (isStandalone() || deferred !== null) return false;
  if (platformName() === "ios") return true;
  const ua = typeof navigator !== "undefined" ? navigator.userAgent : "";
  const isIosBrowser = /iPhone|iPad|iPod/i.test(ua)
    || (/Macintosh/i.test(ua) && typeof navigator !== "undefined" && navigator.maxTouchPoints > 1); // iPadOS 데스크톱 UA
  return isIosBrowser;
}

export async function promptInstall(): Promise<"accepted" | "dismissed" | "unavailable"> {
  if (!deferred) return "unavailable";
  await deferred.prompt();
  const { outcome } = await deferred.userChoice;
  deferred = null;
  listeners.forEach((cb) => cb());
  return outcome;
}
