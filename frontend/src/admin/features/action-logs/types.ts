export interface AdminActionLogRow {
  id: number;
  actorUserId: number | null;
  actorEmail: string | null;
  targetUserId: number | null;
  targetEmail: string | null;
  actionType: string;
  targetType: string;
  beforeValue: string | null;
  afterValue: string | null;
  reason: string | null;
  ipAddress: string | null;
  userAgent: string | null;
  createdAt: string;
}
