package com.careertuner.fitanalysis.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * 분석 신뢰도. 입력 데이터(공고 분석/프로필)가 부족하면 점수와 별개로 신뢰도를 낮춰 안내한다.
 *
 * <p>AI 판단이 아니라 입력 상태 기반의 결정적 계산이므로 mock/실 AI 어느 쪽이든 동일하게 산정된다.
 * 숫자(score 0~100)를 원천으로 계산하고 레벨(HIGH/MEDIUM/LOW)은 구간으로 파생해 둘이 어긋나지 않게 한다.
 * 화면은 "신뢰도 보통 · 72점"처럼 레벨과 숫자를 함께 표기한다.
 */
public record FitAnalysisConfidence(
        String level,
        int score,
        List<String> reasons
) {

    // 점수 구간 → 레벨 (단일 원천: score 에서 파생)
    private static final int HIGH_THRESHOLD = 80;
    private static final int MEDIUM_THRESHOLD = 50;

    /**
     * 입력 상태로 신뢰도 점수를 계산한다. 100점에서 입력 부족 항목만큼 감점하고, 그 점수로 레벨을 파생한다.
     * 공고 요구 역량과 프로필 기술이 비어 있으면 큰 감점(매칭 자체가 추정), 보조 입력은 작은 감점이다.
     */
    public static FitAnalysisConfidence evaluate(FitAnalysisAiCommand command) {
        int score = 100;
        List<String> reasons = new ArrayList<>();

        if (command.requiredSkills().isEmpty() && command.preferredSkills().isEmpty()) {
            score -= 40;
            reasons.add("공고 분석 결과가 없어 요구 역량 비교가 제한적입니다. 공고문 분석을 먼저 실행해주세요.");
        }
        if (command.profileSkills().isEmpty()) {
            score -= 35;
            reasons.add("프로필에 보유 기술이 등록되지 않아 매칭 판정이 추정에 가깝습니다.");
        }
        if (command.duties() == null || command.duties().isBlank()) {
            score -= 10;
            reasons.add("공고의 담당 업무 정보가 없어 직무 연관성 평가가 단순화됐습니다.");
        }
        if (command.profileCertificates().isEmpty()) {
            score -= 8;
            reasons.add("보유 자격증 정보가 없어 자격증 추천이 일반 기준으로 제공됩니다.");
        }
        if (command.desiredJob() == null || command.desiredJob().isBlank()) {
            score -= 7;
            reasons.add("희망 직무가 비어 있어 직무 맞춤 추천 정확도가 낮을 수 있습니다.");
        }
        if (command.companyContext() == null || command.companyContext().isBlank()) {
            // 기업맥락은 판단값(점수·매칭)에 관여하지 않으므로 감점은 소폭 — 전략 조언의 기업 맞춤도만 낮아진다.
            score -= 5;
            reasons.add("기업 분석 정보가 없어 지원 전략이 공고 기반으로만 제안됩니다. 기업 분석을 실행하면 더 구체적인 전략을 받을 수 있습니다.");
        }

        score = Math.max(0, Math.min(100, score));
        String level = score >= HIGH_THRESHOLD ? "HIGH" : score >= MEDIUM_THRESHOLD ? "MEDIUM" : "LOW";
        return new FitAnalysisConfidence(level, score, List.copyOf(reasons));
    }
}
