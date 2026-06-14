package com.careertuner.fitanalysis.ai;

import java.util.List;

/**
 * 분석 신뢰도. 입력 데이터(공고 분석/프로필)가 부족하면 점수와 별개로 신뢰도를 낮춰 안내한다.
 *
 * <p>AI 판단이 아니라 입력 상태 기반의 결정적 계산이므로 mock/실 AI 어느 쪽이든 동일하게 산정된다.
 * level: HIGH/MEDIUM/LOW.
 */
public record FitAnalysisConfidence(
        String level,
        List<String> reasons
) {

    /** 입력 상태로 신뢰도를 계산한다. 공고 분석·프로필 기술이 없으면 LOW, 보조 입력만 부족하면 MEDIUM. */
    public static FitAnalysisConfidence evaluate(FitAnalysisAiCommand command) {
        List<String> majors = new java.util.ArrayList<>();
        List<String> minors = new java.util.ArrayList<>();

        if (command.requiredSkills().isEmpty() && command.preferredSkills().isEmpty()) {
            majors.add("공고 분석 결과가 없어 요구 역량 비교가 제한적입니다. 공고문 분석을 먼저 실행해주세요.");
        }
        if (command.profileSkills().isEmpty()) {
            majors.add("프로필에 보유 기술이 등록되지 않아 매칭 판정이 추정에 가깝습니다.");
        }
        if (command.duties() == null || command.duties().isBlank()) {
            minors.add("공고의 담당 업무 정보가 없어 직무 연관성 평가가 단순화됐습니다.");
        }
        if (command.profileCertificates().isEmpty()) {
            minors.add("보유 자격증 정보가 없어 자격증 추천이 일반 기준으로 제공됩니다.");
        }
        if (command.desiredJob() == null || command.desiredJob().isBlank()) {
            minors.add("희망 직무가 비어 있어 직무 맞춤 추천 정확도가 낮을 수 있습니다.");
        }

        List<String> reasons = new java.util.ArrayList<>(majors);
        reasons.addAll(minors);
        String level = !majors.isEmpty() ? "LOW" : !minors.isEmpty() ? "MEDIUM" : "HIGH";
        return new FitAnalysisConfidence(level, List.copyOf(reasons));
    }
}
