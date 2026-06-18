import { useCallback, useEffect, useRef, useState, type ReactNode } from "react";
import { Lock, Fingerprint, Delete } from "lucide-react";
import {
  AUTOLOCK_GRACE, biometricAvailable, biometricEnabled, hasPin, tryBiometric, verifyPin,
} from "@/platform/applock";

/**
 * 앱 잠금 게이트 — PIN 이 설정돼 있으면 앱 실행/장기 백그라운드 복귀 시 잠금 화면을 덮는다.
 * 잠긴 동안 콘텐츠는 보이지 않으며 PIN(또는 생체)으로만 해제된다.
 */
export function AppLockGate({ children }: { children: ReactNode }) {
  const [locked, setLocked] = useState<boolean>(() => hasPin());
  const [pin, setPin] = useState("");
  const [error, setError] = useState(false);
  const [checking, setChecking] = useState(false);
  const hiddenAt = useRef<number | null>(null);

  const attemptBiometric = useCallback(async () => {
    if (biometricAvailable() && biometricEnabled()) {
      if (await tryBiometric()) setLocked(false);
    }
  }, []);

  useEffect(() => {
    if (locked) void attemptBiometric();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // 백그라운드 복귀 시 grace 초과면 재잠금.
  useEffect(() => {
    const onVisibility = () => {
      if (document.visibilityState === "hidden") {
        hiddenAt.current = Date.now();
      } else if (hasPin() && hiddenAt.current && Date.now() - hiddenAt.current > AUTOLOCK_GRACE) {
        setLocked(true);
        setPin("");
        void attemptBiometric();
      }
    };
    document.addEventListener("visibilitychange", onVisibility);
    return () => document.removeEventListener("visibilitychange", onVisibility);
  }, [attemptBiometric]);

  const submit = async (value: string) => {
    setChecking(true);
    const ok = await verifyPin(value);
    setChecking(false);
    if (ok) {
      setLocked(false);
      setPin("");
      setError(false);
    } else {
      setError(true);
      setPin("");
      if (typeof navigator !== "undefined" && navigator.vibrate) navigator.vibrate([40, 30, 40]);
    }
  };

  const press = (digit: string) => {
    setError(false);
    const next = (pin + digit).slice(0, 6);
    setPin(next);
    if (next.length >= 4) {
      // 4자리 이상 입력 후 확인 버튼 또는 자동검증(6자리)
      if (next.length === 6) void submit(next);
    }
  };

  if (!locked) return <>{children}</>;

  return (
    <>
      {children}
      <div className="fixed inset-0 z-[9999] flex flex-col items-center justify-center bg-black/95 px-6 text-white backdrop-blur">
        <div className="flex size-16 items-center justify-center rounded-2xl bg-card/10">
          <Lock className="size-8" />
        </div>
        <h1 className="mt-5 text-lg font-bold">앱 잠금</h1>
        <p className="mt-1 text-sm text-white/60">PIN을 입력해 잠금을 해제하세요</p>

        <div className="mt-6 flex gap-3">
          {Array.from({ length: 6 }).map((_, i) => (
            <span key={i} className={`size-3 rounded-full ${i < pin.length ? "bg-card" : "bg-card/25"} ${error ? "bg-red-400" : ""}`} />
          ))}
        </div>
        {error && <div className="mt-2 text-sm text-red-300">PIN이 일치하지 않습니다</div>}

        <div className="mt-8 grid w-full max-w-xs grid-cols-3 gap-3">
          {["1", "2", "3", "4", "5", "6", "7", "8", "9"].map((n) => (
            <button key={n} onClick={() => press(n)} className="rounded-2xl bg-card/10 py-4 text-2xl font-semibold active:bg-card/20">
              {n}
            </button>
          ))}
          <button
            onClick={() => void attemptBiometric()}
            disabled={!biometricAvailable() || !biometricEnabled()}
            className="flex items-center justify-center rounded-2xl py-4 text-white/70 active:bg-card/10 disabled:opacity-30"
            aria-label="생체 인증"
          >
            <Fingerprint className="size-6" />
          </button>
          <button onClick={() => press("0")} className="rounded-2xl bg-card/10 py-4 text-2xl font-semibold active:bg-card/20">0</button>
          <button onClick={() => { setError(false); setPin((p) => p.slice(0, -1)); }} className="flex items-center justify-center rounded-2xl py-4 text-white/70 active:bg-card/10" aria-label="지우기">
            <Delete className="size-6" />
          </button>
        </div>

        <button
          onClick={() => void submit(pin)}
          disabled={pin.length < 4 || checking}
          className="mt-6 w-full max-w-xs rounded-2xl bg-blue-600 py-3 text-sm font-semibold disabled:opacity-40"
        >
          잠금 해제
        </button>
      </div>
    </>
  );
}
