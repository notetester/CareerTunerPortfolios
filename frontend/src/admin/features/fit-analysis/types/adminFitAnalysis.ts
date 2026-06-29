export interface AdminFitAnalysisListItem {
  id: number;
  applicationCaseId: number;
  userName: string;
  userEmail: string;
  companyName: string;
  jobTitle: string;
  applicationStatus: string;
  favorite: boolean;
  fitScore: number | null;
  matchedSkills: string[];
  missingSkills: string[];
  model: string | null;
  promptVersion: string | null;
  status: string;
  errorMessage: string | null;
  createdAt: string;
  memoCount: number;
  latestMemoAt: string | null;
  /** REANALYSIS 운영 메모 보유 여부(재분석 요청 상태). */
  reanalysisRequested: boolean;
  /** review-first evidence gate(R3). R3 이전 분석은 null. PASSED/REVIEW_REQUIRED/REJECTED. */
  gateStatus: string | null;
  needsHumanReview: boolean;
  gateReasonCount: number;
  gateMaxSeverity: string | null;
}

export interface AdminFitAnalysisMemo {
  id: number;
  fitAnalysisId: number;
  adminUserId: number;
  adminName: string;
  adminEmail: string;
  memoType: string;
  content: string;
  createdAt: string;
  updatedAt: string;
}

export interface AdminFitAnalysisLearningTask {
  id: number;
  fitAnalysisId: number;
  skill: string;
  title: string;
  practiceTask: string;
  expectedDuration: string;
  priority: string;
  sortOrder: number;
  completed: boolean;
  completedAt: string | null;
}

export interface AdminFitAnalysisDetail {
  id: number;
  applicationCaseId: number;
  userId: number;
  userName: string;
  userEmail: string;
  companyName: string;
  jobTitle: string;
  applicationStatus: string;
  favorite: boolean;
  fitScore: number | null;
  matchedSkills: string[];
  missingSkills: string[];
  recommendedStudy: string[];
  recommendedCertificates: string[];
  strategy: string | null;
  sourceSnapshot: string | null;
  scoreBasis: string[];
  gapRecommendations: string | null;
  certificateRecommendations: string | null;
  strategyActions: string[];
  conditionMatrix: string | null;
  analysisConfidence: string | null;
  applyDecision: string | null;
  model: string | null;
  promptVersion: string | null;
  status: string;
  errorMessage: string | null;
  createdAt: string;
  /** review-first evidence gate(R3). R3 이전 분석은 null. */
  gateStatus: string | null;
  needsHumanReview: boolean;
  gateReasonCount: number;
  gateMaxSeverity: string | null;
  evidenceGateVersion: string | null;
  learningTasks: AdminFitAnalysisLearningTask[];
  memos: AdminFitAnalysisMemo[];
}

export interface AdminFitAnalysisMemoRequest {
  memoType: string;
  content: string;
}
