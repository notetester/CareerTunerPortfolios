/** 백엔드 /api/admin/community/reports 응답 1:1 매핑 */

export interface AdminReportReasonCount {
  l: string;
  n: number;
}

export interface AdminAiOpinion {
  status: string;
  toxic: boolean | null;
  category: string | null;
  confidence: number | null;
  model: string | null;
  completedAt: string | null;
  errorMessage: string | null;
}

export interface AdminReportListResponse {
  id: number;
  reason: string;
  type: string;
  cnt: number;
  title: string;
  excerpt: string;
  cat: string;
  catKey: string;
  author: string;
  time: string;
  status: string;
  action: string | null;
}

export interface AdminReportDetailResponse extends AdminReportListResponse {
  reasons: AdminReportReasonCount[];
  aiOpinion: AdminAiOpinion | null;
}
