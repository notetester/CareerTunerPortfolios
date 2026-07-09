// 개인 차단/허용 정책 REST 클라이언트 (backend /api/privacy/**, docs/PERSONAL_BLOCK_POLICY.md §6).
import { api } from "@/app/lib/api";
import type {
  ConversationBlockRequest,
  ConversationBlockResponse,
  IpBlockResponse,
  PrivacyPolicyResponse,
  PrivacyPolicyUpdateRequest,
  UserBlockByContentRequest,
  UserBlockRequest,
  UserBlockResponse,
  UserBlockUpdateRequest,
} from "../types";

/* ── 관계별 정책 ── */

export function getPrivacyPolicy(): Promise<PrivacyPolicyResponse> {
  return api<PrivacyPolicyResponse>("/privacy/policy", { method: "GET" });
}

/** 부분 갱신 — 포함되지 않은 키는 유지, ""(빈 문자열)=명시값 제거(상위 따름). */
export function updatePrivacyPolicy(request: PrivacyPolicyUpdateRequest): Promise<PrivacyPolicyResponse> {
  return api<PrivacyPolicyResponse>("/privacy/policy", {
    method: "PUT",
    body: JSON.stringify(request),
  });
}

/* ── 계정 차단 ── */

export function listUserBlocks(): Promise<UserBlockResponse[]> {
  return api<UserBlockResponse[]>("/privacy/blocks/users", { method: "GET" });
}

export function blockUser(request: UserBlockRequest): Promise<UserBlockResponse> {
  return api<UserBlockResponse>("/privacy/blocks/users", {
    method: "POST",
    body: JSON.stringify(request),
  });
}

/**
 * 콘텐츠 id(게시글/댓글)로 작성자 차단 — 익명 콘텐츠용(작성자 id 가 클라이언트에 없음).
 * 익명 콘텐츠면 응답의 blockedUserName 이 "익명 작성자 (게시글 #id)" 라벨로 마스킹된다.
 */
export function blockUserByContent(request: UserBlockByContentRequest): Promise<UserBlockResponse> {
  return api<UserBlockResponse>("/privacy/blocks/users/by-content", {
    method: "POST",
    body: JSON.stringify(request),
  });
}

export function updateUserBlock(blockId: number, request: UserBlockUpdateRequest): Promise<UserBlockResponse> {
  return api<UserBlockResponse>(`/privacy/blocks/users/${blockId}`, {
    method: "PUT",
    body: JSON.stringify(request),
  });
}

/** 차단 해제 — 파생 IP 차단도 함께 해제된다. */
export function unblockUser(blockId: number): Promise<void> {
  return api<void>(`/privacy/blocks/users/${blockId}`, { method: "DELETE" });
}

/* ── IP 차단 (원본 IP 비노출 — 조회/해제만) ── */

export function listIpBlocks(): Promise<IpBlockResponse[]> {
  return api<IpBlockResponse[]>("/privacy/blocks/ips", { method: "GET" });
}

export function deleteIpBlock(ipBlockId: number): Promise<void> {
  return api<void>(`/privacy/blocks/ips/${ipBlockId}`, { method: "DELETE" });
}

/* ── 채팅방 차단 ── */

export function listConversationBlocks(): Promise<ConversationBlockResponse[]> {
  return api<ConversationBlockResponse[]>("/privacy/blocks/conversations", { method: "GET" });
}

export function blockConversation(request: ConversationBlockRequest): Promise<ConversationBlockResponse> {
  return api<ConversationBlockResponse>("/privacy/blocks/conversations", {
    method: "POST",
    body: JSON.stringify(request),
  });
}

/** flags[플래그키] = "allow" | "block" | ""(기본값 복원). */
export function updateConversationBlock(blockId: number, flags: Record<string, string>): Promise<ConversationBlockResponse> {
  return api<ConversationBlockResponse>(`/privacy/blocks/conversations/${blockId}`, {
    method: "PUT",
    body: JSON.stringify(flags),
  });
}

export function unblockConversation(blockId: number): Promise<void> {
  return api<void>(`/privacy/blocks/conversations/${blockId}`, { method: "DELETE" });
}
