/**
 * 관리자 홈 타입(C 담당). 백엔드 /api/admin/home/summary 응답 형태.
 * 운영자가 지금 처리할 적합도 분석 대기 큐와 바로가기를 담는다.
 */
export interface AdminHomeShortcut {
  label: string;
  path: string;
  description: string;
}

export interface AdminHomeSummary {
  fitAnalysisFailures: number;
  unanalyzedApplications: number;
  newAnalysesLast7Days: number;
  shortcuts: AdminHomeShortcut[];
}
