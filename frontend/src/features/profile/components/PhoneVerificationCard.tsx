import { useCallback, useEffect, useRef, useState } from "react";
import type { ConfirmationResult } from "firebase/auth";
import { CheckCircle2, Loader2, Phone, ShieldCheck, Sparkles } from "lucide-react";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Input } from "@/app/components/ui/input";
import {
  getPhoneAuthConfig,
  getPhoneStatus,
  requestPhoneOtp,
  verifyPhoneFirebase,
  verifyPhoneOtp,
  type PhoneAuthConfig,
  type PhoneStatus,
} from "../api/phoneVerificationApi";
import {
  firebaseAuthErrorMessage,
  resetFirebaseRecaptcha,
  sendFirebaseOtp,
  toE164Kr,
} from "../lib/firebasePhoneAuth";

// invisible reCAPTCHA 를 붙일 컨테이너 id.
const RECAPTCHA_CONTAINER_ID = "ct-phone-recaptcha";

/**
 * 전화번호 SMS OTP 인증 카드.
 *
 * 전화번호 입력 → 인증요청 → (Mock 모드면 devCode 안내/자동채움) → 코드 입력 → 검증 → 인증완료 배지.
 * 실 제공자 키가 없으면 서버가 Mock 으로 발송하고 응답 devCode 로 코드를 내려주므로,
 * 키 없이도 발송~검증 전 과정을 브라우저에서 시연할 수 있다.
 */
