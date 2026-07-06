import { api } from "@/app/lib/api";

export interface EmailAuditRow {
  id: number;
  userId: number | null;
  email: string;
  purpose: string;
  status: string; // USED / EXPIRED / PENDING
  used: boolean;
  expiredAt: string;
  usedAt: string | null;
  createdAt: string;
}

export function getEmailAudit(params: {
  email?: string;
  purpose?: string;
  status?: string;
  limit?: number;
}): Promise<EmailAuditRow[]> {
  const q = new URLSearchParams();
  if (params.email) q.set("email", params.email);
  if (params.purpose) q.set("purpose", params.purpose);
  if (params.status) q.set("status", params.status);
  q.set("limit", String(params.limit ?? 200));
  return api<EmailAuditRow[]>(`/admin/email-audit?${q}`);
}
