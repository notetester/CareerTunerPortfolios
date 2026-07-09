export interface AdminCompanyAnalysisQueryParams {
  keyword?: string | null;
  sourceType?: string | null;
  industry?: string | null;
  confirmed?: boolean | null;
  hasMemo?: boolean | null;
  checked?: boolean | null;
  refreshDue?: boolean | null;
  applicationCaseId?: number | null;
  userId?: number | null;
  createdFrom?: string | null;
  createdTo?: string | null;
  sort?: string | null;
  limit?: number | null;
  offset?: number | null;
}

export interface AdminCompanyAnalysisSummaryResponse {
  totalCount: number;
  confirmedCount: number;
  unconfirmedCount: number;
  refreshDueCount: number;
  missingSourceCount: number;
  checkedCount: number;
  memoCount: number;
}

export interface AdminCompanyAnalysisRow {
  id: number;
  applicationCaseId: number;
  jobPostingId: number | null;
  jobPostingRevision: number | null;
  latestJobPostingRevision: number | null;
  staleAgainstLatestPosting: boolean;
  userId: number;
  userEmail: string;
  companyName: string;
  jobTitle: string;
  companySummary: string | null;
  recentIssues: string | null;
  industry: string | null;
  competitors: string | null;
  interviewPoints: string | null;
  sources: string | null;
  verifiedFacts: string | null;
  aiInferences: string | null;
  /** virtual 필드 — aiInferences의 kind=UNKNOWN 마커를 백엔드가 응답 직전 펼친 확인 불가 항목. */
  unknowns: string | null;
  sourceType: string | null;
  checkedAt: string | null;
  refreshRecommendedAt: string | null;
  confirmedAt: string | null;
  adminMemo: string | null;
  createdAt: string;
}

export interface AdminCompanyAnalysisMetadataUpdateRequest {
  sourceType: string | null;
  checkedAt: string | null;
  refreshRecommendedAt: string | null;
  clearCheckedAt?: boolean;
  clearRefreshRecommendedAt?: boolean;
}
