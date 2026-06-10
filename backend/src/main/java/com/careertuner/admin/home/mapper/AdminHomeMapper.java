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
}
