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

/** iOS Safari 처럼 수동 안내가 필요한 환경인지. */
export function needsManualInstall(): boolean {
  return platformName() === "ios" && !isStandalone() && deferred === null;
}

export async function promptInstall(): Promise<"accepted" | "dismissed" | "unavailable"> {
  if (!deferred) return "unavailable";
  await deferred.prompt();
  const { outcome } = await deferred.userChoice;
  deferred = null;
  listeners.forEach((cb) => cb());
  return outcome;
}
