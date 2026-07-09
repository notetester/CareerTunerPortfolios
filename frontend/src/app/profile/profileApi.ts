import { api } from "../lib/api";

/** 프로필 문서 업로드 — 기존 POST /file/upload 재사용. */
export function uploadProfileFile(file: File, kind: "RESUME" | "ATTACHMENT" = "RESUME") {
  const fd = new FormData();
  fd.append("file", file);
  fd.append("kind", kind);
  return api<{ id: number; originalName?: string }>("/file/upload", { method: "POST", body: fd });
}

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
  aiScore?: number;
  qualityPenalty?: number;
  qualityWarnings?: string[];
  qualityRecommendations?: string[];
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
  aiScore?: number;
  qualityPenalty?: number;
  qualityWarnings?: string[];
  qualityRecommendations?: string[];
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

/** 문서 import 대상 필드. */
export type ProfileImportTarget = "RESUME_TEXT" | "SELF_INTRO";

export interface ProfileImportResponse {
  profile: UserProfile;
  truncated: boolean;
}

/** 구조화 분석 초안 — DB 미커밋. Profile.tsx 폼 키와 일치. */
export interface ProfileAnalyzeDraft {
  education?: unknown;
  career?: unknown;
  projects?: unknown;
  skills?: string[] | null;
  portfolioLinks?: string[] | null;
}

export interface ProfileAnalyzeResponse {
  jobId: string;
  status: "PENDING" | "DONE" | "FAILED" | string;
  draft?: ProfileAnalyzeDraft | null;
  errorMessage?: string | null;
}

/** 업로드된 fileId 텍스트를 resume_text / self_intro 에 덤프. */
export function importProfileDocument(
  fileId: number,
  target: ProfileImportTarget,
): Promise<ProfileImportResponse> {
  return api<ProfileImportResponse>("/profile/import", {
    method: "POST",
    body: JSON.stringify({ fileId, target }),
  });
}

/** 이력서 구조화 분석 비동기 발사 (202). */
export function startProfileAnalyze(fileId: number): Promise<ProfileAnalyzeResponse> {
  return api<ProfileAnalyzeResponse>("/profile/import/analyze", {
    method: "POST",
    body: JSON.stringify({ fileId }),
  });
}

/** 구조화 분석 작업 조회. */
export function getProfileAnalyze(jobId: string): Promise<ProfileAnalyzeResponse> {
  return api<ProfileAnalyzeResponse>(`/profile/import/analyze/${encodeURIComponent(jobId)}`, {
    method: "GET",
  });
}

/** PENDING 이 끝날 때까지 폴링. */
export async function pollProfileAnalyze(
  jobId: string,
  options: { intervalMs?: number; maxAttempts?: number } = {},
): Promise<ProfileAnalyzeResponse> {
  const intervalMs = options.intervalMs ?? 1500;
  const maxAttempts = options.maxAttempts ?? 80;
  for (let i = 0; i < maxAttempts; i++) {
    const res = await getProfileAnalyze(jobId);
    if (res.status !== "PENDING") return res;
    await new Promise((r) => setTimeout(r, intervalMs));
  }
  return {
    jobId,
    status: "FAILED",
    errorMessage: "구조화 분석은 실패했어요. 폼을 직접 채워주세요.",
  };
}

/** 프로필 문서 첨부 허용 확장자/MIME (.doc 제외). */
export const PROFILE_DOC_ACCEPT =
  ".txt,.md,.pdf,.docx,text/plain,text/markdown,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document";
