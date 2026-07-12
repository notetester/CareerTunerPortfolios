import { getApps, initializeApp } from "firebase/app";
import {
  getAuth,
  RecaptchaVerifier,
  signInWithPhoneNumber,
  type Auth,
  type ConfirmationResult,
} from "firebase/auth";

/**
 * Firebase Phone Auth 프런트 흐름.
 *
 * 백엔드 `GET /api/auth/phone/config` 가 내려준 공개 웹 config 로 Firebase 앱을 lazy 초기화하고,
 * invisible reCAPTCHA + signInWithPhoneNumber 로 실제 SMS 를 발송한다. 코드 확인(confirm) 후 얻은
 * ID 토큰은 `POST /api/auth/phone/verify-firebase` 로 넘겨 백엔드가 최종 검증한다.
 * 발송·코드검증은 여기서, 신뢰(토큰 검증)는 백엔드에서 담당한다.
 */

/** 백엔드 config 응답의 firebase 필드(공개키만). */
export interface FirebaseWebConfig {
  apiKey: string;
  authDomain: string;
  projectId: string;
  appId: string;
  messagingSenderId: string;
}

// FCM 등 다른 Firebase 앱과 충돌하지 않도록 전용 이름으로 초기화한다.
const APP_NAME = "careertuner-phone-auth";

let authRef: Auth | null = null;
let verifierRef: RecaptchaVerifier | null = null;

function ensureAuth(config: FirebaseWebConfig): Auth {
  if (authRef) return authRef;
  const existing = getApps().find((a) => a.name === APP_NAME);
  const app =
    existing ??
    initializeApp(
      {
        apiKey: config.apiKey,
        authDomain: config.authDomain,
        projectId: config.projectId,
        appId: config.appId,
        messagingSenderId: config.messagingSenderId,
      },
      APP_NAME,
    );
  authRef = getAuth(app);
  authRef.languageCode = "ko"; // reCAPTCHA·안내 문구 한국어.
  return authRef;
}

/** 한국 번호(010…/+8210…)를 Firebase 가 요구하는 E.164(+8210…)로 맞춘다. */
export function toE164Kr(raw: string): string {
  const digits = raw.replace(/[^0-9]/g, "");
  if (digits.startsWith("82")) return "+" + digits;
  if (digits.startsWith("0")) return "+82" + digits.slice(1);
  return "+82" + digits;
}

/**
 * invisible reCAPTCHA 를 붙여 SMS 를 발송한다. 반환된 ConfirmationResult 의 confirm(code) 으로 검증한다.
 * @param containerId reCAPTCHA 를 붙일 DOM 요소 id (invisible 이라 화면에 보이지 않는다)
 */
export async function sendFirebaseOtp(
  config: FirebaseWebConfig,
  e164Phone: string,
  containerId: string,
): Promise<ConfirmationResult> {
  const auth = ensureAuth(config);
  if (!verifierRef) {
    verifierRef = new RecaptchaVerifier(auth, containerId, { size: "invisible" });
  }
  return signInWithPhoneNumber(auth, e164Phone, verifierRef);
}

/** reCAPTCHA 위젯을 정리한다(재시도 시 중복 렌더 방지). */
export function resetFirebaseRecaptcha(): void {
  if (verifierRef) {
    try {
      verifierRef.clear();
    } catch {
      // 이미 정리된 경우 무시.
    }
    verifierRef = null;
  }
}

/** Firebase 오류 코드를 사용자 친화 메시지로 변환한다. */
export function firebaseAuthErrorMessage(e: unknown): string {
  const code = typeof e === "object" && e && "code" in e ? String((e as { code: unknown }).code) : "";
  switch (code) {
    case "auth/invalid-phone-number":
      return "전화번호 형식이 올바르지 않습니다.";
    case "auth/too-many-requests":
      return "요청이 많아 잠시 후 다시 시도해 주세요.";
    case "auth/invalid-verification-code":
      return "인증번호가 일치하지 않습니다.";
    case "auth/code-expired":
      return "인증번호가 만료되었습니다. 다시 요청해 주세요.";
    case "auth/quota-exceeded":
      return "일일 인증 한도를 초과했습니다. 나중에 다시 시도해 주세요.";
    default:
      return e instanceof Error ? e.message : "인증 처리에 실패했습니다.";
  }
}
