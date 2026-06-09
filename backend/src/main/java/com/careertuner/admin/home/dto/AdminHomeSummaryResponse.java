package com.careertuner.admin.home.dto;

import java.util.List;

/**
 * 관리자 홈 요약 응답(C 담당).
 *
 * <p>현황 숫자 중심의 admin/dashboard 와 달리, "운영자가 지금 처리할 것"(작업 큐)과 바로가기를 제공한다.
 * 적합도 분석 운영 관점의 대기 항목을 집계한다.
 */
public record AdminHomeSummaryResponse(
        int fitAnalysisFailures,
        int unanalyzedApplications,
        int newAnalysesLast7Days,
        List<AdminHomeShortcutResponse> shortcuts
) {
}
