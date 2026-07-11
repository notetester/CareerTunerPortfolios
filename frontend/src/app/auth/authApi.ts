import { api } from "../lib/api";
import type { SocialProvider, TokenResponse } from "./AuthContext";

export type OAuthProviderAvailability = Record<SocialProvider, boolean>;

/** 현재 환경에서 실제 인증 흐름을 시작할 수 있는 소셜 로그인 제공자. */
export function getOAuthProviderAvailability(): Promise<OAuthProviderAvailability> {
  return api<OAuthProviderAvailability>(
    "/auth/oauth/providers",
    { method: "GET" },
    { auth: false },
  );
}

export function requestPasswordReset(email: string): Promise<void> {
  return api<void>(
    "/auth/password/reset-request",
    { method: "POST", body: JSON.stringify({ identifier: email }) },
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

export function requestFindId(email: string): Promise<void> {
  return api<void>(
    "/auth/find-id/request",
    { method: "POST", body: JSON.stringify({ email }) },
    { auth: false },
  );
}

export function verifyFindId(token: string): Promise<{ loginId: string }> {
  return api<{ loginId: string }>(
    `/auth/find-id/verify?token=${encodeURIComponent(token)}`,
    { method: "GET" },
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

/** 로그인 아이디 중복 확인. duplicate=true 면 이미 사용 중인 아이디. */
export function checkLoginIdDuplicate(loginId: string): Promise<{ duplicate: boolean }> {
  return api<{ duplicate: boolean }>(
    `/auth/check/login-id?value=${encodeURIComponent(loginId)}`,
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
