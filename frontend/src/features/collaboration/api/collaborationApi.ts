import { api, apiBlob } from "@/app/lib/api";
import { trackPendingCollaborationUpload } from "@/app/lib/pendingCollaborationFiles";
import type {
  AttachmentShareMode,
  CollaborationUser,
  ConversationPermissionUpdateRequest,
  ConversationSettingsResponse,
  ConversationSettingsUpdateRequest,
  ConversationSummaryResponse,
  CreateConversationRequest,
  FileAssetResponse,
  FriendRequestResponse,
  FriendResponse,
  MessageAttachmentResponse,
  MessageResponse,
  SendMessageRequest,
} from "../types/collaboration";

function query(params: Record<string, string | number | undefined | null>): string {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") {
      search.set(key, String(value));
    }
  });
  const value = search.toString();
  return value ? `?${value}` : "";
}

export function searchUsers(keyword: string, limit = 20): Promise<CollaborationUser[]> {
  return api<CollaborationUser[]>(
    `/collaboration/users/search${query({ keyword, limit })}`,
    { method: "GET" },
  );
}

export function listFriends(): Promise<FriendResponse[]> {
  return api<FriendResponse[]>("/collaboration/friends", { method: "GET" });
}

export function removeFriend(friendUserId: number): Promise<void> {
  return api<void>(`/collaboration/friends/${friendUserId}`, { method: "DELETE" });
}

export function listIncomingFriendRequests(): Promise<FriendRequestResponse[]> {
  return api<FriendRequestResponse[]>("/collaboration/friend-requests/incoming", { method: "GET" });
}

export function listOutgoingFriendRequests(): Promise<FriendRequestResponse[]> {
  return api<FriendRequestResponse[]>("/collaboration/friend-requests/outgoing", { method: "GET" });
}

export function sendFriendRequest(targetUserId: number): Promise<FriendRequestResponse> {
  return api<FriendRequestResponse>("/collaboration/friend-requests", {
    method: "POST",
    body: JSON.stringify({ targetUserId }),
  });
}

export function acceptFriendRequest(requestId: number): Promise<FriendRequestResponse> {
  return api<FriendRequestResponse>(`/collaboration/friend-requests/${requestId}/accept`, { method: "POST" });
}

export function declineFriendRequest(requestId: number): Promise<FriendRequestResponse> {
  return api<FriendRequestResponse>(`/collaboration/friend-requests/${requestId}/decline`, { method: "POST" });
}

export function listConversations(): Promise<ConversationSummaryResponse[]> {
  return api<ConversationSummaryResponse[]>("/collaboration/conversations", { method: "GET" });
}

export function discoverConversations(keyword: string, limit = 30): Promise<ConversationSummaryResponse[]> {
  return api<ConversationSummaryResponse[]>(
    `/collaboration/conversations/discover${query({ keyword, limit })}`,
    { method: "GET" },
  );
}

export function createConversation(request: CreateConversationRequest): Promise<ConversationSummaryResponse> {
  return api<ConversationSummaryResponse>("/collaboration/conversations", {
    method: "POST",
    body: JSON.stringify(request),
  });
}

export function openDirectConversation(targetUserId: number): Promise<ConversationSummaryResponse> {
  return api<ConversationSummaryResponse>("/collaboration/conversations/direct", {
    method: "POST",
    body: JSON.stringify({ targetUserId }),
  });
}

export function joinConversation(conversationId: number, password?: string): Promise<ConversationSummaryResponse> {
  return api<ConversationSummaryResponse>(`/collaboration/conversations/${conversationId}/join`, {
    method: "POST",
    body: JSON.stringify({ password: password || null }),
  });
}

export function inviteConversationMembers(conversationId: number, userIds: number[]): Promise<ConversationSummaryResponse> {
  return api<ConversationSummaryResponse>(`/collaboration/conversations/${conversationId}/invites`, {
    method: "POST",
    body: JSON.stringify({ userIds }),
  });
}

