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
  createdAt: string;
  memoCount: number;
  latestMemoAt: string | null;
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
  createdAt: string;
  memos: AdminFitAnalysisMemo[];
}

export interface AdminFitAnalysisMemoRequest {
  memoType: string;
  content: string;
}
