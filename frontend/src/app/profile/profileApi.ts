import { api } from "../lib/api";

export interface UserProfile {
  id?: number;
  userId?: number;
  desiredJob?: string | null;
  desiredIndustry?: string | null;
  education?: unknown;
  career?: unknown;
  projects?: unknown;
  skills?: unknown;
  certificates?: unknown;
  languages?: unknown;
  portfolioLinks?: unknown;
  resumeText?: string | null;
  selfIntro?: string | null;
  preferences?: unknown;
  updatedAt?: string;
}

export interface ProfileAiResponse {
  featureType: string;
  summary: string;
  extractedSkills: string[];
  strengths: string[];
  gaps: string[];
  recommendations: string[];
  completenessScore: number;
  jobFamily?: string;
  jobFamilyLabel?: string;
  criteria?: ProfileCriterionScore[];
  model?: string;
  status?: string;
}

export interface ProfileCompleteness {
  score: number;
  completed: string[];
  missing: string[];
  recommendations: string[];
  jobFamily?: string;
  jobFamilyLabel?: string;
  criteria?: ProfileCriterionScore[];
  model?: string;
  status?: string;
}

export interface ProfileCriterionScore {
  criterion: string;
  label: string;
  rawScore: number;
  weight: number;
  weightedScore: number;
  evidence: string;
  improvement: string;
}

export function getProfile(): Promise<UserProfile> {
  return api<UserProfile>("/profile", { method: "GET" });
}

export function saveProfile(profile: UserProfile): Promise<UserProfile> {
  return api<UserProfile>("/profile", { method: "PUT", body: JSON.stringify(profile) });
}

export function summarizeProfile(): Promise<ProfileAiResponse> {
  return api<ProfileAiResponse>("/profile/ai/summary", { method: "POST" });
}

export function extractProfileSkills(): Promise<ProfileAiResponse> {
  return api<ProfileAiResponse>("/profile/ai/skills", { method: "POST" });
}

export function diagnoseProfileCompleteness(): Promise<ProfileCompleteness> {
  return api<ProfileCompleteness>("/profile/ai/completeness", { method: "POST" });
}
