export type ConversationType = "DIRECT" | "GROUP" | "PUBLIC" | "PRIVATE";

export type MessageKind = "CHAT" | "NOTE";

export type AttachmentShareMode = "TEMPORARY" | "CLOUD" | "LOCAL";

export type AttachmentAvailability = "AVAILABLE" | "EXPIRED" | "PLAN_INACTIVE" | "LOCAL_ONLY";

export interface CollaborationUser {
  id: number;
  email: string;
  name: string;
  plan?: string | null;
  relationStatus?: "NONE" | "REQUESTED" | "PENDING_INCOMING" | "FRIEND" | string | null;
}

/** 과거 메시지/대화의 공개 신원. 탈퇴 계정은 링크·이메일을 제거한다. */
export interface CollaborationPublicIdentity {
  id: number | null;
  email: string | null;
  name: string;
}

export interface FriendResponse {
  user: CollaborationUser;
  friendsSince: string;
}

export interface FriendRequestResponse {
  id: number;
  requester: CollaborationUser;
  receiver: CollaborationUser;
  status: "PENDING" | "ACCEPTED" | "DECLINED" | string;
  createdAt: string;
  respondedAt: string | null;
}

export interface MessagePreviewResponse {
  id: number;
  kind: MessageKind;
  content: string | null;
  createdAt: string;
}

export interface ConversationSummaryResponse {
  id: number;
  type: ConversationType;
  title: string | null;
  description: string | null;
  displayName: string;
  imageFileId: number | null;
  notice: string | null;
  locked: boolean;
  memberCount: number;
  joined: boolean;
  /** 내가 이 방 알림을 해제했는지 (해제 시 이름·키워드 언급만 알림) */
  muted: boolean;
  /** 방 내 내 role (OWNER/MANAGER/MEMBER, 미참여 null). */
  myRole: RoomRole | null;
  /** 방 설정 시트 진입 버튼 노출 힌트(실제 권한은 시트 조회에서 재검사). */
  canManageRoom: boolean;
  peer: CollaborationPublicIdentity | null;
  latestMessage: MessagePreviewResponse | null;
  unreadCount: number;
  updatedAt: string;
}

export type RoomRole = "OWNER" | "MANAGER" | "MEMBER";

export type InvitePolicy = "OWNER_ONLY" | "MANAGERS" | "SPECIFIC_MEMBERS" | "ALL_MEMBERS";

/** 방 관리자 세부 권한 플래그. OWNER 는 전부 true 로 내려온다. */
export interface ConversationPermission {
  owner: boolean;
  canKick: boolean;
  canBan: boolean;
  canSetPassword: boolean;
  canInvite: boolean;
  canEditRoom: boolean;
  canManageMembers: boolean;
}

export interface ConversationMemberDetail {
  userId: number | null;
  /** 익명 참가자는 방 전용 표시명, 그 외 실명. */
  displayName: string;
  /** 익명이면 null. */
  email: string | null;
  role: RoomRole;
  anonymous: boolean;
  roomProfileFileId: number | null;
  joinedAt: string;
  permission: ConversationPermission;
  banned: boolean;
}

export interface ConversationBan {
  userId: number | null;
  displayName: string;
  reason: string | null;
  bannedBy: number | null;
  bannedAt: string;
}

export interface ConversationAudit {
  id: number;
  actorId: number | null;
  actorName: string | null;
  targetUserId: number | null;
  targetName: string | null;
  action: string;
  detail: string | null;
  createdAt: string;
}

export interface ConversationSettingsResponse {
  conversationId: number;
  type: ConversationType;
  title: string | null;
  description: string | null;
  imageFileId: number | null;
  notice: string | null;
  locked: boolean;
  hasPassword: boolean;
  maxMembers: number;
  invitePolicy: InvitePolicy;
  allowAnonymous: boolean;
  anonymousOnly: boolean;
  /** 조회자 관점 권한(UI 게이팅). */
  myPermission: ConversationPermission;
  members: ConversationMemberDetail[];
  bans: ConversationBan[];
  inviteAllowUserIds: number[];
  recentAudits: ConversationAudit[];
}

export interface ConversationSettingsUpdateRequest {
  title?: string | null;
  description?: string | null;
  notice?: string | null;
  /** file_asset id. 0/음수면 이미지 제거. */
  imageFileId?: number | null;
  /** PUBLIC / PRIVATE 전환. */
  type?: Exclude<ConversationType, "DIRECT" | "GROUP"> | null;
  /** SET / CLEAR / null. */
  passwordAction?: "SET" | "CLEAR" | null;
  password?: string | null;
  maxMembers?: number | null;
  invitePolicy?: InvitePolicy | null;
  allowAnonymous?: boolean | null;
  anonymousOnly?: boolean | null;
}

export interface ConversationPermissionUpdateRequest {
  manager: boolean;
  canKick?: boolean;
  canBan?: boolean;
  canSetPassword?: boolean;
  canInvite?: boolean;
  canEditRoom?: boolean;
  canManageMembers?: boolean;
}

export interface CreateConversationRequest {
  type: Exclude<ConversationType, "DIRECT">;
  title: string;
  description?: string | null;
  password?: string | null;
  memberUserIds?: number[];
}

export interface SendMessageRequest {
  kind: MessageKind;
  content?: string | null;
  attachmentFileIds?: number[];
  attachmentShareMode?: AttachmentShareMode | null;
  temporaryHours?: number | null;
  sharedApplicationCaseIds?: number[];
}

export interface MessageResponse {
  id: number;
  conversationId: number;
  sender: CollaborationPublicIdentity;
  mine: boolean;
  kind: MessageKind;
  content: string | null;
  attachments: MessageAttachmentResponse[];
  sharedPostings: SharedPostingResponse[];
  createdAt: string;
  /** 개인 차단 정책 톰스톤 — true 면 content 가 대체 문구("차단한 사용자의 메시지입니다.") */
  blocked?: boolean;
}

export interface MessageAttachmentResponse {
  fileId: number;
  originalName: string;
  contentType: string | null;
  sizeBytes: number;
  shareMode: AttachmentShareMode;
  availability: AttachmentAvailability;
  expiresAt: string | null;
  downloadUrl: string | null;
  /** LOCAL 공유일 때만 세팅 — 파일 소유자의 데스크톱이 온라인이면 다운로드 가능. */
  ownerDesktopOnline?: boolean | null;
}

export interface SharedPostingResponse {
  applicationCaseId: number;
  companyName: string;
  jobTitle: string;
  deadlineDate: string | null;
  sourceType: string;
}

export interface FileAssetResponse {
  id: number;
  kind: string;
  refType: string;
  refId: number | null;
  originalName: string;
  contentType: string | null;
  sizeBytes: number;
  contentUrl: string;
  createdAt: string;
}
