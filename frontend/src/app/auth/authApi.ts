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
