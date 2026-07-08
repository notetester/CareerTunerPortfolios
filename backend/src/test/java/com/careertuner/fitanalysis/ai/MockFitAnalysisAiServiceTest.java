package com.careertuner.fitanalysis.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class MockFitAnalysisAiServiceTest {

    private final MockFitAnalysisAiService service = new MockFitAnalysisAiService();

    @Test
    void keepsEmptyProfileScoreLowAndMarksRequiredSkillsMissing() {
        FitAnalysisAiResult result = service.generate(new FitAnalysisAiCommand(
                "테스트기업",
                "백엔드 개발자",
                List.of("Java", "Spring"),
                List.of("AWS"),
                "REST API 개발",
                List.of(),
                List.of(),
                "백엔드 개발자"));

        assertThat(result.fitScore()).isEqualTo(10);
        assertThat(result.matchedSkills()).isEmpty();
        assertThat(result.missingSkills()).contains("Java", "Spring", "AWS");
        assertThat(result.applyDecision().decision()).isEqualTo("HOLD");
        assertThat(result.conditionMatrix())
                .filteredOn(row -> "REQUIRED".equals(row.conditionType()))
                .allSatisfy(row -> assertThat(row.matchStatus()).isEqualTo("UNMET"));
    }

    @Test
    void createsThreeStageRoadmapForEachTopGap() {
        FitAnalysisAiResult result = service.generate(new FitAnalysisAiCommand(
                "테스트기업",
                "프론트엔드 개발자",
                List.of("React", "TypeScript"),
                List.of("AWS"),
                "웹 서비스 개발",
                List.of("React"),
                List.of(),
                "프론트엔드 개발자"));

        assertThat(result.learningRoadmap()).hasSize(6);
        assertThat(result.learningRoadmap())
                .extracting(FitLearningRoadmapItem::title)
                .contains(
                        "TypeScript 1단계 · 핵심 개념 정리",
                        "TypeScript 2단계 · 적용 실습",
                        "TypeScript 3단계 · 포트폴리오 근거화");
        assertThat(result.learningRoadmap())
                .extracting(FitLearningRoadmapItem::sortOrder)
                .containsExactly(1, 2, 3, 4, 5, 6);
    }

    @Test
    void genericJobWithoutCertSignalProducesNoCertificateRecommendations() {
        // cert-need-gate OFF(신호 없음) → 자격증이 무분별하게 붙지 않는다(완료 기준: 필요 신호 없으면 추천 미생성).
        FitAnalysisAiResult result = service.generate(new FitAnalysisAiCommand(
                "테스트기업", "백엔드 개발자",
                List.of("Java", "Spring"), List.of("AWS"), "REST API 개발",
                List.of("Java"), List.of(), "백엔드 개발자"));

        assertThat(result.recommendedCertificates()).isEmpty();
        assertThat(result.certificateRecommendations()).isEmpty();
    }

    @Test
    void postingNamingCertificateProducesRecommendations() {
        // cert-need-gate ON(공고에 자격증 명시) → 자격증 추천 생성.
        FitAnalysisAiResult result = service.generate(new FitAnalysisAiCommand(
                "테스트기업", "백엔드 개발자",
                List.of("Java"), List.of("정보처리기사"), "백엔드 개발",
                List.of("Java"), List.of(), "백엔드 개발자"));

        assertThat(result.recommendedCertificates()).isNotEmpty();
    }

    @Test
    void companyContextDoesNotAffectRuleEngineJudgment() {
        // 뉴로-심볼릭 불변식: 기업 맥락은 설명 생성용일 뿐, 규칙엔진 판단값(fitScore/matched/missing/applyDecision/
        // conditionMatrix)을 절대 바꾸지 않는다. 기업 맥락 유무로 판단값이 달라지면 이 테스트가 실패한다.
        FitAnalysisAiCommand withoutCtx = new FitAnalysisAiCommand(
                "테스트기업", "백엔드 개발자", List.of("Java", "Spring"), List.of("AWS"),
                "REST API 개발", List.of("Java"), List.of(), "백엔드 개발자");
        FitAnalysisAiCommand withCtx = new FitAnalysisAiCommand(
                "테스트기업", "백엔드 개발자", List.of("Java", "Spring"), List.of("AWS"),
                "REST API 개발", List.of("Java"), List.of(), "백엔드 개발자",
                "- 회사 요약: 결제 플랫폼 스타트업\n- 최근 이슈: 대규모 트래픽 대응\n- 면접 포인트: 동시성 경험");

        FitAnalysisAiResult a = service.generate(withoutCtx);
        FitAnalysisAiResult b = service.generate(withCtx);

        assertThat(b.fitScore()).isEqualTo(a.fitScore());
        assertThat(b.matchedSkills()).isEqualTo(a.matchedSkills());
        assertThat(b.missingSkills()).isEqualTo(a.missingSkills());
        assertThat(b.applyDecision().decision()).isEqualTo(a.applyDecision().decision());
        assertThat(b.conditionMatrix()).isEqualTo(a.conditionMatrix());
    }

    @Test
    void includesOwnedPreferredSkillsInMatchedSkillsWithoutInflatingRequiredBasis() {
        FitAnalysisAiResult result = service.generate(new FitAnalysisAiCommand(
                "테스트기업",
                "프론트엔드 개발자",
                List.of("React"),
                List.of("TypeScript", "typescript"),
                "웹 서비스 개발",
                List.of("React", "TypeScript"),
                List.of(),
                "프론트엔드 개발자"));

        assertThat(result.matchedSkills()).containsExactly("React", "TypeScript");
        assertThat(result.missingSkills()).doesNotContain("TypeScript");
        assertThat(result.scoreBasis().get(0)).contains("필수 역량 1개 중 1개");
        assertThat(result.conditionMatrix()).hasSize(2);
    }
}
