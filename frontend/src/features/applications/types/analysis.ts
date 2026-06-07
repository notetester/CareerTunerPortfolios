export interface JobAnalysis {
  id: number;
  applicationCaseId: number;
  employmentType: string | null;
  experienceLevel: string | null;
  requiredSkills: string | null;
  preferredSkills: string | null;
  duties: string | null;
  qualifications: string | null;
  difficulty: string | null;
  summary: string | null;
  createdAt: string;
}

export interface CompanyAnalysis {
  id: number;
  applicationCaseId: number;
  companySummary: string | null;
  recentIssues: string | null;
  industry: string | null;
  competitors: string | null;
  interviewPoints: string | null;
  sources: string | null;
  createdAt: string;
}

export function parseJsonStringArray(value: string | null | undefined): string[] {
  if (!value) return [];
  try {
    const parsed = JSON.parse(value) as unknown;
    if (Array.isArray(parsed)) {
      return parsed
        .map((item) => String(item).trim())
        .filter(Boolean);
    }
  } catch {
    return [value].filter(Boolean);
  }
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
