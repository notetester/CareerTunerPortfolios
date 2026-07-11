import { api } from "../lib/api";

export interface ConsentView {
  id: number;
  userId: number;
  userEmail?: string;
  consentType: string;
  consentVersion?: string | null;
  agreed: boolean;
  agreedAt?: string | null;
  revokedAt?: string | null;
  source?: string | null;
  createdAt?: string;
}

export interface ConsentStatus {
  termsAgreed: boolean;
  privacyAgreed: boolean;
  aiDataAgreed: boolean;
  resumeAnalysisAgreed: boolean;
  marketingAgreed: boolean;
  requiredConsentsMissing: boolean;
  history: ConsentView[];
}

export interface ConsentRequest {
  termsAgreed: boolean;
  privacyAgreed: boolean;
  aiDataAgreed: boolean;
  resumeAnalysisAgreed: boolean;
  marketingAgreed: boolean;
}

export type ConsentType = "TERMS" | "PRIVACY" | "AI_DATA" | "RESUME_ANALYSIS" | "MARKETING";

export function getMyConsents(): Promise<ConsentStatus> {
  return api<ConsentStatus>("/consents/me", { method: "GET" });
}

export function saveMyConsents(request: ConsentRequest): Promise<ConsentStatus> {
  return api<ConsentStatus>("/consents/me", { method: "POST", body: JSON.stringify(request) });
}

export function revokeAiConsent(): Promise<ConsentStatus> {
  return api<ConsentStatus>("/consents/ai/revoke", { method: "POST" });
}

const consentTypeSlug: Record<ConsentType, string> = {
  TERMS: "terms",
  PRIVACY: "privacy",
  AI_DATA: "ai-data",
  RESUME_ANALYSIS: "resume-analysis",
  MARKETING: "marketing",
};

export function revokeConsent(consentType: ConsentType): Promise<ConsentStatus> {
  return api<ConsentStatus>(`/consents/${consentTypeSlug[consentType]}/revoke`, { method: "POST" });
}
