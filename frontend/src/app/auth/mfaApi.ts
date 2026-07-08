import { api } from "../lib/api";
import type { LoginResponse, TokenResponse } from "./AuthContext";

export interface MfaStatusResponse {
  enabled: boolean;
  verified: boolean;
  mfaType: string;
  deviceName: string | null;
  pushEnabled: boolean;
  backupCodeRemaining: number;
  adminSetupRecommended: boolean;
}

export interface MfaSetupStartResponse {
  secret: string;
  otpauthUri: string;
  deviceName: string;
}

export interface MfaBackupCodesResponse {
  codes: string[];
}

export interface MfaChallengeResponse {
  challengeToken: string;
  status: string;
  ipAddress: string | null;
  userAgent: string | null;
  deviceName: string | null;
  expiresAt: string;
  createdAt: string;
}

export interface MfaLoginStatusResponse {
  status: string;
  token: TokenResponse | null;
}

export interface MfaPolicyResponse {
  requireAdmins: boolean;
  allowBackupCode: boolean;
  allowPushApproval: boolean;
}

export function getMfaStatus() {
  return api<MfaStatusResponse>("/auth/mfa/status", { method: "GET" });
}

export function startMfaSetup(deviceName: string) {
  return api<MfaSetupStartResponse>(`/auth/mfa/setup/start?deviceName=${encodeURIComponent(deviceName)}`, { method: "POST" });
}

export function verifyMfaSetup(code: string) {
  return api<MfaBackupCodesResponse>("/auth/mfa/setup/verify", { method: "POST", body: JSON.stringify({ code }) });
}

export function disableMfa(code: string, backupCode: string) {
  return api<void>("/auth/mfa/disable", { method: "POST", body: JSON.stringify({ code, backupCode }) });
}

export function regenerateBackupCodes() {
  return api<MfaBackupCodesResponse>("/auth/mfa/backup-codes/regenerate", { method: "POST" });
}

export function verifyMfaLogin(challengeToken: string, code: string, backupCode = "", useApprovedChallenge = false) {
  return api<LoginResponse>(
    "/auth/mfa/login/verify",
    { method: "POST", body: JSON.stringify({ challengeToken, code, backupCode, useApprovedChallenge }) },
    { auth: false },
  );
}

export function getMfaLoginStatus(challengeToken: string) {
  return api<MfaLoginStatusResponse>(
    `/auth/mfa/login/status?challengeToken=${encodeURIComponent(challengeToken)}`,
    { method: "GET" },
    { auth: false },
  );
}

export function getPendingMfaPush() {
  return api<MfaChallengeResponse[]>("/auth/mfa/push/pending", { method: "GET" });
}

export function approveMfaPush(challengeToken: string, approve: boolean, deviceName: string) {
  return api<void>("/auth/mfa/push/approve", {
    method: "POST",
    body: JSON.stringify({ challengeToken, approve, deviceName }),
  });
}

export function getMfaPolicy() {
  return api<MfaPolicyResponse>("/admin/mfa-policy", { method: "GET" });
}

export function updateMfaPolicy(body: Partial<MfaPolicyResponse>) {
  return api<MfaPolicyResponse>("/admin/mfa-policy", { method: "PUT", body: JSON.stringify(body) });
}
