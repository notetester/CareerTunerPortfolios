import { useEffect, useRef } from "react";
import {
  guardAppLockGeneration,
  isAppLockGenerationCurrent,
  updateAppLockState,
} from "./appLockState";

export { captureAppLockGeneration, isAppLockGenerationCurrent } from "./appLockState";

const APP_LOCK_STATE_EVENT = "careertuner:app-lock-state";

/** 라우트 상태는 유지하면서 카메라·마이크 같은 민감 자원만 즉시 정리하도록 알린다. */
export function emitAppLockState(locked: boolean): void {
  if (!updateAppLockState(locked) || typeof window === "undefined") return;
  window.dispatchEvent(new CustomEvent(APP_LOCK_STATE_EVENT, { detail: { locked } }));
}

/** 비동기 획득 직후 세대가 바뀌었으면 새 스트림을 즉시 폐기한다. */
export function keepStreamForAppLock(stream: MediaStream, expected: number | null): boolean {
  return guardAppLockGeneration(expected, () => {
    stream.getTracks().forEach((track) => track.stop());
  });
}

export function onAppLockState(listener: (locked: boolean) => void): () => void {
  if (typeof window === "undefined") return () => {};
  const handler = (event: Event) => {
    const locked = (event as CustomEvent<{ locked?: unknown }>).detail?.locked;
    if (typeof locked === "boolean") listener(locked);
  };
  window.addEventListener(APP_LOCK_STATE_EVENT, handler);
  return () => window.removeEventListener(APP_LOCK_STATE_EVENT, handler);
}

/** cleanup 함수가 매 렌더 바뀌어도 구독은 한 번만 유지한다. */
export function useAppLockCleanup(cleanup: () => void): void {
  const cleanupRef = useRef(cleanup);
  cleanupRef.current = cleanup;
  useEffect(() => onAppLockState((locked) => {
    if (locked) cleanupRef.current();
  }), []);
}
