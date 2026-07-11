import { useSyncExternalStore } from "react";

export type DataMode = "real" | "static-demo" | "outage-demo" | "restoring";

export interface OutageFallbackSnapshot {
  mode: DataMode;
  checking: boolean;
  lastCheckedAt: number | null;
}

const STATIC_MOCK = import.meta.env.VITE_USE_MOCK === "true";
const OUTAGE_FALLBACK_ENABLED =
  !STATIC_MOCK && import.meta.env.VITE_ENABLE_OUTAGE_FALLBACK === "true";
const HEALTH_PATH = "/__backup/health";
const HEALTH_TIMEOUT_MS = 5_000;
const RECOVERY_POLL_MS = 12_000;

let snapshot: OutageFallbackSnapshot = {
  mode: STATIC_MOCK ? "static-demo" : "real",
  checking: false,
  lastCheckedAt: null,
};
const listeners = new Set<() => void>();
let healthPromise: Promise<boolean> | null = null;
let reloadTimer: number | null = null;

function publish(patch: Partial<OutageFallbackSnapshot>): void {
  const next = { ...snapshot, ...patch };
  if (
    next.mode === snapshot.mode &&
    next.checking === snapshot.checking &&
    next.lastCheckedAt === snapshot.lastCheckedAt
  ) {
    return;
  }
  snapshot = next;
  listeners.forEach((listener) => listener());
}

function enterOutageDemo(): void {
  if (!OUTAGE_FALLBACK_ENABLED || snapshot.mode === "outage-demo" || snapshot.mode === "restoring") {
    return;
  }
  publish({ mode: "outage-demo" });
}

function scheduleRealModeReload(): void {
  if (typeof window === "undefined" || reloadTimer != null) return;
  reloadTimer = window.setTimeout(() => window.location.reload(), 900);
}

async function requestUpstreamHealth(): Promise<boolean> {
  if (!OUTAGE_FALLBACK_ENABLED) return true;
  if (healthPromise) return healthPromise;

  publish({ checking: true });
  healthPromise = (async () => {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), HEALTH_TIMEOUT_MS);
    try {
      const response = await fetch(HEALTH_PATH, {
        method: "GET",
        headers: { Accept: "application/json" },
        cache: "no-store",
        credentials: "omit",
        signal: controller.signal,
      });
      if (!response.ok) return false;
      const body = (await response.json().catch(() => null)) as {
        status?: unknown;
        upstreamStatus?: unknown;
      } | null;
      return (
        body?.status === "UP" &&
        typeof body.upstreamStatus === "number" &&
        body.upstreamStatus >= 200 &&
        body.upstreamStatus < 300
      );
    } catch {
      return false;
    } finally {
      clearTimeout(timeout);
    }
  })().finally(() => {
    healthPromise = null;
    publish({ checking: false, lastCheckedAt: Date.now() });
  });

  return healthPromise;
}

export function getOutageFallbackSnapshot(): OutageFallbackSnapshot {
  return snapshot;
}

export function subscribeOutageFallback(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function useOutageFallback(): OutageFallbackSnapshot & {
  socialOAuthBlocked: boolean;
  sitesPaymentBlocked: boolean;
} {
  const current = useSyncExternalStore(
    subscribeOutageFallback,
    getOutageFallbackSnapshot,
    getOutageFallbackSnapshot,
  );
  return {
    ...current,
    socialOAuthBlocked: current.mode === "outage-demo" || current.mode === "restoring",
    sitesPaymentBlocked: OUTAGE_FALLBACK_ENABLED,
  };
}

export function isOutageFallbackActive(): boolean {
  return snapshot.mode === "outage-demo" || snapshot.mode === "restoring";
}

export function shouldUseMockData(): boolean {
  return snapshot.mode !== "real";
}

/** 소셜 OAuth는 실제 AWS 장애가 확인된 동안에만 막는다. */
export function isSocialOAuthBlocked(): boolean {
  return isOutageFallbackActive();
}

/** Sites는 금융 거래를 제공하지 않으므로 정상 연결 상태에서도 결제를 막는다. */
export function isSitesPaymentBlocked(): boolean {
  return OUTAGE_FALLBACK_ENABLED;
}

export function isOutageStatus(status: number): boolean {
  return status === 502 || status === 503 || status === 504;
}

export function isNetworkOutageError(error: unknown): boolean {
  if (error instanceof DOMException && error.name === "AbortError") return false;
  return error instanceof TypeError;
}

/** 실제 요청의 장애 후보가 전체 AWS 장애인지 health로 한 번 더 확인한다. */
export async function activateOutageFallbackIfConfirmed(): Promise<boolean> {
  if (!OUTAGE_FALLBACK_ENABLED) return false;
  if (isOutageFallbackActive()) return true;

  const healthy = await requestUpstreamHealth();
  if (healthy) return false;
  enterOutageDemo();
  return true;
}

/** 배너의 수동 확인과 자동 polling이 공유하는 복구 확인. */
export async function probeOutageRecovery(): Promise<boolean> {
  if (!OUTAGE_FALLBACK_ENABLED) return snapshot.mode === "real";
  const healthy = await requestUpstreamHealth();
  if (!healthy) {
    if (snapshot.mode === "real") enterOutageDemo();
    return false;
  }

  if (snapshot.mode === "outage-demo") {
    publish({ mode: "restoring" });
    scheduleRealModeReload();
  }
  return true;
}

/** real-first 요청이 장애 모드를 활성화한 뒤에만 복구 polling과 재연결 이벤트를 담당한다. */
export function startOutageFallbackMonitor(): () => void {
  if (!OUTAGE_FALLBACK_ENABLED || typeof window === "undefined") return () => {};

  let disposed = false;
  const check = () => {
    if (disposed) return;
    void probeOutageRecovery();
  };

  const interval = window.setInterval(() => {
    if (snapshot.mode === "outage-demo") check();
  }, RECOVERY_POLL_MS);
  const onVisible = () => {
    if (document.visibilityState === "visible" && snapshot.mode === "outage-demo") check();
  };
  const onOnline = () => {
    if (snapshot.mode === "outage-demo") check();
  };
  window.addEventListener("online", onOnline);
  window.addEventListener("focus", onOnline);
  document.addEventListener("visibilitychange", onVisible);

  return () => {
    disposed = true;
    window.clearInterval(interval);
    window.removeEventListener("online", onOnline);
    window.removeEventListener("focus", onOnline);
    document.removeEventListener("visibilitychange", onVisible);
  };
}
