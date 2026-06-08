package com.careertuner.fitanalysis.ai;

import java.util.List;

/**
 * 적합도 분석 AI 출력 묶음.
 *
 * <p>C 담당 AI 기능 12~15(공고-스펙 적합도, 부족 역량 추천, 학습 로드맵 추천, 자격증 추천)의 결과를
 * 하나의 fit_analysis 레코드로 모은다. 점수는 매칭/부족 근거와 함께 제공한다.
 */
public record FitAnalysisAiResult(
        int fitScore,
        List<String> matchedSkills,
        List<String> missingSkills,
        List<String> recommendedStudy,
        List<String> recommendedCertificates,
        String strategy
) {
}
