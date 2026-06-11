import { api } from "../lib/api";

export interface ConsentView {
  id: number;
  userId: number;
  userEmail?: string;
  consentType: string;
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
  marketingAgreed: boolean;
  requiredConsentsMissing: boolean;
  history: ConsentView[];
}

export interface ConsentRequest {
  termsAgreed: boolean;
  privacyAgreed: boolean;
  aiDataAgreed: boolean;
  marketingAgreed: boolean;
}

export function getMyConsents(): Promise<ConsentStatus> {
  return api<ConsentStatus>("/consents/me", { method: "GET" });
}

export function saveMyConsents(request: ConsentRequest): Promise<ConsentStatus> {
  return api<ConsentStatus>("/consents/me", { method: "POST", body: JSON.stringify(request) });
}

export function revokeAiConsent(): Promise<ConsentStatus> {
  return api<ConsentStatus>("/consents/ai/revoke", { method: "POST" });
}
