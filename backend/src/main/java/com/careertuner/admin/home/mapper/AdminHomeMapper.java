package com.careertuner.admin.home.mapper;

import org.apache.ibatis.annotations.Mapper;

/**
 * 관리자 홈(운영 작업 큐) 집계 매퍼(C 담당). 적합도 분석 운영 대기 항목을 읽기 전용으로 센다.
 */
@Mapper
public interface AdminHomeMapper {

    /** 적합도 분석 AI 호출 실패 건수(ai_usage_log status=FAILED, feature=FIT_ANALYSIS). */
    int countFitAnalysisFailures();

    /** 아직 적합도 분석이 없는 지원 건 수(처리 대기). */
    int countUnanalyzedApplications();

    /** 최근 7일 내 새로 생성된 적합도 분석 수. */
    int countNewAnalysesLast7Days();

    /** 지원 건별 최신 적합도 분석이 비정상(FAILED/FALLBACK) 상태로 노출 중인 건수. */
    int countDegradedLatestAnalyses();

    /** 재분석 필요(REANALYSIS) 운영 메모가 달린 적합도 분석 수. */
    int countReanalysisRequests();

    /** 장기 경향/대시보드 요약 실행 이력의 비정상(FAILED/FALLBACK) 건수. */
    int countCareerRunFailures();
}
