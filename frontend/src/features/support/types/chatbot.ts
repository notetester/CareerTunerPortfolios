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

/* ── 오케스트레이터 인테이크 메타(칩 렌더용) ── */
export interface IntakeCaseCandidate {
  id: number;
  companyName: string;
  jobTitle: string;
  status?: string;
}

export interface IntakeModeOption {
  code: string;
  label: string;
}

/** 봇 말풍선에 딸려오는 ③ 인테이크 한 턴 메타. nextAsk 가 가리키는 칩만 채워진다. */
export interface IntakeStepMeta {
  ready: boolean;
  nextAsk: "CASE" | "MODE" | null;
  candidates: IntakeCaseCandidate[];
  modes: IntakeModeOption[];
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
  /** ③ 인테이크 턴에서만 set — 지원 건/면접 모드 칩 렌더에 쓴다. */
  intake?: IntakeStepMeta;
  /** 추천 후기 압축 요약 칩 — agentPath 턴에서 검색된 글이 2개 이상일 때만 set. */
  summaryChip?: { label: string; postIds: number[] };
  /** 면접 복귀 결과 카드 — 면접 완료 후 챗봇 복귀 시 재조회한 리포트(실값만). */
  interviewReport?: InterviewReportCard;
}

/** 면접 결과 카드 데이터 — 백엔드 InterviewReportResponse 에서 필요한 필드만(순위/상위% 없음). */
export interface InterviewReportCard {
  sessionId: number;
  caseId: number | null;
  totalScore: number;
  questionCount: number;
  durationLabel: string | null;
  categories: { label: string; score: number }[];
  summaryFeedback: string[];
}

export type BotStatus = "idle" | "thinking" | "answered" | "not_found" | "disconnected";
export type VoiceState = "idle" | "requesting" | "listening" | "denied";

export interface ChatSession {
  id: string;
  title: string;
  lastMessage: string;
  meta: string;
  updatedAt: number;
  /** 면접 모드 코드(BASIC/JOB/…). 슬롯 미설정 세션은 null — 모드 배지 생략. */
  mode?: string | null;
}

export const SUGGESTED_QUESTIONS = [
  { icon: "FileText" as const, text: "처음인데 어떻게 시작해요?" },
  { icon: "CreditCard" as const, text: "돈 돌려받고 싶어요" },
  { icon: "KeyRound" as const, text: "모의면접은 어떻게 진행되나요?" },
] as const;

export const SIDEBAR_SUGGESTIONS = ["탈퇴하고 싶어요", "무료로 어디까지 쓸 수 있나요?", "게시글 작성 방법"] as const;
