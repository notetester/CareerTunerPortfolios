package com.careertuner.fitanalysis.ai;

import java.util.List;

/**
 * 적합도 분석 AI 입력 묶음.
 *
 * <p>A의 프로필 스냅샷과 B의 공고 분석 결과를 읽기 전용 기준 데이터로 받아 적합도 분석에 사용한다.
 * 원본 데이터(프로필/공고 분석)는 각 담당 영역에서 관리하며 C는 수정하지 않는다.
 */
public record FitAnalysisAiCommand(
        String companyName,
        String jobTitle,
        List<String> requiredSkills,
        List<String> preferredSkills,
        String duties,
        List<String> profileSkills,
        List<String> profileCertificates,
        String desiredJob
) {
}
