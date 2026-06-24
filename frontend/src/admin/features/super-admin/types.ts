export interface AdminPermissionAssignmentRow {
  id: number;
  userId: number;
  permissionCode: string;
  displayName: string;
  grantedBy: number | null;
  grantedAt: string;
}

export interface AdminGroupAssignmentRow {
  id: number;
  userId: number;
  groupCode: string;
  displayName: string;
  grantedBy: number | null;
  grantedAt: string;
}

export interface AdminAccountRow {
  id: number;
  email: string;
  name: string;
  role: string;
  status: string;
  lastLoginAt: string | null;
  createdAt: string;
  permissions?: AdminPermissionAssignmentRow[];
  groups?: AdminGroupAssignmentRow[];
}

export interface AdminPermissionPolicyRow {
  permissionCode: string;
  displayName: string;
  description: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface AdminPermissionGroupRow {
  groupCode: string;
  displayName: string;
  description: string | null;
  active: boolean;
  itemCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface AdminPermissionAuditRow {
  id: number;
  actorUserId: number | null;
  actorEmail: string | null;
  targetUserId: number | null;
  targetEmail: string | null;
  actionType: string;
  permissionCode: string | null;
  groupCode: string | null;
  reason: string | null;
  createdAt: string;
}
