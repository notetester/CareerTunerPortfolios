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
  /** gate review workflow 처리 상태(PENDING/RESOLVED/REANALYSIS_REQUESTED). gate 없으면 null. */
  gateReviewStatus: string | null;
}

/** 서버측 페이지네이션 결과. data 가 곧 PageResult (ApiResponse.data). */
export interface AdminFitAnalysisPage {
  items: AdminFitAnalysisListItem[];
  total: number;
  page: number;
  size: number;
  totalPages: number;
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
  /** 실제 gate reason 목록(축약). 상세에서 왜 검토 필요인지 판단용. */
  gateReasons?: AdminFitAnalysisGateReason[] | null;
  /** gate review workflow 처리 상태와 이력. */
  gateReviewStatus: string | null;
  gateReviewedAt: string | null;
  gateReviewerName: string | null;
  learningTasks: AdminFitAnalysisLearningTask[];
  memos: AdminFitAnalysisMemo[];
}

export interface AdminGateReviewRequest {
  reviewStatus: string;
  note?: string;
}

export interface AdminFitAnalysisGateReason {
  type: string;
  claim: string;
  reason: string;
  severity: string;
}

export interface AdminFitAnalysisMemoRequest {
  memoType: string;
  content: string;
}

/** gate 통계의 빈출 claim 항목(최대 10건). */
export interface AdminGateStatsTopClaim {
  claim: string;
  count: number;
}

/** review-first evidence gate(R3) 운영 통계 요약. */
export interface AdminGateStats {
  total: number;
  byGateStatus: Record<string, number>;
  byReviewStatus: Record<string, number>;
  byMaxSeverity: Record<string, number>;
  byReasonType: Record<string, number>;
  byReasonSeverity: Record<string, number>;
  /** 파싱 불가 reasons JSON 건수. 0보다 크면 데이터 정합성 확인 필요. */
  brokenReasonsJsonCount: number;
  topClaims: AdminGateStatsTopClaim[];
}
