export interface AdminPromptView {
  feature: string;
  name: string;
  version: string;
  purpose: string;
  systemPrompt: string;
  schemaSummary: string;
  evaluationCriteria?: AdminPromptCriterion[];
  weightProfiles?: AdminPromptWeightProfile[];
}

export interface AdminPromptCriterion {
  criterion: string;
  label: string;
  description: string;
}

export interface AdminPromptWeightProfile {
  jobFamily: string;
  label: string;
  description: string;
  weights: Record<string, number>;
}
