import { useCallback, useEffect, useRef, useState } from "react";
import type { ConfirmationResult } from "firebase/auth";
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
export const RECAPTCHA_CONTAINER_ID = "ct-phone-recaptcha";

export interface UsePhoneVerificationOptions {
  /** 인증 성공 시 호출 — 상위 화면이 계정 정보를 다시 불러오는 등 후처리에 쓴다. */
  onVerified?: (status: PhoneStatus) => void;
}

/**
 * 전화번호 SMS OTP / Firebase Phone Auth 인증 로직 훅.
 *
 * 전화번호 입력 → 인증요청 → (Mock 모드면 devCode 안내/자동채움) → 코드 입력 → 검증 → 인증완료.
 * 백엔드 OTP 흐름과 프런트 Firebase 흐름을 provider 설정으로 분기하며, 발송·검증 상태를 캡슐화한다.
 * 검증 성공 시 백엔드가 users.phone/phone_verified 를 함께 갱신하므로 별도 저장 호출이 필요 없다.
 * 계정 카드(AccountInfoCard)가 이 훅으로 인증을 진행한다.
 */
export function usePhoneVerification(options?: UsePhoneVerificationOptions) {
  const [status, setStatus] = useState<PhoneStatus | null>(null);
  const [loadingStatus, setLoadingStatus] = useState(true);
  const [authConfig, setAuthConfig] = useState<PhoneAuthConfig | null>(null);
  const confirmationRef = useRef<ConfirmationResult | null>(null);
  // onVerified 를 ref 로 잡아 콜백 참조가 바뀌어도 핸들러를 재생성하지 않는다.
  const onVerifiedRef = useRef(options?.onVerified);
  onVerifiedRef.current = options?.onVerified;

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
      // 입력칸이 비어 있을 때만 저장된 번호로 초기화한다(사용자 입력 유지).
      if (next.phone) setPhone((prev) => prev || next.phone || "");
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
      resetFirebaseRecaptcha(RECAPTCHA_CONTAINER_ID);
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
      resetFirebaseRecaptcha(RECAPTCHA_CONTAINER_ID);
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
        const next: PhoneStatus = { phone: result.phone, phoneVerified: true };
        setStatus(next);
        setCodeSent(false);
        setCode("");
        confirmationRef.current = null;
        resetFirebaseRecaptcha(RECAPTCHA_CONTAINER_ID);
        setMessage("전화번호 인증이 완료되었습니다.");
        onVerifiedRef.current?.(next);
      }
    } catch (e) {
      setError(firebaseAuthErrorMessage(e));
    } finally {
      setVerifying(false);
    }
  };

  // ── 백엔드 OTP 흐름(Mock/실 SMS) ──

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
        const next: PhoneStatus = { phone: result.phone, phoneVerified: true };
        setStatus(next);
        setCodeSent(false);
        setCode("");
        setDevCode(null);
        setCooldown(0);
        setMessage("전화번호 인증이 완료되었습니다.");
        onVerifiedRef.current?.(next);
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : "인증번호 검증에 실패했습니다.");
    } finally {
      setVerifying(false);
    }
  };

  const alreadyVerified = status?.phoneVerified === true;

  return {
    status,
    loadingStatus,
    authConfig,
    phone,
    setPhone,
    code,
    setCode,
    devCode,
    provider,
    realSending,
    codeSent,
    requesting,
    verifying,
    error,
    message,
    cooldown,
    isFirebase,
    alreadyVerified,
    handleRequest,
    handleVerify,
    reloadStatus: loadStatus,
  };
}
