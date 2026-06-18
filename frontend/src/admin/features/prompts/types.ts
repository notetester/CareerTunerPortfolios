export type AdminBPromptFeature = "job-analysis" | "company-analysis";
export type AdminBPromptPath = "/admin/prompts/job-analysis" | "/admin/prompts/company-analysis";

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

export interface AdminJobAnalysisPromptView extends AdminPromptView {
  feature: "job-analysis";
}

export interface AdminCompanyAnalysisPromptView extends AdminPromptView {
  feature: "company-analysis";
}

export type AdminBPromptView = AdminJobAnalysisPromptView | AdminCompanyAnalysisPromptView;

export interface AdminBPromptResponseByPath {
  "/admin/prompts/job-analysis": AdminJobAnalysisPromptView;
  "/admin/prompts/company-analysis": AdminCompanyAnalysisPromptView;
}

export interface AdminBPromptEndpoint<P extends AdminBPromptPath = AdminBPromptPath> {
  feature: AdminBPromptResponseByPath[P]["feature"];
  path: P;
}

export interface AdminPromptViewFulfilled {
  feature: AdminBPromptFeature;
  path: AdminBPromptPath;
  status: "fulfilled";
  value: AdminBPromptView;
}

export interface AdminPromptViewRejected {
  feature: AdminBPromptFeature;
  path: AdminBPromptPath;
  status: "rejected";
  reason: unknown;
  message: string;
}

export type AdminPromptViewSettledResult = AdminPromptViewFulfilled | AdminPromptViewRejected;

export interface AdminBPromptViewsSettledResponse {
  views: AdminBPromptView[];
  failures: AdminPromptViewRejected[];
  results: AdminPromptViewSettledResult[];
}
