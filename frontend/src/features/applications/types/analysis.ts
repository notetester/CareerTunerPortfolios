export interface JobAnalysis {
  id: number;
  applicationCaseId: number;
  jobPostingId: number | null;
  jobPostingRevision: number | null;
  employmentType: string | null;
  experienceLevel: string | null;
  requiredSkills: string | null;
  preferredSkills: string | null;
  duties: string | null;
  qualifications: string | null;
  difficulty: string | null;
  summary: string | null;
  evidence: string | null;
  ambiguousConditions: string | null;
  confirmedAt: string | null;
  adminMemo: string | null;
  createdAt: string;
}

export interface CompanyAnalysis {
  id: number;
  applicationCaseId: number;
  jobPostingId: number | null;
  jobPostingRevision: number | null;
  companySummary: string | null;
  recentIssues: string | null;
  industry: string | null;
  competitors: string | null;
  interviewPoints: string | null;
  sources: string | null;
  verifiedFacts: string | null;
  aiInferences: string | null;
  sourceType: string | null;
  checkedAt: string | null;
  refreshRecommendedAt: string | null;
  confirmedAt: string | null;
  adminMemo: string | null;
  createdAt: string;
}

export interface BAnalysisFailureLog {
  featureType: string;
  errorMessage: string | null;
  createdAt: string;
}

export type JsonArrayOrText =
  | { kind: "empty" }
  | { kind: "list"; items: string[] }
  | { kind: "text"; text: string };

export interface JobAnalysisReviewRequest {
  employmentType?: string | null;
  experienceLevel?: string | null;
  requiredSkills?: string | null;
  preferredSkills?: string | null;
  duties?: string | null;
  qualifications?: string | null;
  difficulty?: string | null;
  summary?: string | null;
  confirmed?: boolean;
}

export interface CompanyAnalysisReviewRequest {
  companySummary?: string | null;
  recentIssues?: string | null;
  industry?: string | null;
  competitors?: string | null;
  interviewPoints?: string | null;
  sources?: string | null;
  confirmed?: boolean;
}

function stringifyJsonArrayItem(item: unknown): string {
  if (typeof item === "string") return item.trim();
  if (typeof item === "number" || typeof item === "boolean") return String(item);
  if (item == null) return "";
  try {
    return JSON.stringify(item);
  } catch {
    return String(item);
  }
}

export function parseJsonArrayOrText(value: string | null | undefined): JsonArrayOrText {
  const trimmed = value?.trim();
  if (!trimmed) return { kind: "empty" };

  try {
    const parsed = JSON.parse(trimmed) as unknown;
    if (Array.isArray(parsed)) {
      const items = parsed.map(stringifyJsonArrayItem).filter(Boolean);
      return items.length > 0 ? { kind: "list", items } : { kind: "empty" };
    }
  } catch {
    return { kind: "text", text: trimmed };
  }

  return { kind: "text", text: trimmed };
}

export function parseJsonStringArray(value: string | null | undefined): string[] {
  const parsed = parseJsonArrayOrText(value);
  if (parsed.kind === "list") return parsed.items;
  if (parsed.kind === "text") return [parsed.text];
  return [];
}

export function getDifficultyLabel(value: string | null | undefined): string {
  switch (value) {
    case "EASY":
      return "쉬움";
    case "NORMAL":
      return "보통";
    case "HARD":
      return "높음";
    default:
      return value ?? "미정";
  }
}
