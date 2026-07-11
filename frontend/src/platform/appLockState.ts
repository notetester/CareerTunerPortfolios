let locked = false;
let generation = 0;

/** 상태가 실제로 바뀐 경우에만 세대를 증가시키고 true를 반환한다. */
export function updateAppLockState(nextLocked: boolean): boolean {
  if (locked === nextLocked) return false;
  locked = nextLocked;
  generation += 1;
  return true;
}

/** 잠금 중이면 새 민감 작업을 시작할 수 없으므로 null을 반환한다. */
export function captureAppLockGeneration(): number | null {
  return locked ? null : generation;
}

/** 작업을 시작한 뒤 잠금 또는 잠금 해제가 한 번이라도 끼어들었는지 검사한다. */
export function isAppLockGenerationCurrent(expected: number | null): boolean {
  return expected !== null && !locked && generation === expected;
}

/** 세대가 바뀐 민감 자원을 호출자가 즉시 폐기하도록 단일 분기에서 보장한다. */
export function guardAppLockGeneration(expected: number | null, discard: () => void): boolean {
  if (isAppLockGenerationCurrent(expected)) return true;
  discard();
  return false;
}
