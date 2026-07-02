import { api } from "@/app/lib/api";
import { getAccessToken } from "@/app/lib/tokenStore";
import type {
  AttachmentShareMode,
  CollaborationUser,
  ConversationSummaryResponse,
  CreateConversationRequest,
  FileAssetResponse,
  FriendRequestResponse,
  FriendResponse,
  MessageAttachmentResponse,
  MessageResponse,
  SendMessageRequest,
} from "../types/collaboration";

const API_BASE = ((import.meta.env.VITE_API_BASE_URL as string | undefined)?.replace(/\/+$/, "")) || "/api";

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
  const response = await fetch(`${API_BASE}/collaboration/files/${file.fileId}/content`, {
    headers: token ? { Authorization: `Bearer ${token}` } : undefined,
  });

  if (!response.ok) {
    throw new Error(`다운로드에 실패했습니다 (${response.status})`);
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
