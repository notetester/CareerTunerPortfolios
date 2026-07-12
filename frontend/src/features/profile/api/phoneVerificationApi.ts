import { api } from "@/app/lib/api";
import type { FirebaseWebConfig } from "../lib/firebasePhoneAuth";

/** POST /api/auth/phone/request-otp 응답. Mock 모드면 devCode 포함. */
export interface PhoneOtpRequestResult {
  sent: boolean;
  provider: string;
  realSending: boolean;
  /** Mock(데모) 발송일 때만 채워지는 생성 코드. 실 발송이면 undefined. */
  devCode?: string | null;
  validitySeconds: number;
  cooldownSeconds: number;
}

/** POST /api/auth/phone/verify-otp 응답. */
export interface PhoneOtpVerifyResult {
  verified: boolean;
  phone: string;
}

/** GET /api/auth/phone/status 응답. */
export interface PhoneStatus {
  phone: string | null;
  phoneVerified: boolean;
}

/** GET /api/auth/phone/config 응답. provider="firebase" 면 프런트 Firebase 흐름, 그 외 "otp"(백엔드 발송). */
export interface PhoneAuthConfig {
  provider: "firebase" | "otp";
  firebase: FirebaseWebConfig | null;
}

/** 인증번호 발송. Mock 모드면 devCode 로 코드가 함께 온다. */
export function requestPhoneOtp(phone: string): Promise<PhoneOtpRequestResult> {
  return api<PhoneOtpRequestResult>("/auth/phone/request-otp", {
    method: "POST",
    body: JSON.stringify({ phone }),
  });
}

/** 인증번호 검증. 성공 시 전화번호 인증 완료. */
export function verifyPhoneOtp(phone: string, code: string): Promise<PhoneOtpVerifyResult> {
  return api<PhoneOtpVerifyResult>("/auth/phone/verify-otp", {
    method: "POST",
    body: JSON.stringify({ phone, code }),
  });
}

/** 현재 전화번호 인증 상태 조회. */
export function getPhoneStatus(): Promise<PhoneStatus> {
  return api<PhoneStatus>("/auth/phone/status", { method: "GET" });
}

/** 전화번호 인증 흐름 설정(provider + Firebase 웹 config) 조회. */
export function getPhoneAuthConfig(): Promise<PhoneAuthConfig> {
  return api<PhoneAuthConfig>("/auth/phone/config", { method: "GET" });
}

/** Firebase Phone Auth 로 받은 ID 토큰을 백엔드에 넘겨 최종 검증. 성공 시 전화번호 인증 완료. */
export function verifyPhoneFirebase(idToken: string): Promise<PhoneOtpVerifyResult> {
  return api<PhoneOtpVerifyResult>("/auth/phone/verify-firebase", {
    method: "POST",
    body: JSON.stringify({ idToken }),
  });
}
