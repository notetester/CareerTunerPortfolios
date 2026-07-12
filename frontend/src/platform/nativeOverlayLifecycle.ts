/**
 * 네이티브 전체화면 오버레이의 수명주기 중재자.
 *
 * Android 하드웨어 뒤로가기는 React Router보다 먼저 최상단 오버레이가 소비하고,
 * 앱 비활성화(통화·화면 꺼짐·백그라운드) 때는 모든 민감 오버레이가 스트림을
 * 즉시 정리한다. 브라우저 popstate에는 연결하지 않아 웹의 뒤로가기 동작은 바꾸지 않는다.
 */

export interface NativeOverlayLifecycleHandler {
  onBack: () => void;
  onSuspend: () => void;
}

interface RegisteredNativeOverlay extends NativeOverlayLifecycleHandler {
  id: number;
  suspended: boolean;
}

let nextOverlayId = 1;
const overlayStack: RegisteredNativeOverlay[] = [];

/** 마운트 순서대로 쌓고, 해제 함수는 여러 번 호출해도 안전하다. */
export function registerNativeOverlayLifecycle(
  handler: NativeOverlayLifecycleHandler,
): () => void {
  const entry: RegisteredNativeOverlay = {
    ...handler,
    id: nextOverlayId++,
    suspended: false,
  };
  overlayStack.push(entry);

  return () => {
    const index = overlayStack.findIndex((candidate) => candidate.id === entry.id);
    if (index >= 0) overlayStack.splice(index, 1);
  };
}

/** 최상단 오버레이가 있으면 뒤로가기를 소비한다. */
export function consumeNativeOverlayBack(): boolean {
  const entry = overlayStack.at(-1);
  if (!entry || entry.suspended) return false;
  try {
    entry.onBack();
  } catch {
    /* 오버레이 콜백 실패가 native back listener까지 전파되지 않게 격리한다. */
  }
  return true;
}

/** 앱이 비활성화되는 즉시 민감 자원을 정리한다. 중복 이벤트는 한 번만 전달한다. */
export function suspendNativeOverlays(): void {
  for (const entry of [...overlayStack].reverse()) {
    if (entry.suspended) continue;
    entry.suspended = true;
    try {
      entry.onSuspend();
    } catch {
      /* 한 오버레이의 정리 실패가 다른 카메라·마이크 정리를 막으면 안 된다. */
    }
  }
}

/** 정적 계약 테스트가 모듈 전역 스택을 다음 케이스와 격리할 때만 사용한다. */
export function resetNativeOverlayLifecycleForTest(): void {
  overlayStack.splice(0, overlayStack.length);
}
