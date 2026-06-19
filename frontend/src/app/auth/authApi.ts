import { api } from "../lib/api";
import type { TokenResponse } from "./AuthContext";

export function requestPasswordReset(email: string): Promise<void> {
  return api<void>(
    "/auth/password/reset-request",
    { method: "POST", body: JSON.stringify({ email }) },
    { auth: false },
  );
}

export function resetPassword(token: string, newPassword: string): Promise<void> {
  return api<void>(
    "/auth/password/reset",
    { method: "POST", body: JSON.stringify({ token, newPassword }) },
    { auth: false },
  );
}

export function requestDormantRelease(email: string): Promise<void> {
  return api<void>(
    "/auth/dormant/release-request",
    { method: "POST", body: JSON.stringify({ email }) },
    { auth: false },
  );
}

export function releaseDormant(token: string): Promise<TokenResponse> {
  return api<TokenResponse>(
    "/auth/dormant/release",
    { method: "POST", body: JSON.stringify({ token }) },
    { auth: false },
  );
}

/** 회원가입 이메일 중복 확인. duplicate=true 면 이미 가입된 이메일. */
export function checkEmailDuplicate(email: string): Promise<{ duplicate: boolean }> {
  return api<{ duplicate: boolean }>(
    `/auth/check/email?value=${encodeURIComponent(email)}`,
    { method: "GET" },
    { auth: false },
  );
}

/** 인증 메일 재발송. */
export function resendVerificationEmail(email: string): Promise<void> {
  return api<void>(
    `/auth/email/resend?email=${encodeURIComponent(email)}`,
    { method: "POST" },
    { auth: false },
  );
}
