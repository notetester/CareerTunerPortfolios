import { api } from "@/app/lib/api";
import { apiBase } from "@/app/lib/apiBase";
import { getAccessToken } from "@/app/lib/tokenStore";
import type {
  AttachmentShareMode,
  ChatProfile,
  CollaborationUser,
  ConversationMember,
  ConversationSettings,
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

export function joinConversationWithProfile(conversationId: number, request: {
  password?: string | null;
  anonymous?: boolean;
  chatProfileId?: number | null;
  displayName?: string | null;
  avatarUrl?: string | null;
}): Promise<ConversationSummaryResponse> {
  return api<ConversationSummaryResponse>(`/collaboration/conversations/${conversationId}/join`, {
    method: "POST",
    body: JSON.stringify(request),
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

export function getConversationSettings(conversationId: number): Promise<ConversationSettings> {
  return api<ConversationSettings>(`/collaboration/conversations/${conversationId}/settings`, { method: "GET" });
}

export function updateConversationSettings(conversationId: number, request: Partial<ConversationSettings> & {
  password?: string | null;
  clearPassword?: boolean;
}): Promise<ConversationSettings> {
  return api<ConversationSettings>(`/collaboration/conversations/${conversationId}/settings`, {
    method: "PATCH",
    body: JSON.stringify(request),
  });
}

export function listConversationMembers(conversationId: number): Promise<ConversationMember[]> {
  return api<ConversationMember[]>(`/collaboration/conversations/${conversationId}/members`, { method: "GET" });
}

export function updateConversationMember(conversationId: number, userId: number, request: {
  role?: string;
  permissions?: string[];
  displayName?: string | null;
  avatarUrl?: string | null;
  anonymous?: boolean;
}): Promise<ConversationMember> {
  return api<ConversationMember>(`/collaboration/conversations/${conversationId}/members/${userId}`, {
    method: "PATCH",
    body: JSON.stringify(request),
  });
}

export function kickConversationMember(conversationId: number, userId: number, request: {
  reason?: string;
  ban?: boolean;
  bannedUntil?: string | null;
} = {}): Promise<void> {
  return api<void>(`/collaboration/conversations/${conversationId}/members/${userId}/kick`, {
    method: "POST",
    body: JSON.stringify(request),
  });
}

export function listChatProfiles(): Promise<ChatProfile[]> {
  return api<ChatProfile[]>("/collaboration/chat-profiles", { method: "GET" });
}

export function createChatProfile(request: {
  nickname: string;
  avatarUrl?: string | null;
  description?: string | null;
  defaultProfile?: boolean;
}): Promise<ChatProfile> {
  return api<ChatProfile>("/collaboration/chat-profiles", { method: "POST", body: JSON.stringify(request) });
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

export function uploadCollaborationFile(file: File): Promise<FileAssetResponse> {
  const formData = new FormData();
  formData.append("kind", "ATTACHMENT");
  formData.append("refType", "COLLAB_MESSAGE");
  formData.append("file", file);

  return api<FileAssetResponse>("/file/upload", {
    method: "POST",
    body: formData,
  });
}

export async function downloadCollaborationAttachment(file: MessageAttachmentResponse): Promise<void> {
  const token = getAccessToken();
  const response = await fetch(`${apiBase()}/collaboration/files/${file.fileId}/content`, {
    headers: token ? { Authorization: `Bearer ${token}` } : undefined,
  });

  if (!response.ok) {
    // 서버 envelope 의 message 를 우선 노출 — LOCAL 공유는 소유자 데스크톱이
    // 목록 조회와 클릭 사이에 오프라인이 되면 CONFLICT 안내 문구가 내려온다.
    let message = `다운로드에 실패했습니다 (${response.status})`;
    try {
      const body = (await response.json()) as { message?: string };
      if (body?.message) message = body.message;
    } catch {
      // envelope 이 아닌 응답 — 기본 문구 유지
    }
    throw new Error(message);
  }

  const blob = await response.blob();
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
