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
  locked: boolean;
  memberCount: number;
  joined: boolean;
  /** 내가 이 방 알림을 해제했는지 (해제 시 이름·키워드 언급만 알림) */
  muted: boolean;
  peer: CollaborationUser | null;
  latestMessage: MessagePreviewResponse | null;
  unreadCount: number;
  updatedAt: string;
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
  sender: CollaborationUser;
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
