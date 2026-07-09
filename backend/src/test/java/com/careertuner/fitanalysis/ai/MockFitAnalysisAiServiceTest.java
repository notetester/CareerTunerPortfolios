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
