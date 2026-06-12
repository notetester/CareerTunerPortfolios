package com.careertuner.fitanalysis.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 재분석 히스토리 항목. 직전 분석과 비교한 점수 변화와 매칭/부족 역량 변화를 담는다.
 * 첫 분석은 previousScore/scoreDelta 가 null 이다.
 */
public record FitAnalysisHistoryEntryResponse(
        Long id,
        Integer fitScore,
        Integer previousScore,
        Integer scoreDelta,
        List<String> gainedSkills,
        List<String> resolvedGaps,
        List<String> newGaps,
        String model,
        String status,
        LocalDateTime createdAt
) {
}
