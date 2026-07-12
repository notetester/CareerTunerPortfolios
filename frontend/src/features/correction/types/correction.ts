export const CORRECTION_TABS = ["answer", "cover", "resume", "portfolio"] as const;
export const CORRECTION_MIN_CREDIT_COST = 2;
export const CORRECTION_MAX_CREDIT_COST = 5;

export type CorrectionTab = (typeof CORRECTION_TABS)[number];

export type CorrectionType =
  | "INTERVIEW_ANSWER"
  | "SELF_INTRO"
  | "RESUME"
  | "PORTFOLIO";

export const CORRECTION_TYPE_BY_TAB: Record<CorrectionTab, CorrectionType> = {
  answer: "INTERVIEW_ANSWER",
  cover: "SELF_INTRO",
  resume: "RESUME",
  portfolio: "PORTFOLIO",
};

export interface CorrectionCreateRequest {
  correctionType: CorrectionType;
  applicationCaseId?: number;
  originalText: string;
  sourceType: "DIRECT_INPUT" | "INTERVIEW_ANSWER";
  sourceRefId?: number;
  questionText?: string;
  policyAcknowledgementKey: string;
  requestKey: string;
}

export type CorrectionSubmitRequest = Omit<CorrectionCreateRequest, "policyAcknowledgementKey" | "requestKey">;

export interface CorrectionResponse {
  id: number;
  applicationCaseId: number | null;
  correctionType: CorrectionType;
  sourceType: string;
  sourceRefId: number | null;
  originalText: string;
  improvedText: string | null;
  sourceSnapshot?: string | null;
  summary: string;
  issues: string[];
  changeReasons: string[];
  suggestions: string[];
  status: string;
  aiUsageLogId: number | null;
  chargeType?: "TICKET" | "CREDIT" | "SKIPPED" | null;
  chargedCredit?: number;
  totalTokens?: number;
  remainingCredit?: number;
  replayed?: boolean;
  createdAt: string | null;
}

export interface CorrectionInterviewSource {
  sourceRefId: number;
  applicationCaseId: number;
  sessionId: number;
  questionId: number;
  questionText: string;
  originalText: string;
  score: number | null;
  feedback: string | null;
  answeredAt: string | null;
}

export type CorrectionWarmupStatus =
  | "STARTED"
  | "IN_PROGRESS"
  | "ALREADY_WARM"
  | "COOLDOWN"
  | "SKIPPED";

export interface CorrectionWarmupResponse {
  status: CorrectionWarmupStatus;
  model: string;
}
