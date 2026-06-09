package com.careertuner.admin.dashboard.mapper;

import org.apache.ibatis.annotations.Mapper;

/**
 * 관리자 운영 대시보드 집계 매퍼(C 담당). 모두 읽기 전용 COUNT 쿼리다.
 * 도메인 횡단 카운트는 운영 대시보드의 본래 성격이며, 다른 도메인 테이블은 읽기만 한다.
 */
@Mapper
public interface AdminDashboardMapper {

    int countUsers();

    int countActiveUsers();

    int countApplications();

    int countFitAnalyses();

    int countInterviewSessions();

    int countAiCallsThisMonth();
}
