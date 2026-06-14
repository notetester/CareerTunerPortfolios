package com.careertuner.analysis.dto;

import java.util.List;

/** 현재 스펙과 지원 상태를 함께 반영한 지원 우선순위. */
public record AnalysisApplicationPriorityResponse(
        Long applicationCaseId,
        String companyName,
        String jobTitle,
        Integer fitScore,
        int priorityScore,
        String urgency,
        List<String> reasons
) {
}
