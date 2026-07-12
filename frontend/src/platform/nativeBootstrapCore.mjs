const DEFAULT_NATIVE_READY_DELAYS_MS = Object.freeze([0, 50, 100, 250, 500, 1_000, 2_000, 4_000]);

const waitFor = (delayMs) => new Promise((resolve) => setTimeout(resolve, delayMs));

/** Capacitor가 WebView에 플랫폼 정보를 주입할 때까지 제한된 횟수만 기다린다. */
export async function waitForNativePlatform({
  isNative,
  retryDelaysMs = DEFAULT_NATIVE_READY_DELAYS_MS,
  wait = waitFor,
}) {
  for (const delayMs of retryDelaysMs) {
    if (delayMs > 0) await wait(delayMs);
    try {
      if (isNative()) return true;
    } catch {
      // 브리지 주입 도중의 일시 오류는 다음 시도에서 복구한다.
    }
  }
  return false;
}

/** 플랫폼 준비 뒤 shell/deep-link/push 같은 네이티브 초기화기를 함께 한 번 호출한다. */
export async function bootstrapNativeRuntime({
  isNative,
  initializers,
  retryDelaysMs = DEFAULT_NATIVE_READY_DELAYS_MS,
  wait = waitFor,
}) {
  const ready = await waitForNativePlatform({ isNative, retryDelaysMs, wait });
  if (!ready) return false;

  for (const initialize of initializers) {
    try {
      initialize();
    } catch {
      // 한 보조 기능의 동기 실패가 나머지 네이티브 초기화를 막지 않게 격리한다.
    }
  }
  return true;
}

export const nativeReadyRetryDelaysMs = DEFAULT_NATIVE_READY_DELAYS_MS;
