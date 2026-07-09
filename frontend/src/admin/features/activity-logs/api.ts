import { api } from "@/app/lib/api";

export interface ActivityLog {
  id: number;
  requestId: string;
  flowTraceId: string | null;
  userId: number | null;
  requestUri: string;
  httpMethod: string;
  activityDomain: string;
  activityType: string;
  activityCode: string | null;
  activityProvider: string | null;
  authEventType: string | null;
  targetType: string | null;
  targetId: string | null;
  handlerName: string | null;
  ipAddress: string | null;
  userAgent: string | null;
  responseStatus: number | null;
  responseTimeMs: number | null;
  success: boolean | null;
  detailSummary: string | null;
  createdAt: string;
}

export interface SecurityHistory {
  id: number;
  userId: number | null;
  actorUserId: number | null;
  eventType: string;
  eventStage: string | null;
  inputIdentifier: string | null;
  targetEmail: string | null;
  success: boolean;
  failReason: string | null;
  detailMessage: string | null;
  requestId: string | null;
  flowTraceId: string | null;
  ipAddress: string | null;
  occurredAt: string;
}

export interface Page<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
}

export interface ActivityFilters {
  keyword?: string;
  domain?: string;
  activityType?: string;
  success?: boolean;
  userId?: number;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

export interface SecurityFilters {
  keyword?: string;
  eventType?: string;
  eventStage?: string;
  success?: boolean;
  userId?: number;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

function qs(params: object): string {
  const sp = new URLSearchParams();
  Object.entries(params as Record<string, unknown>).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== "") sp.set(k, String(v));
  });
  const s = sp.toString();
  return s ? `?${s}` : "";
}

export function getActivityLogs(filters: ActivityFilters): Promise<Page<ActivityLog>> {
  return api<Page<ActivityLog>>(`/admin/activity-logs${qs(filters)}`);
}

export function getSecurityHistories(filters: SecurityFilters): Promise<Page<SecurityHistory>> {
  return api<Page<SecurityHistory>>(`/admin/security-histories${qs(filters)}`);
}
