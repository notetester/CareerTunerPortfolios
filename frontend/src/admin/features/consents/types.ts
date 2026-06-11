export interface AdminConsentView {
  id: number;
  userId: number;
  userEmail?: string | null;
  consentType: string;
  agreed: boolean;
  agreedAt?: string | null;
  revokedAt?: string | null;
  source?: string | null;
  createdAt?: string | null;
}
