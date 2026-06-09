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
  analysisRun: CareerAnalysisRun;
}
