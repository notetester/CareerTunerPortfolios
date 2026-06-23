export interface SiteLink {
  url: string;
  label: string;
}

export interface ChatEvidence {
  id: string;
  type: "도움말" | "가이드" | "공지" | "FAQ";
  title: string;
  snippet: string;
  url: string;
}

export interface ChatMessage {
  id: string;
  role: "user" | "bot";
  text: string;
  evidence: ChatEvidence[];
  links: SiteLink[];
  quickReplies: string[];
  ttsState: "idle" | "playing" | "paused";
  ttsProgress: number;
  timestamp: number;
}

export type BotStatus = "idle" | "thinking" | "answered" | "not_found" | "disconnected";
export type VoiceState = "idle" | "requesting" | "listening" | "denied";

export interface ChatSession {
  id: string;
  title: string;
  lastMessage: string;
  meta: string;
  updatedAt: number;
}

export const SUGGESTED_QUESTIONS = [
  { icon: "FileText" as const, text: "처음인데 어떻게 시작해요?" },
  { icon: "CreditCard" as const, text: "돈 돌려받고 싶어요" },
  { icon: "KeyRound" as const, text: "모의면접은 어떻게 진행되나요?" },
] as const;

export const SIDEBAR_SUGGESTIONS = ["탈퇴하고 싶어요", "무료로 어디까지 쓸 수 있나요?", "게시글 작성 방법"] as const;
