/**
 * 네이티브 앱이 브라우저 history 없이 바로 종료될 때 확인해야 하는 화면 상태를 등록한다.
 *
 * history가 있는 뒤로가기는 React Router blocker가 처리한다. 이 가드는 cold start/deep link처럼
 * Capacitor의 `canGoBack=false`인 경우에만 nativeShell이 조회해 미저장 입력을 우회한 종료를 막는다.
 */

export type NativeExitGuard = () => boolean;

interface RegisteredNativeExitGuard {
  id: number;
  allowExit: NativeExitGuard;
}

let nextGuardId = 1;
const guards: RegisteredNativeExitGuard[] = [];

/** 마지막에 마운트된 화면의 종료 가드를 우선한다. 반환한 해제 함수는 여러 번 호출해도 안전하다. */
export function registerNativeExitGuard(allowExit: NativeExitGuard): () => void {
  const entry = { id: nextGuardId++, allowExit };
  guards.push(entry);
  return () => {
    const index = guards.findIndex((candidate) => candidate.id === entry.id);
    if (index >= 0) guards.splice(index, 1);
  };
}

/** 등록된 가드가 없으면 종료를 허용하고, 가드 자체가 실패하면 데이터 보존 쪽으로 닫는다. */
export function allowNativeAppExit(): boolean {
  const entry = guards.at(-1);
  if (!entry) return true;
  try {
    return entry.allowExit();
  } catch {
    return false;
  }
}

/** 정적 계약 테스트에서 모듈 전역 상태를 케이스 사이에 격리할 때만 사용한다. */
export function resetNativeExitGuardsForTest(): void {
  guards.splice(0, guards.length);
}
