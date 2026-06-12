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
  /** 최신 적합도 분석이 비정상(FAILED/FALLBACK)으로 노출 중인 건수. */
  degradedLatestAnalyses: number;
  /** 재분석 필요(REANALYSIS) 메모가 달린 분석 수. */
  reanalysisRequests: number;
  /** 장기/대시보드 실행 이력의 비정상 건수. */
  careerRunFailures: number;
  shortcuts: AdminHomeShortcut[];
}