/** 대화방 알림 해제/재개. 해제한 방은 내 이름·키워드 언급 시에만 알림이 온다. */
export function muteConversation(conversationId: number, muted: boolean): Promise<ConversationSummaryResponse> {
  return api<ConversationSummaryResponse>(`/collaboration/conversations/${conversationId}/mute`, {
    method: "PATCH",
    body: JSON.stringify({ muted }),
  });
}

export function listMessages(conversationId: number, limit = 100): Promise<MessageResponse[]> {
  return api<MessageResponse[]>(
    `/collaboration/conversations/${conversationId}/messages${query({ limit })}`,
    { method: "GET" },
  );
}

export function sendMessage(conversationId: number, request: SendMessageRequest): Promise<MessageResponse> {
  return api<MessageResponse>(`/collaboration/conversations/${conversationId}/messages`, {
    method: "POST",
    body: JSON.stringify(request),
  });
}

// ── 방 설정 / 관리자 위임 (W5) — OWNER 및 위임받은 MANAGER 만 ──

export function getConversationSettings(conversationId: number): Promise<ConversationSettingsResponse> {
  return api<ConversationSettingsResponse>(
    `/collaboration/conversations/${conversationId}/settings`,
    { method: "GET" },
  );
}

export function updateConversationSettings(
  conversationId: number,
  request: ConversationSettingsUpdateRequest,
): Promise<ConversationSettingsResponse> {
  return api<ConversationSettingsResponse>(
    `/collaboration/conversations/${conversationId}/settings`,
    { method: "PATCH", body: JSON.stringify(request) },
  );
}

export function updateMemberPermission(
  conversationId: number,
  targetUserId: number,
  request: ConversationPermissionUpdateRequest,
): Promise<ConversationSettingsResponse> {
  return api<ConversationSettingsResponse>(
    `/collaboration/conversations/${conversationId}/members/${targetUserId}/permission`,
    { method: "PATCH", body: JSON.stringify(request) },
  );
}

export function kickConversationMember(
  conversationId: number,
  targetUserId: number,
): Promise<ConversationSettingsResponse> {
  return api<ConversationSettingsResponse>(
    `/collaboration/conversations/${conversationId}/members/${targetUserId}/kick`,
    { method: "POST" },
  );
}

export function banConversationMember(
  conversationId: number,
  targetUserId: number,
  reason?: string,
): Promise<ConversationSettingsResponse> {
  return api<ConversationSettingsResponse>(
    `/collaboration/conversations/${conversationId}/members/${targetUserId}/ban`,
    { method: "POST", body: JSON.stringify({ reason: reason || null }) },
  );
}

export function unbanConversationMember(
  conversationId: number,
  targetUserId: number,
): Promise<ConversationSettingsResponse> {
  return api<ConversationSettingsResponse>(
    `/collaboration/conversations/${conversationId}/bans/${targetUserId}`,
    { method: "DELETE" },
  );
}

export function setInviteAllowList(
  conversationId: number,
  userIds: number[],
): Promise<ConversationSettingsResponse> {
  return api<ConversationSettingsResponse>(
    `/collaboration/conversations/${conversationId}/invite-allowlist`,
    { method: "PUT", body: JSON.stringify({ userIds }) },
  );
}

export function uploadCollaborationFile(file: File): Promise<FileAssetResponse> {
  const formData = new FormData();
  formData.append("kind", "ATTACHMENT");
  formData.append("refType", "COLLAB_MESSAGE");
  formData.append("file", file);

  return trackPendingCollaborationUpload(api<FileAssetResponse>("/file/upload", {
    method: "POST",
    body: formData,
  }));
}

export async function downloadCollaborationAttachment(file: MessageAttachmentResponse): Promise<void> {
  const blob = await apiBlob(`/collaboration/files/${file.fileId}/content`);
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = file.originalName;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}

export const SHARE_MODE_LABEL: Record<AttachmentShareMode, string> = {
  TEMPORARY: "임시 공유",
  CLOUD: "클라우드 공유",
  LOCAL: "로컬 공유",
};
