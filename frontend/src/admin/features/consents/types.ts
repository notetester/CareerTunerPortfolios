export interface AdminConsentView {
  id: number;
  userId: number;
  userEmail?: string | null;
  consentType: string;
  consentVersion?: string | null;
  agreed: boolean;
  agreedAt?: string | null;
  revokedAt?: string | null;
  source?: string | null;
  createdAt?: string | null;
}
