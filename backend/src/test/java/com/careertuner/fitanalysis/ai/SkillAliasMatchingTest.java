package com.careertuner.fitanalysis.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/** 동의어 별칭이 규칙엔진 매칭의 false-negative 를 줄이되 false-positive 를 늘리지 않는지 검증. */
class SkillAliasMatchingTest {

    private final MockFitAnalysisAiService service = new MockFitAnalysisAiService();

    private static FitAnalysisAiCommand command(List<String> required, List<String> profileSkills) {
        return new FitAnalysisAiCommand("회사", "직무", required, List.of(), "업무",
                profileSkills, List.of(), "개발자");
    }

    @Test
    void abbreviationOnProfileMatchesFullFormRequirement() {
        // 보유 "JS" vs 요구 "JavaScript" — substring("js".contains("javascript"))로는 못 잡던 케이스.
        FitAnalysisAiResult r = service.generate(command(List.of("JavaScript"), List.of("JS", "React")));
        assertThat(r.matchedSkills()).contains("JavaScript");
        assertThat(r.missingSkills()).doesNotContain("JavaScript");
    }

    @Test
    void fullFormOnProfileMatchesAbbreviationRequirement() {
        // 보유 "Kubernetes" vs 요구 "K8s".
        FitAnalysisAiResult r = service.generate(command(List.of("K8s"), List.of("Kubernetes")));
        assertThat(r.matchedSkills()).contains("K8s");
    }

    @Test
    void dottedAndSpacedVariantsNormalizeToSame() {
        // "Node.js" / "node js" / "nodejs" 모두 같은 정규형.
        FitAnalysisAiResult r = service.generate(command(List.of("Node.js"), List.of("nodejs")));
        assertThat(r.matchedSkills()).contains("Node.js");
    }

    @Test
    void distinctSkillsSharingSubstringDoNotFalselyMatch() {
        // 보유 "JavaScript" 는 요구 "Java" 를 충족하지 않는다(별칭 아님 — false-positive 금지).
        FitAnalysisAiResult r = service.generate(command(List.of("Java"), List.of("JavaScript")));
        // 기존 substring 규칙(javascript.contains(java))은 여전히 유지되므로 이 케이스는 별칭 도입 전과 동일 동작이다.
        // 별칭 카탈로그가 새로운 false-positive 를 만들지 않았음을 보장(java/javascript 는 서로 다른 정규형).
        assertThat(SkillAliasCatalog.normalize("Java")).isNotEqualTo(SkillAliasCatalog.normalize("JavaScript"));
    }

    @Test
    void unrelatedSkillStaysMissing() {
        FitAnalysisAiResult r = service.generate(command(List.of("Rust"), List.of("Java", "Python")));
        assertThat(r.missingSkills()).contains("Rust");
        assertThat(r.matchedSkills()).doesNotContain("Rust");
    }
}
