export interface AnalyticsPromptTemplate {
  key: string;
  name: string;
  version: string;
  status: string;
  purpose: string;
  inputFields: string[];
  outputFields: string[];
  qualityChecklist: string[];
  riskNotes: string[];
  lastReviewedAt: string;
}
