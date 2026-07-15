export const STALE_CHUNK_RELOAD_STORAGE_KEY = "careertuner:stale-chunk-reload-at";
export const STALE_CHUNK_RELOAD_COOLDOWN_MS = 60_000;
export const STALE_CHUNK_RELOAD_HISTORY_STATE_KEY = "__careertunerStaleChunkReloadAt";

interface ReloadAttemptStorage {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
}

interface HistoryStateStore {
  readonly state: unknown;
  replaceState(data: unknown, unused: string): void;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

function getSessionStorage(): ReloadAttemptStorage | null | undefined {
  try {
    return typeof window !== "undefined" ? window.sessionStorage : null;
  } catch {
    return null;
  }
}

/** sessionStorage를 쓸 수 없는 WebView에서도 reload를 건너 유지되는 history.state 저장소. */
export function createHistoryStateAdapter(
  history: HistoryStateStore | null = typeof window !== "undefined" ? window.history : null,
): ReloadAttemptStorage | null {
  if (!history) return null;

  return {
    getItem(key) {
      if (key !== STALE_CHUNK_RELOAD_STORAGE_KEY) return null;
      const state = history.state;
      if (!isRecord(state)) return null;
      const value = state[STALE_CHUNK_RELOAD_HISTORY_STATE_KEY];
      return typeof value === "string" ? value : null;
    },
    setItem(key, value) {
      if (key !== STALE_CHUNK_RELOAD_STORAGE_KEY) return;
      const currentState = history.state;
      const preservedState = isRecord(currentState) ? currentState : {};
      history.replaceState(
        { ...preservedState, [STALE_CHUNK_RELOAD_HISTORY_STATE_KEY]: value },
        "",
      );
    },
  };
}

function readReloadAttempt(adapter: ReloadAttemptStorage | null | undefined): number {
  try {
    const storedAttempt = adapter?.getItem(STALE_CHUNK_RELOAD_STORAGE_KEY);
    return storedAttempt == null ? Number.NaN : Number(storedAttempt);
  } catch {
    return Number.NaN;
  }
}

function writeReloadAttempt(
  adapter: ReloadAttemptStorage | null | undefined,
  attemptedAt: number,
): void {
  try {
    adapter?.setItem(STALE_CHUNK_RELOAD_STORAGE_KEY, String(attemptedAt));
  } catch {
    // 한 저장소가 막혀도 다른 저장소 adapter에 기록을 계속 시도한다.
  }
}

/**
 * 배포로 이전 해시의 lazy chunk가 사라졌을 때 최신 index를 한 번 다시 받는다.
 * 최근 시각을 sessionStorage와 history.state에 남겨 reload 뒤 같은 오류가 반복되어도 루프에 빠지지 않는다.
 */
export function installStaleChunkReload(
  eventTarget = typeof window !== "undefined" ? window : null,
  storage = getSessionStorage(),
  reload = () => window.location.reload(),
  now = () => Date.now(),
  historyState = createHistoryStateAdapter(),
) {
  if (!eventTarget) return;

  let reloadStarted = false;

  eventTarget.addEventListener("vite:preloadError", (event) => {
    event.preventDefault();
    if (reloadStarted) return;

    const previousAttempts = [
      readReloadAttempt(storage),
      readReloadAttempt(historyState),
    ].filter((attempt) => Number.isFinite(attempt));
    const previousAttempt = previousAttempts.length > 0
      ? Math.max(...previousAttempts)
      : Number.NaN;

    const attemptedAt = now();
    if (
      Number.isFinite(previousAttempt)
      && attemptedAt - previousAttempt < STALE_CHUNK_RELOAD_COOLDOWN_MS
    ) {
      return;
    }

    reloadStarted = true;
    writeReloadAttempt(storage, attemptedAt);
    writeReloadAttempt(historyState, attemptedAt);
    reload();
  });
}

export function installServiceWorkerUpdateReload(
  serviceWorker = typeof navigator !== "undefined" && "serviceWorker" in navigator
    ? navigator.serviceWorker
    : null,
  reload = () => window.location.reload(),
) {
  if (!serviceWorker) return;

  let controller = serviceWorker.controller;
  let reloadStarted = false;

  serviceWorker.addEventListener("controllerchange", () => {
    const nextController = serviceWorker.controller;
    if (!nextController || nextController === controller) return;

    if (!controller) {
      controller = nextController;
      return;
    }
    controller = nextController;
    if (reloadStarted) return;

    reloadStarted = true;
    reload();
  });
}
