export type AdminUserStatus = "ACTIVE" | "DORMANT" | "BLOCKED" | "DELETED";

export interface AdminUserRow {
  id: number;
  email: string;
  name: string;
  passwordEnabled: boolean;
  emailVerified: boolean;
  userType: string;
  role: string;
  status: AdminUserStatus;
  plan: string;
  credit: number;
  lastLoginAt: string | null;
  dormantAt: string | null;
  blockedReason: string | null;
  blockedUntil: string | null;
  deletedAt: string | null;
  statusChangedAt: string | null;
  statusChangedBy: number | null;
  failedLoginCount: number;
  lastFailedLoginAt: string | null;
  createdAt: string;
  updatedAt: string;
  loginSuccessCount: number;
  loginFailCount: number;
}

export interface AdminUserLoginHistoryRow {
  id: number;
  userId: number | null;
  eventType: string;
  authProvider: string;
  loginMethod: string | null;
  loginIdentifier: string | null;
  success: boolean;
  failReason: string | null;
  ipAddress: string | null;
  userAgent: string | null;
  requestUri: string | null;
  createdAt: string;
}

export interface AdminUserStatusHistoryRow {
  id: number;
  userId: number;
  actorUserId: number | null;
  previousStatus: string | null;
  newStatus: string;
  reason: string | null;
  memo: string | null;
  blockedUntil: string | null;
  createdAt: string;
}

export interface AdminUserConsentRow {
  id: number;
  userId: number;
  consentType: string;
  agreed: boolean;
  agreedAt: string | null;
  revokedAt: string | null;
  source: string | null;
  createdAt: string;
}

export interface AdminUserDetail {
  user: AdminUserRow;
  loginHistory: AdminUserLoginHistoryRow[];
  statusHistory: AdminUserStatusHistoryRow[];
  consents: AdminUserConsentRow[];
}
