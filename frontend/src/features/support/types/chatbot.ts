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
  { icon: "KeyRound" as const, text: "비밀번호는 어떻게 변경하나요?" },
  { icon: "CreditCard" as const, text: "요금제 환불 규정이 궁금해요" },
  { icon: "FileText" as const, text: "이력서 분석은 어떻게 시작하나요?" },
] as const;

export const SIDEBAR_SUGGESTIONS = ["결제 영수증 발급", "계정 탈퇴", "알림 설정"] as const;
