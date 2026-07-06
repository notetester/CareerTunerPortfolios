export const CORRECTION_TABS = ["answer", "cover", "resume", "portfolio"] as const;

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

export const CORRECTION_CREDIT_COST = 2;

export interface CorrectionCreateRequest {
  correctionType: CorrectionType;
  applicationCaseId?: number;
  originalText: string;
  sourceType: "DIRECT_INPUT";
  sourceRefId?: number;
  questionText?: string;
  policyAcknowledgementKey: string;
}

export type CorrectionSubmitRequest = Omit<CorrectionCreateRequest, "policyAcknowledgementKey">;

export interface CorrectionResponse {
  id: number;
  applicationCaseId: number | null;
  correctionType: CorrectionType;
  sourceType: string;
  sourceRefId: number | null;
  originalText: string;
  improvedText: string | null;
  summary: string;
  issues: string[];
  changeReasons: string[];
  suggestions: string[];
  status: string;
  aiUsageLogId: number | null;
  createdAt: string | null;
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
