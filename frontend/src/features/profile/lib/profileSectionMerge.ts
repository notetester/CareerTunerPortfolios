export class ProfileSectionConflictError extends Error {
  readonly fields: string[];

  constructor(fields: string[]) {
    super(`다른 화면에서 같은 프로필 항목을 먼저 변경했습니다: ${fields.join(", ")}`);
    this.name = "ProfileSectionConflictError";
    this.fields = fields;
  }
}

/** JSON 계약으로 전달되는 프로필 값의 구조적 동등성 비교. 객체 키 순서에는 의존하지 않는다. */
export function profileValueEquals(left: unknown, right: unknown): boolean {
  if (Object.is(left, right)) return true;
  if (Array.isArray(left) || Array.isArray(right)) {
    return Array.isArray(left)
      && Array.isArray(right)
      && left.length === right.length
      && left.every((value, index) => profileValueEquals(value, right[index]));
  }
  if (left && right && typeof left === "object" && typeof right === "object") {
    const leftRecord = left as Record<string, unknown>;
    const rightRecord = right as Record<string, unknown>;
    const leftKeys = Object.keys(leftRecord).sort();
    const rightKeys = Object.keys(rightRecord).sort();
    return leftKeys.length === rightKeys.length
      && leftKeys.every((key, index) => key === rightKeys[index]
        && profileValueEquals(leftRecord[key], rightRecord[key]));
  }
  return false;
}

/**
 * section 최초 기준본, 저장 직전 최신 서버본, 현재 로컬 patch를 3-way 병합한다.
 * 로컬과 서버가 같은 profile field를 서로 다르게 바꾼 경우에는 자동 덮어쓰기 대신 충돌로 닫는다.
 */
export function mergeProfileSectionPatch<T extends object>(
  baseline: T,
  latest: T,
  localPatch: Partial<T>,
): T {
  const merged = { ...latest } as T;
  const conflicts: string[] = [];

  for (const key of Object.keys(localPatch) as Array<keyof T>) {
    const baselineValue = baseline[key];
    const latestValue = latest[key];
    const localValue = localPatch[key];
    const localChanged = !profileValueEquals(localValue, baselineValue);
    const serverChanged = !profileValueEquals(latestValue, baselineValue);

    if (localChanged && serverChanged && !profileValueEquals(localValue, latestValue)) {
      conflicts.push(String(key));
      continue;
    }
    if (localChanged) merged[key] = localValue as T[keyof T];
  }

  if (conflicts.length) throw new ProfileSectionConflictError(conflicts);
  return merged;
}

/**
 * 요청이 진행되는 동안 새로 입력된 draft만 서버 응답 위에 보존한다.
 * 요청 시작 전에 있던 값은 서버 응답으로 교체하고, 시작 이후 바뀐 최상위 draft field는 로컬에 남긴다.
 */
export function rebaseDraftAfterCommit<T extends object>(
  requestStart: T,
  current: T,
  committed: T,
): { draft: T; conflicts: string[] } {
  const conflicts: string[] = [];
  const draft = mergeDraftValue(requestStart, current, committed, "draft", conflicts) as T;
  return { draft, conflicts };
}

function mergeDraftValue(
  requestStart: unknown,
  current: unknown,
  committed: unknown,
  path: string,
  conflicts: string[],
): unknown {
  if (profileValueEquals(current, requestStart)) return committed;
  if (profileValueEquals(committed, requestStart) || profileValueEquals(current, committed)) return current;

  if (isPlainRecord(requestStart) && isPlainRecord(current) && isPlainRecord(committed)) {
    const merged: Record<string, unknown> = {};
    const keys = new Set([...Object.keys(requestStart), ...Object.keys(current), ...Object.keys(committed)]);
    for (const key of keys) {
      merged[key] = mergeDraftValue(
        requestStart[key],
        current[key],
        committed[key],
        `${path}.${key}`,
        conflicts,
      );
    }
    return merged;
  }

  // 배열 순서와 자유 텍스트는 부분 병합으로 의미가 달라질 수 있어 원자값으로 취급한다.
  // 현재 입력을 화면에 보존하되 후속 PUT은 충돌 해소 전까지 닫는다.
  conflicts.push(path);
  return current;
}

function isPlainRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}
