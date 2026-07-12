import { api, apiBlob } from "../lib/api";
import type { AiModelChoice } from "@/app/components/ai/ModelPicker";

/** 프로필 AI 모델 선택 쿼리(AUTO=현행 폴백이라 파라미터 생략). */
function modelQuery(model: AiModelChoice): string {
  return model && model !== "AUTO" ? `?model=${model}` : "";
}

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

export interface ProfilePortfolioFile {
  id: number;
  kind: "PORTFOLIO";
  refType: string;
  refId: number;
  originalName: string;
  contentType?: string | null;
  sizeBytes?: number | null;
  contentUrl: string;
  createdAt?: string | null;
}

/** 포트폴리오를 업로드와 동시에 현재 사용자 프로필에 연결한다. */
export function uploadProfilePortfolioFile(file: File): Promise<ProfilePortfolioFile> {
  const fd = new FormData();
  fd.append("file", file);
  return api<ProfilePortfolioFile>("/profile/portfolio-files/upload", { method: "POST", body: fd });
}

/** 기존 업로드 파일을 프로필 PORTFOLIO 자산으로 재확인/입양한다. */
export function linkProfilePortfolioFiles(fileIds: number[]): Promise<ProfilePortfolioFile[]> {
  return api<ProfilePortfolioFile[]>("/profile/portfolio-files/link", {
    method: "POST",
    body: JSON.stringify({ fileIds }),
  });
}

export function listProfilePortfolioFiles(): Promise<ProfilePortfolioFile[]> {
  return api<ProfilePortfolioFile[]>("/profile/portfolio-files", { method: "GET" });
}

/** 현재 프로필에 연결된 포트폴리오 파일을 메타데이터와 저장소에서 함께 삭제한다. */
export function deleteProfilePortfolioFile(fileId: number): Promise<void> {
  return api<void>(`/profile/portfolio-files/${fileId}`, { method: "DELETE" });
}

/** 아직 어느 도메인에도 연결되지 않은 본인 업로드를 정리한다. */
export function deleteUnlinkedProfileFile(fileId: number): Promise<void> {
  return api<void>(`/file/${fileId}`, { method: "DELETE" });
}

export async function downloadProfilePortfolioFile(file: ProfilePortfolioFile): Promise<void> {
  const objectUrl = URL.createObjectURL(await apiBlob(`/file/${file.id}/content`, { method: "GET" }));
  const anchor = document.createElement("a");
  anchor.href = objectUrl;
  anchor.download = file.originalName;
  anchor.click();
  window.setTimeout(() => URL.revokeObjectURL(objectUrl), 10_000);
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

export function summarizeProfile(model: AiModelChoice = "AUTO"): Promise<ProfileAiResponse> {
  return api<ProfileAiResponse>(`/profile/ai/summary${modelQuery(model)}`, { method: "POST" });
}

export interface ProfileAiAnalysis {
  hasAnalysis: boolean;
  summary: string | null;
  strengths: string[];
  gaps: string[];
  recommendations: string[];
  extractedSkills: string[];
  jobFamily: string | null;
  jobFamilyLabel: string | null;
  completenessScore: number | null;
  aiScore: number | null;
  criteria: ProfileCriterionScore[] | null;
  qualityWarnings: string[];
  analyzedAt: string | null;
}

/** 저장된 프로필 AI 분석 조회(새로고침 후에도 최근 분석 표시). 분석 이력 없으면 hasAnalysis=false. */
export function getProfileAiAnalysis(): Promise<ProfileAiAnalysis> {
  return api<ProfileAiAnalysis>("/profile/ai-analysis");
}

export function extractProfileSkills(model: AiModelChoice = "AUTO"): Promise<ProfileAiResponse> {
  return api<ProfileAiResponse>(`/profile/ai/skills${modelQuery(model)}`, { method: "POST" });
}

export function diagnoseProfileCompleteness(model: AiModelChoice = "AUTO"): Promise<ProfileCompleteness> {
  return api<ProfileCompleteness>(`/profile/ai/completeness${modelQuery(model)}`, { method: "POST" });
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

/** PENDING 이 끝날 때까지 폴링. 원격 Ollama 콜드스타트 대비 기본 ~4분. */
export async function pollProfileAnalyze(
  jobId: string,
  options: { intervalMs?: number; maxAttempts?: number } = {},
): Promise<ProfileAnalyzeResponse> {
  const intervalMs = options.intervalMs ?? 2000;
  const maxAttempts = options.maxAttempts ?? 120;
  for (let i = 0; i < maxAttempts; i++) {
    const res = await getProfileAnalyze(jobId);
    if (res.status !== "PENDING") return res;
    await new Promise((r) => setTimeout(r, intervalMs));
  }
  return {
    jobId,
    status: "FAILED",
    errorMessage: "구조화 분석 시간이 초과됐어요. 원문은 저장됐고, 폼은 직접 채워 주세요.",
  };
}

/** draft 에 반영할 비어 있지 않은 구조 필드가 있는지. */
export function draftHasStructuredFields(draft: ProfileAnalyzeDraft | null | undefined): boolean {
  if (!draft) return false;
  const n = (v: unknown) => (Array.isArray(v) ? v.length : 0);
  return (
    n(draft.education) +
      n(draft.career) +
      n(draft.projects) +
      n(draft.skills) +
      n(draft.portfolioLinks) >
    0
  );
}

/** 프로필 문서 첨부 허용 확장자/MIME (.doc 제외). */
export const PROFILE_DOC_ACCEPT =
  ".txt,.md,.pdf,.docx,text/plain,text/markdown,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document";
