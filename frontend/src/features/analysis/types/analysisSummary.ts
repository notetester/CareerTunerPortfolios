export interface AnalysisStats {
  totalApplications: number;
  analyzedApplications: number;
  averageFitScore: number;
  highFitApplications: number;
  readyApplications: number;
}

export interface SkillGap {
  skill: string;
  count: number;
  total: number;
  percentage: number;
}

export interface JobReadiness {
  jobTitle: string;
  readiness: number;
  applicationCount: number;
  trend: "up" | "neutral" | "down";
}

export interface ScorePoint {
  label: string;
  score: number;
}

export interface AnalysisApplicationSummary {
  applicationCaseId: number;
  companyName: string;
  jobTitle: string;
  postingDate: string | null;
  status: string;
  favorite: boolean;
  fitScore: number | null;
  analyzedAt: string | null;
}

export interface CareerAnalysisRun {
  id: number;
  analysisType: string;
  status: "SUCCESS" | "FALLBACK" | "FAILED" | string;
  inputSnapshot: string | null;
  result: string | null;
  model: string | null;
  tokenUsage: number;
  errorMessage: string | null;
  retryable: boolean;
  createdAt: string;
}

export interface InterviewTrend {
  totalSessions: number;
  averageSessionScore: number;
  totalAnswers: number;
  averageAnswerScore: number;
}

/** 반복 강점(자주 활용되는 강점 경험) — 기획 §8.9. matched_skills 집계. */
export interface StrengthTrend {
  skill: string;
  count: number;
  total: number;
  percentage: number;
}

/** 자주 지원하는 직무 분포 — 디자인 분석 §6.10. */
export interface JobDistribution {
  jobTitle: string;
  count: number;
  percentage: number;
  averageFitScore: number | null;
}

/** 자주 개선이 필요한 답변 요소(질문 유형별) — 기획 §8.9 답변의 공통 약점. */
export interface AnswerTheme {
  questionType: string;
  answerCount: number;
  averageScore: number;
  sampleFeedback: string | null;
}

/** 분석 대상 기간과 데이터 수 — 디자인 분석 §6.10. */
export interface AnalysisPeriod {
  from: string | null;
  to: string | null;
  applicationCount: number;
  analyzedCount: number;
  interviewSessionCount: number;
}

/** 월별 평균 적합도 변화. month 는 yyyy-MM. */
export interface MonthlyFitPoint {
  month: string;
  averageScore: number;
  analysisCount: number;
}

export interface TierItem {
  applicationCaseId: number;
  companyName: string;
  jobTitle: string;
  fitScore: number | null;
}

/** 상향/적정/안전 지원 분류(SAFE/MATCH/CHALLENGE). 세 구간이 항상 내려온다. */
export interface ApplicationTier {
  tier: "SAFE" | "MATCH" | "CHALLENGE" | string;
  label: string;
  description: string;
  items: TierItem[];
}

export interface AnalysisSummary {
  stats: AnalysisStats;
  skillGaps: SkillGap[];
  jobReadiness: JobReadiness[];
  scoreHistory: ScorePoint[];
  applications: AnalysisApplicationSummary[];
  recommendedDirections: string[];
  /** 장기 취업 경향 AI 요약(C 담당 AI 16). API 키가 없으면 결정적 mock, 있으면 실제 구조화 분석. */
  trendSummary: string;
  interviewTrend: InterviewTrend;
  strengthTrends: StrengthTrend[];
  jobDistribution: JobDistribution[];
  answerThemes: AnswerTheme[];
  period: AnalysisPeriod;
  monthlyFitTrend: MonthlyFitPoint[];
  applicationTiers: ApplicationTier[];
  analysisRun: CareerAnalysisRun;
}
