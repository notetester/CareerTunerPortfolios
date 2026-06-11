export interface FitAnalysisApplication {
  id: number;
  companyName: string;
  jobTitle: string;
  postingDate: string | null;
  status: string;
  favorite: boolean;
  updatedAt: string | null;
}

export interface FitGapRecommendation {
  skill: string;
  category: "REQUIRED_MISSING" | "PREFERRED_GAP" | "LONG_TERM_GROWTH" | string;
  priority: "HIGH" | "MEDIUM" | "LOW" | string;
  reason: string;
}

export interface FitCertificateRecommendation {
  name: string;
  priority: "HIGH" | "MEDIUM" | "LOW" | string;
  reason: string;
}

export interface FitAnalysisLearningTask {
  id: number;
  fitAnalysisId: number;
  skill: string;
  title: string;
  practiceTask: string;
  expectedDuration: string;
  priority: "HIGH" | "MEDIUM" | "LOW" | string;
  sortOrder: number;
  completed: boolean;
  completedAt: string | null;
}

export interface FitAnalysisDetail {
  id: number;
  applicationCaseId: number;
  fitScore: number | null;
  matchedSkills: string | null;
  missingSkills: string | null;
  recommendedStudy: string | null;
  recommendedCertificates: string | null;
  strategy: string | null;
  sourceSnapshot: string | null;
  scoreBasis: string | null;
  gapRecommendations: string | null;
  certificateRecommendations: string | null;
  strategyActions: string | null;
  model: string | null;
  status: string | null;
  errorMessage: string | null;
  createdAt: string | null;
  application: FitAnalysisApplication;
  learningTasks: FitAnalysisLearningTask[];
}

export function parseJsonValue<T>(value: string | null | undefined, fallback: T): T {
  if (!value) return fallback;
  try {
    return JSON.parse(value) as T;
  } catch {
    return fallback;
  }
}

export function parseJsonList(value: string | null | undefined): string[] {
  if (!value) return [];
  try {
    const parsed = JSON.parse(value);
    if (Array.isArray(parsed)) {
      return parsed.map((item) => String(item));
    }
  } catch {
    return value
      .split(",")
      .map((item) => item.trim())
      .filter(Boolean);
  }
  return [];
}

// 적합도 하이브리드 표기(기획 §8.6: "적합도 82점 / 높음"처럼 점수와 구간을 함께 표기).
// 구간 라벨은 기획 표기 방식을 따른다: 높음 / 보완 필요 / 준비 부족.
export function scoreTone(score: number | null | undefined) {
  const value = score ?? 0;
  if (value >= 70) return { text: "text-green-600", bg: "bg-green-100", label: "높음" };
  if (value >= 50) return { text: "text-amber-600", bg: "bg-amber-100", label: "보완 필요" };
  return { text: "text-red-500", bg: "bg-red-100", label: "준비 부족" };
}
