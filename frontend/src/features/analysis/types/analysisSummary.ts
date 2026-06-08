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

export interface AnalysisSummary {
  stats: AnalysisStats;
  skillGaps: SkillGap[];
  jobReadiness: JobReadiness[];
  scoreHistory: ScorePoint[];
  applications: AnalysisApplicationSummary[];
  recommendedDirections: string[];
}
