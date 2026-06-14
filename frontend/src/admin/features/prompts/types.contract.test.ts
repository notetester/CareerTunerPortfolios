import type {
  AdminBPromptEndpoint,
  AdminBPromptFeature,
  AdminBPromptResponseByPath,
  AdminBPromptViewsSettledResponse,
  AdminCompanyAnalysisPromptView,
  AdminJobAnalysisPromptView,
  AdminPromptViewRejected,
  AdminPromptViewSettledResult,
} from "./types";

const promptFeature: AdminBPromptFeature = "job-analysis";

const jobAnalysisPromptResponse: AdminJobAnalysisPromptView = {
  feature: "job-analysis",
  name: "Job analysis prompt",
  version: "b-v1",
  purpose: "Extract job posting requirements.",
  systemPrompt: "You analyze job postings.",
  schemaSummary: "employmentType, requiredSkills[], preferredSkills[]",
};

const companyAnalysisPromptResponse: AdminCompanyAnalysisPromptView = {
  feature: "company-analysis",
  name: "Company analysis prompt",
  version: "b-v1",
  purpose: "Summarize company facts for interview preparation.",
  systemPrompt: "You analyze company information.",
  schemaSummary: "companySummary, industry, verifiedFacts[], aiInferences[]",
};

const jobAnalysisEndpoint: AdminBPromptEndpoint<"/admin/prompts/job-analysis"> = {
  feature: "job-analysis",
  path: "/admin/prompts/job-analysis",
};

const companyAnalysisEndpoint: AdminBPromptEndpoint<"/admin/prompts/company-analysis"> = {
  feature: "company-analysis",
  path: "/admin/prompts/company-analysis",
};

const promptResponsesByPath: AdminBPromptResponseByPath = {
  "/admin/prompts/job-analysis": jobAnalysisPromptResponse,
  "/admin/prompts/company-analysis": companyAnalysisPromptResponse,
};

const rejectedPromptView: AdminPromptViewRejected = {
  feature: "company-analysis",
  path: "/admin/prompts/company-analysis",
  status: "rejected",
  reason: new Error("Prompt unavailable"),
  message: "Prompt unavailable",
};

const settledPromptResults: AdminPromptViewSettledResult[] = [
  {
    feature: "job-analysis",
    path: "/admin/prompts/job-analysis",
    status: "fulfilled",
    value: jobAnalysisPromptResponse,
  },
  rejectedPromptView,
];

const settledPromptResponse: AdminBPromptViewsSettledResponse = {
  views: [jobAnalysisPromptResponse],
  failures: [rejectedPromptView],
  results: settledPromptResults,
};

void promptFeature;
void jobAnalysisEndpoint;
void companyAnalysisEndpoint;
void promptResponsesByPath;
void settledPromptResponse;
