import { api } from "@/app/lib/api";
import type {
  AccountInfo,
  ConversationProfile,
  NicknameProfile,
  NicknameProfilePayload,
  ResumeDetail,
  ResumeDetailPayload,
} from "../types/nicknameProfile";

// ── 복수 닉네임 프로필 ──

export function listNicknameProfiles(): Promise<NicknameProfile[]> {
  return api<NicknameProfile[]>("/nicknames", { method: "GET" });
}

export function createNicknameProfile(payload: NicknameProfilePayload): Promise<NicknameProfile> {
  return api<NicknameProfile>("/nicknames", { method: "POST", body: JSON.stringify(payload) });
}

export function updateNicknameProfile(id: number, payload: NicknameProfilePayload): Promise<NicknameProfile> {
  return api<NicknameProfile>(`/nicknames/${id}`, { method: "PUT", body: JSON.stringify(payload) });
}

export function deleteNicknameProfile(id: number): Promise<void> {
  return api<void>(`/nicknames/${id}`, { method: "DELETE" });
}

export function setDefaultNicknameProfile(id: number): Promise<NicknameProfile> {
  return api<NicknameProfile>(`/nicknames/${id}/default`, { method: "POST" });
}

/** 닉네임 전역 사용 가능 여부. excludeProfileId 는 수정 시 자기 자신 제외. */
export function checkNicknameAvailability(nickname: string, excludeProfileId?: number): Promise<boolean> {
  const params = new URLSearchParams({ nickname });
  if (excludeProfileId != null) params.set("excludeProfileId", String(excludeProfileId));
  return api<boolean>(`/nicknames/availability?${params.toString()}`, { method: "GET" });
}

// ── 채팅방 전용 프로필 ──

export function getConversationProfile(conversationId: number): Promise<ConversationProfile> {
  return api<ConversationProfile>(`/nicknames/conversations/${conversationId}`, { method: "GET" });
}

export function setConversationProfile(
  conversationId: number,
  nicknameProfileId: number | null,
): Promise<ConversationProfile> {
  return api<ConversationProfile>(`/nicknames/conversations/${conversationId}`, {
    method: "PUT",
    body: JSON.stringify({ nicknameProfileId, anonymous: nicknameProfileId == null }),
  });
}

// ── 계정 정보 ──

export function getAccountInfo(): Promise<AccountInfo> {
  return api<AccountInfo>("/account", { method: "GET" });
}

export function setLoginId(loginId: string): Promise<AccountInfo> {
  return api<AccountInfo>("/account/login-id", { method: "POST", body: JSON.stringify({ loginId }) });
}

export function setPhone(phone: string): Promise<AccountInfo> {
  return api<AccountInfo>("/account/phone", { method: "POST", body: JSON.stringify({ phone }) });
}

export function requestEmailRegistration(email: string): Promise<void> {
  return api<void>("/account/email-registration", { method: "POST", body: JSON.stringify({ email }) });
}

// ── 이력서 상세 스펙 ──

export function getResumeDetail(): Promise<ResumeDetail> {
  return api<ResumeDetail>("/resume-detail", { method: "GET" });
}

export function saveResumeDetail(payload: ResumeDetailPayload): Promise<ResumeDetail> {
  return api<ResumeDetail>("/resume-detail", { method: "PUT", body: JSON.stringify(payload) });
}
