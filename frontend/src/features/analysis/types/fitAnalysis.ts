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

/** 요구조건-스펙 비교 매트릭스 한 행. */
export interface FitConditionMatch {
  condition: string;
  conditionType: "REQUIRED" | "PREFERRED" | string;
  matchStatus: "MET" | "PARTIAL" | "UNMET" | string;
  evidence: string;
}

/** 분석 신뢰도(입력 데이터 상태 기반 결정적 계산). */
export interface FitAnalysisConfidence {
  level: "HIGH" | "MEDIUM" | "LOW" | string;
  reasons: string[];
}

/** "지원해도 되는가?" 최종 판단 카드. */
export interface FitApplyDecision {
  decision: "APPLY" | "COMPLEMENT" | "HOLD" | string;
  reasons: string[];
  actions: string[];
}

/** 재분석 히스토리 항목(최신순). 첫 분석은 previousScore/scoreDelta 가 null. */
export interface FitAnalysisHistoryEntry {
  id: number;
  fitScore: number | null;
  previousScore: number | null;
  scoreDelta: number | null;
  gainedSkills: string[];
  resolvedGaps: string[];
  newGaps: string[];
  model: string | null;
  status: string | null;
  createdAt: string | null;
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
  conditionMatrix: string | null;
  analysisConfidence: string | null;
  applyDecision: string | null;
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

// 점수 구간 설명(기획 §8.6 하이브리드 표기 확장): 단순 라벨이 아니라 구간의 의미를 함께 안내한다.
export function scoreBandDescription(score: number | null | undefined) {
  const value = score ?? 0;
  if (value >= 85) return "강한 적합 구간입니다. 바로 지원을 진행하고 면접 준비에 집중하세요.";
  if (value >= 70) return "지원 가능 구간입니다. 부족 역량 1~2개를 지원서·면접 답변에서 보완하세요.";
  if (value >= 50) return "보완 필요 구간입니다. 핵심 부족 역량을 해결한 뒤 재분석을 권장합니다.";
  return "준비 부족 구간입니다. 다른 공고를 우선 검토하거나 기본 요구 역량부터 채우세요.";
}
