/**
 * 앱 잠금(PIN/생체) — 기기 로컬 보안 잠금.
 * 서버 인증과 별개로, 앱 실행/복귀 시 PIN(또는 생체)으로 한 번 더 잠근다(분실/공용기기 대비).
 * PIN 은 평문 저장하지 않고 SHA-256 해시만 로컬에 둔다. 생체는 네이티브 플러그인이 있을 때만 동작.
 */
import { isNativeApp, nativePlugin } from "./capacitor";

const PIN_KEY = "careertuner.applock.pinHash";
const BIO_KEY = "careertuner.applock.bio";
const AUTOLOCK_GRACE_MS = 30_000; // 30초 이내 복귀는 재잠금하지 않음(편의)

async function sha256(text: string): Promise<string> {
  // http LAN 주소 등 비보안 컨텍스트에서는 crypto.subtle 이 undefined 라 PIN 설정이 조용히 무반응이 된다
  // — 원인을 알 수 있는 에러로 바꿔 호출부(설정 화면)의 에러 표시에 태운다.
  if (!globalThis.crypto?.subtle) {
    throw new Error("보안 연결(https 또는 localhost)에서만 앱 잠금을 설정할 수 있습니다.");
  }
  const buf = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return Array.from(new Uint8Array(buf)).map((b) => b.toString(16).padStart(2, "0")).join("");
}

export function hasPin(): boolean {
  return Boolean(localStorage.getItem(PIN_KEY));
}

export async function setPin(pin: string): Promise<void> {
  localStorage.setItem(PIN_KEY, await sha256(pin));
}

export async function verifyPin(pin: string): Promise<boolean> {
  const stored = localStorage.getItem(PIN_KEY);
  return stored != null && stored === (await sha256(pin));
}

export function clearLock(): void {
  localStorage.removeItem(PIN_KEY);
  localStorage.removeItem(BIO_KEY);
}

export function biometricEnabled(): boolean {
  return localStorage.getItem(BIO_KEY) === "1";
}

export function setBiometricEnabled(on: boolean): void {
  if (on) localStorage.setItem(BIO_KEY, "1");
  else localStorage.removeItem(BIO_KEY);
}

/** 네이티브 생체 플러그인 존재 여부(런타임). 없으면 PIN 만 사용. */
export function biometricAvailable(): boolean {
  return isNativeApp() && (nativePlugin("BiometricAuth") != null || nativePlugin("NativeBiometric") != null);
}

interface BioPlugin {
  authenticate?: (opts?: Record<string, unknown>) => Promise<unknown>;
  verifyIdentity?: (opts?: Record<string, unknown>) => Promise<unknown>;
}

/** 생체 인증 시도. 성공 true. 플러그인/지원 없으면 false. */
export async function tryBiometric(): Promise<boolean> {
  const plugin = nativePlugin<BioPlugin>("BiometricAuth") ?? nativePlugin<BioPlugin>("NativeBiometric");
  if (!plugin) return false;
  try {
    if (plugin.authenticate) await plugin.authenticate({ reason: "CareerTuner 잠금 해제" });
    else if (plugin.verifyIdentity) await plugin.verifyIdentity({ reason: "CareerTuner 잠금 해제" });
    else return false;
    return true;
  } catch {
    return false;
  }
}

export const AUTOLOCK_GRACE = AUTOLOCK_GRACE_MS;
