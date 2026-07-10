// 데모/목: MFA 설정과 관리자 정책. 설정 화면의 실제 API 계약을 세션 내 상태로 재현한다.
import type {
  MfaBackupCodesResponse,
  MfaPolicyResponse,
  MfaSetupStartResponse,
  MfaStatusResponse,
} from "@/app/auth/mfaApi";
import type { MockRoute } from "../registry";

let status: MfaStatusResponse = {
  enabled: false,
  verified: false,
  mfaType: "TOTP",
  deviceName: null,
  pushEnabled: false,
  backupCodeRemaining: 0,
  adminSetupRecommended: false,
};

let policy: MfaPolicyResponse = {
  requireAdmins: false,
  allowBackupCode: true,
  allowPushApproval: true,
};

const backupCodes = (): MfaBackupCodesResponse => ({
  codes: ["DEMO-A1B2", "DEMO-C3D4", "DEMO-E5F6", "DEMO-G7H8"],
});

export const mfaRoutes: MockRoute[] = [
  { method: "GET", pattern: /^\/auth\/mfa\/status$/, handler: () => ({ ...status }) },
  {
    method: "POST",
    pattern: /^\/auth\/mfa\/setup\/start$/,
    handler: ({ query }) => ({
      secret: "JBSWY3DPEHPK3PXP",
      otpauthUri: "otpauth://totp/CareerTuner:demo?secret=JBSWY3DPEHPK3PXP&issuer=CareerTuner",
      deviceName: query.get("deviceName") || "내 휴대폰",
    }) satisfies MfaSetupStartResponse,
  },
  {
    method: "POST",
    pattern: /^\/auth\/mfa\/setup\/verify$/,
    handler: () => {
      status = {
        ...status,
        enabled: true,
        verified: true,
        deviceName: status.deviceName ?? "내 휴대폰",
        backupCodeRemaining: 4,
      };
      return backupCodes();
    },
  },
  {
    method: "POST",
    pattern: /^\/auth\/mfa\/disable$/,
    handler: () => {
      status = { ...status, enabled: false, verified: false, pushEnabled: false, backupCodeRemaining: 0 };
      return null;
    },
  },
  {
    method: "POST",
    pattern: /^\/auth\/mfa\/backup-codes\/regenerate$/,
    handler: () => {
      status = { ...status, backupCodeRemaining: 4 };
      return backupCodes();
    },
  },
  { method: "GET", pattern: /^\/auth\/mfa\/push\/pending$/, handler: () => [] },
  { method: "POST", pattern: /^\/auth\/mfa\/push\/approve$/, handler: () => null },
  { method: "GET", pattern: /^\/admin\/mfa-policy$/, handler: () => ({ ...policy }) },
  {
    method: "PUT",
    pattern: /^\/admin\/mfa-policy$/,
    handler: ({ body }) => {
      policy = { ...policy, ...((body ?? {}) as Partial<MfaPolicyResponse>) };
      return { ...policy };
    },
  },
];
