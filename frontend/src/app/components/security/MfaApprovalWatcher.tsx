import { useEffect, useMemo, useRef, useState } from "react";
import { useLocation } from "react-router";
import { CheckCircle2, Loader2, ShieldCheck, XCircle } from "lucide-react";
import { useAuth } from "@/app/auth/AuthContext";
import { approveMfaPush, getPendingMfaPush, type MfaChallengeResponse } from "@/app/auth/mfaApi";
import { Button } from "@/app/components/ui/button";
import { isNativeApp } from "@/platform/capacitor";

const POLL_INTERVAL_MS = 5000;

/**
 * 로그인된 모바일/웹앱 사용자가 어느 화면에 있든 MFA 승인 요청을 볼 수 있게 하는 전역 감시자.
 *
 * 백엔드는 PC 로그인 시 mfa_challenge를 PENDING으로 만들고,
 * 이 컴포넌트는 짧은 주기로 내 pending challenge를 조회해 모달로 승인/거절을 노출한다.
 * Firebase Push가 없어도 동작하는 1차 구현이고, 추후 FCM 수신 이벤트가 오면 같은 모달 상태만 열면 된다.
 */
export function MfaApprovalWatcher() {
  const location = useLocation();
  const { isAuthenticated } = useAuth();
  const [current, setCurrent] = useState<MfaChallengeResponse | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [processing, setProcessing] = useState(false);
  const [now, setNow] = useState(Date.now());
  const seenTokensRef = useRef<Set<string>>(new Set());
  const mountedRef = useRef(true);

  const shouldPoll = isNativeApp() && isAuthenticated && !location.pathname.startsWith("/m/mfa-approvals");

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
    };
  }, []);

  useEffect(() => {
    setNow(Date.now());
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);

  useEffect(() => {
    if (!shouldPoll) {
      setCurrent(null);
      return;
    }

    let cancelled = false;
    const poll = async () => {
      if (processing || current) return;
      try {
        const pending = await getPendingMfaPush();
        if (cancelled || !mountedRef.current) return;
        const next = pending.find((item) => !seenTokensRef.current.has(item.challengeToken));
        if (next) {
          setMessage(null);
          setError(null);
          setCurrent(next);
        }
      } catch {
        // MFA 미설정 사용자나 일시적인 네트워크 오류 때문에 앱 전체 사용을 막지 않는다.
      }
    };

    void poll();
    const timer = window.setInterval(() => void poll(), POLL_INTERVAL_MS);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [current, processing, shouldPoll]);

  const expiresInSec = useMemo(() => {
    if (!current?.expiresAt) return 0;
    return Math.max(0, Math.ceil((new Date(current.expiresAt).getTime() - now) / 1000));
  }, [current?.expiresAt, now]);

  if (!current) return null;

  const decide = async (approve: boolean) => {
    setProcessing(true);
    setError(null);
    setMessage(null);
    try {
      await approveMfaPush(current.challengeToken, approve, deviceName());
      seenTokensRef.current.add(current.challengeToken);
      setMessage(approve ? "로그인 요청을 승인했습니다." : "로그인 요청을 거절했습니다.");
      window.setTimeout(() => {
        if (!mountedRef.current) return;
        setCurrent(null);
        setMessage(null);
      }, 900);
    } catch (err) {
      setError(err instanceof Error ? err.message : "승인 요청 처리에 실패했습니다.");
    } finally {
      setProcessing(false);
    }
  };

  return (
    <div className="fixed inset-0 z-[1200] flex items-end justify-center bg-slate-950/55 px-4 pb-[calc(env(safe-area-inset-bottom)+16px)] pt-6 backdrop-blur-sm sm:items-center sm:pb-6">
      <section
        role="dialog"
        aria-modal="true"
        aria-labelledby="mfa-approval-title"
        className="w-full max-w-md overflow-hidden rounded-2xl border border-slate-200 bg-white text-slate-950 shadow-2xl"
      >
        <div className="border-b border-slate-200 bg-blue-50 px-5 py-4">
          <div className="flex items-center gap-3">
            <div className="flex size-10 items-center justify-center rounded-full bg-blue-600 text-white">
              <ShieldCheck className="size-5" />
            </div>
            <div>
              <h2 id="mfa-approval-title" className="text-base font-bold text-slate-950">
                새 로그인 승인 요청
              </h2>
              <p className="text-xs font-medium text-blue-800">본인이 시도한 로그인이 맞는지 확인해 주세요.</p>
            </div>
          </div>
        </div>

        <div className="space-y-3 px-5 py-4 text-sm">
          <InfoRow label="요청 IP" value={current.ipAddress ?? "확인 불가"} />
          <InfoRow label="요청 시간" value={formatDate(current.createdAt)} />
          <InfoRow label="남은 시간" value={expiresInSec > 0 ? `${expiresInSec}초` : "만료 확인 중"} strong />
          <div className="rounded-xl border border-slate-200 bg-slate-50 p-3">
            <div className="mb-1 text-xs font-semibold text-slate-500">브라우저/기기</div>
            <div className="break-words text-xs leading-5 text-slate-700">{current.userAgent ?? "확인 불가"}</div>
          </div>

          {message && <div className="rounded-lg border border-green-200 bg-green-50 p-3 text-sm font-medium text-green-700">{message}</div>}
          {error && <div className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm font-medium text-red-700">{error}</div>}
        </div>

        <div className="grid grid-cols-2 gap-2 border-t border-slate-200 bg-slate-50 px-5 py-4">
          <Button
            type="button"
            variant="outline"
            className="gap-2 border-red-200 bg-white text-red-600 hover:bg-red-50"
            disabled={processing}
            onClick={() => void decide(false)}
          >
            {processing ? <Loader2 className="size-4 animate-spin" /> : <XCircle className="size-4" />}
            거절
          </Button>
          <Button
            type="button"
            className="gap-2 bg-blue-600 text-white hover:bg-blue-700"
            disabled={processing || expiresInSec <= 0}
            onClick={() => void decide(true)}
          >
            {processing ? <Loader2 className="size-4 animate-spin" /> : <CheckCircle2 className="size-4" />}
            승인
          </Button>
        </div>
      </section>
    </div>
  );
}

function InfoRow({ label, value, strong = false }: { label: string; value: string; strong?: boolean }) {
  return (
    <div className="flex items-center justify-between gap-3 rounded-xl border border-slate-200 bg-white px-3 py-2">
      <span className="text-xs font-semibold text-slate-500">{label}</span>
      <span className={`text-right text-sm ${strong ? "font-bold text-blue-700" : "font-medium text-slate-800"}`}>{value}</span>
    </div>
  );
}

function formatDate(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}

function deviceName() {
  const ua = navigator.userAgent || "CareerTuner app";
  return ua.length > 120 ? ua.slice(0, 120) : ua;
}
