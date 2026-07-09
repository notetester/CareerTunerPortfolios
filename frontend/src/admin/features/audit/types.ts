/** 로그인 감사 그리드 행 — 백엔드 AdminLoginAuditRow(user_login_history + users 조인)와 1:1. */
export interface AdminLoginAuditRow {
  id: number;
  userId: number | null;
  userEmail: string | null;
  userName: string | null;
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