export function PhoneVerificationCard() {
  const [status, setStatus] = useState<PhoneStatus | null>(null);
  const [loadingStatus, setLoadingStatus] = useState(true);
  const [authConfig, setAuthConfig] = useState<PhoneAuthConfig | null>(null);
  const confirmationRef = useRef<ConfirmationResult | null>(null);

  const [phone, setPhone] = useState("");
  const [code, setCode] = useState("");
  const [devCode, setDevCode] = useState<string | null>(null);
  const [provider, setProvider] = useState<string | null>(null);
  const [realSending, setRealSending] = useState(false);
  const [codeSent, setCodeSent] = useState(false);

  const [requesting, setRequesting] = useState(false);
  const [verifying, setVerifying] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const [cooldown, setCooldown] = useState(0);
  const cooldownTimer = useRef<ReturnType<typeof setInterval> | null>(null);

  const loadStatus = useCallback(async () => {
    setLoadingStatus(true);
    try {
      const next = await getPhoneStatus();
      setStatus(next);
      if (next.phone && !phone) setPhone(next.phone);
    } catch {
      // 상태 조회 실패는 치명적이지 않다 — 인증 흐름 자체는 계속 시도할 수 있게 둔다.
    } finally {
      setLoadingStatus(false);
    }
    try {
      // 인증 흐름(firebase | otp) 판정. 실패 시 기존 OTP 흐름으로 취급한다.
      setAuthConfig(await getPhoneAuthConfig());
    } catch {
      setAuthConfig({ provider: "otp", firebase: null });
    }
    // phone 은 초기값 채움 용도이므로 의존성에서 제외한다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    void loadStatus();
  }, [loadStatus]);

  // 쿨다운 카운트다운.
  useEffect(() => {
    if (cooldown <= 0) {
      if (cooldownTimer.current) {
        clearInterval(cooldownTimer.current);
        cooldownTimer.current = null;
      }
      return;
    }
    if (!cooldownTimer.current) {
      cooldownTimer.current = setInterval(() => {
        setCooldown((prev) => (prev <= 1 ? 0 : prev - 1));
      }, 1000);
    }
    return () => {
      if (cooldownTimer.current) {
        clearInterval(cooldownTimer.current);
        cooldownTimer.current = null;
      }
    };
  }, [cooldown]);

  const isFirebase = authConfig?.provider === "firebase" && authConfig.firebase != null;

  // ── Firebase 흐름: 발송·코드검증을 프런트 SDK 가 수행, ID 토큰만 백엔드 검증 ──

  const handleRequestFirebase = async () => {
    const value = phone.trim();
    if (!value || !authConfig?.firebase) {
      setError("전화번호를 입력해 주세요.");
      return;
    }
    setRequesting(true);
    setError(null);
    setMessage(null);
    setDevCode(null);
    try {
      resetFirebaseRecaptcha();
      const confirmation = await sendFirebaseOtp(
        authConfig.firebase,
        toE164Kr(value),
        RECAPTCHA_CONTAINER_ID,
      );
      confirmationRef.current = confirmation;
      setCodeSent(true);
      setProvider("firebase");
      setRealSending(true);
      // 재발송 쿨다운은 Firebase 가 서버측에서 관리한다 — 프런트 카운트다운은 두지 않는다.
      setMessage("인증번호를 문자로 발송했습니다. 문자를 확인해 주세요.");
    } catch (e) {
      resetFirebaseRecaptcha();
      setError(firebaseAuthErrorMessage(e));
    } finally {
      setRequesting(false);
    }
  };

  const handleVerifyFirebase = async () => {
    const value = code.trim();
    const confirmation = confirmationRef.current;
    if (!value) {
      setError("인증번호를 입력해 주세요.");
      return;
    }
    if (!confirmation) {
      setError("먼저 인증번호를 요청해 주세요.");
      return;
    }
    setVerifying(true);
    setError(null);
    setMessage(null);
    try {
      const credential = await confirmation.confirm(value);
      const idToken = await credential.user.getIdToken();
      const result = await verifyPhoneFirebase(idToken);
      if (result.verified) {
        setStatus({ phone: result.phone, phoneVerified: true });
        setCodeSent(false);
        setCode("");
        confirmationRef.current = null;
        resetFirebaseRecaptcha();
        setMessage("전화번호 인증이 완료되었습니다.");
      }
    } catch (e) {
      setError(firebaseAuthErrorMessage(e));
    } finally {
      setVerifying(false);
    }
  };

  const handleRequest = async () => {
    if (isFirebase) return handleRequestFirebase();
    const value = phone.trim();
    if (!value) {
      setError("전화번호를 입력해 주세요.");
      return;
    }
    setRequesting(true);
    setError(null);
    setMessage(null);
    setDevCode(null);
    try {
      const result = await requestPhoneOtp(value);
      setCodeSent(true);
      setProvider(result.provider);
      setRealSending(result.realSending);
      setCooldown(result.cooldownSeconds);
      if (result.devCode) {
        // Mock(데모) 모드 — 코드를 화면에 노출하고 입력칸에 자동 채운다.
        setDevCode(result.devCode);
        setCode(result.devCode);
        setMessage("데모 모드로 발송되었습니다. 인증번호가 자동 입력되었습니다.");
      } else {
        setMessage("인증번호를 문자로 발송했습니다. 문자를 확인해 주세요.");
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : "인증번호 발송에 실패했습니다.");
    } finally {
      setRequesting(false);
    }
  };

  const handleVerify = async () => {
    if (isFirebase) return handleVerifyFirebase();
    const value = code.trim();
    if (!value) {
      setError("인증번호를 입력해 주세요.");
      return;
    }
    setVerifying(true);
    setError(null);
    setMessage(null);
    try {
      const result = await verifyPhoneOtp(phone.trim(), value);
      if (result.verified) {
        setStatus({ phone: result.phone, phoneVerified: true });
        setCodeSent(false);
        setCode("");
        setDevCode(null);
        setCooldown(0);
        setMessage("전화번호 인증이 완료되었습니다.");
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : "인증번호 검증에 실패했습니다.");
    } finally {
      setVerifying(false);
    }
  };

  const alreadyVerified = status?.phoneVerified === true;

  return (
    <Card className="border-slate-200">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <ShieldCheck className="size-5 text-blue-600" />
          전화번호 인증
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-5">
        {/* Firebase invisible reCAPTCHA 앵커 — 화면에 보이지 않지만 발송 시점에 DOM 에 있어야 한다. */}
        {isFirebase && <div id={RECAPTCHA_CONTAINER_ID} />}
        {error && (
          <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>
        )}
        {message && (
          <div className="rounded-lg border border-green-200 bg-green-50 px-4 py-3 text-sm text-green-700">{message}</div>
        )}

        {/* 현재 상태 */}
        <div className="flex items-center gap-2">
          <span className="flex items-center gap-1.5 text-xs font-bold text-slate-500">
            <Phone className="size-3.5" />
            인증 상태
          </span>
          {loadingStatus ? (
            <span className="text-sm text-slate-400">확인 중...</span>
          ) : alreadyVerified ? (
            <Badge className="bg-green-100 text-green-700">
              <CheckCircle2 className="mr-1 size-3.5" />
              인증됨 {status?.phone ? `(${status.phone})` : ""}
            </Badge>
          ) : (
            <Badge className="bg-slate-100 text-slate-600">미인증</Badge>
          )}
        </div>

        {!alreadyVerified && (
          <>
            {/* 전화번호 입력 + 발송 */}
            <div className="space-y-2">
              <span className="flex items-center gap-1.5 text-xs font-bold text-slate-500">
                <Phone className="size-3.5" />
                전화번호
              </span>
              <div className="flex flex-wrap items-center gap-2">
                <Input
                  className="max-w-xs"
                  value={phone}
                  onChange={(e) => setPhone(e.target.value)}
                  placeholder="010-1234-5678"
                  inputMode="tel"
                  disabled={requesting}
                />
                <Button
                  size="sm"
                  className="bg-blue-600 text-white hover:bg-blue-700"
                  onClick={() => void handleRequest()}
                  disabled={requesting || cooldown > 0}
                >
                  {requesting ? (
                    <>
                      <Loader2 className="mr-1 size-4 animate-spin" />
                      발송 중...
                    </>
                  ) : cooldown > 0 ? (
                    `재발송 (${cooldown}s)`
                  ) : codeSent ? (
                    "재발송"
                  ) : (
                    "인증번호 발송"
                  )}
                </Button>
              </div>
            </div>

            {/* Mock(데모) 안내 배지 */}
            {devCode && (
              <div className="flex items-center gap-2 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
                <Sparkles className="size-4 shrink-0" />
                <span>
                  데모 모드(실 제공자 키 미설정)로 발송된 인증번호:{" "}
                  <span className="font-mono font-bold tracking-widest">{devCode}</span>
                </span>
              </div>
            )}

            {/* 코드 입력 + 검증 */}
            {codeSent && (
              <div className="space-y-2">
                <span className="flex items-center gap-1.5 text-xs font-bold text-slate-500">
                  <ShieldCheck className="size-3.5" />
                  인증번호
                  {provider && (
                    <span className="font-normal text-slate-400">
                      · {realSending ? `${provider} 실발송` : `${provider} 데모`}
                    </span>
                  )}
                </span>
                <div className="flex flex-wrap items-center gap-2">
                  <Input
                    className="max-w-[10rem] font-mono tracking-widest"
                    value={code}
                    onChange={(e) => setCode(e.target.value.replace(/[^0-9]/g, "").slice(0, 6))}
                    placeholder="6자리 코드"
                    inputMode="numeric"
                    maxLength={6}
                    disabled={verifying}
                  />
                  <Button
                    size="sm"
                    className="bg-green-600 text-white hover:bg-green-700"
                    onClick={() => void handleVerify()}
                    disabled={verifying || code.trim().length === 0}
                  >
                    {verifying ? (
                      <>
                        <Loader2 className="mr-1 size-4 animate-spin" />
                        확인 중...
                      </>
                    ) : (
                      "인증 확인"
                    )}
                  </Button>
                </div>
              </div>
            )}

            <p className="text-xs text-slate-400">
              {isFirebase
                ? "입력하신 번호로 실제 인증 문자가 발송됩니다. 수신한 6자리 인증번호를 입력해 주세요."
                : "실제 SMS 제공자 키가 설정되면 실 문자로 발송되고, 키가 없으면 데모 모드로 화면에 인증번호가 표시됩니다."}
            </p>
          </>
        )}
      </CardContent>
    </Card>
  );
}

export default PhoneVerificationCard;
