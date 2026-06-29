package com.careertuner.fitanalysis.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.careertuner.analysis.ai.provider.CareerAnalysisAiUsage;
import com.careertuner.fitanalysis.ai.FitAnalysisAiCommand;
import com.careertuner.fitanalysis.ai.FitAnalysisAiResult;
import com.careertuner.fitanalysis.ai.FitApplyDecision;

/**
 * review-first evidence gate(R3) 결정론 검증.
 *
 * <p>핵심 불변식: gate 는 fitScore/applyDecision/matchedSkills/missingSkills 를 바꾸지 않고 노출·검토 상태만 정한다.
 * 휴리스틱은 기존 E1 grounding guard 와 동일 기준(보유 표현 + 결핍·부정 표현 없음)을 일반화한다.
 */
class EvidenceGateServiceTest {

    private final EvidenceGateService gate = new EvidenceGateService();

    @Test
    void supportedOwnedClaimPasses() {
        // React 는 matchedSkills(=userEvidence)에 있으므로 '보유' 서술이 정상.
        FitAnalysisAiResult ai = ai(70, List.of("React"), List.of("AWS"), "React 경험을 보유한 강점이 있습니다.");
        EvidenceGateDecision decision = gate.evaluate(command(List.of("React"), List.of("AWS"), List.of("React"), List.of()), ai);

        assertThat(decision.gateStatus()).isEqualTo(EvidenceGateDecision.STATUS_PASSED);
        assertThat(decision.needsHumanReview()).isFalse();
        assertThat(decision.reasons()).isEmpty();
    }

    @Test
    void preferredRequirementClaimedAsOwnedIsReviewWarning() {
        // AWS 는 우대 요구이며 userEvidence 에 없는데 '활용 가능'으로 보유 단정 → warning, REVIEW_REQUIRED.
        FitAnalysisAiResult ai = ai(60, List.of("React"), List.of("AWS"), "AWS 를 활용 가능한 수준입니다.");
        EvidenceGateDecision decision = gate.evaluate(command(List.of("React"), List.of("AWS"), List.of("React"), List.of()), ai);

        assertThat(decision.gateStatus()).isEqualTo(EvidenceGateDecision.STATUS_REVIEW_REQUIRED);
        assertThat(decision.needsHumanReview()).isTrue();
        assertThat(decision.maxSeverity()).isEqualTo(EvidenceGateDecision.SEVERITY_WARNING);
        assertThat(decision.reasons()).singleElement().satisfies(reason -> {
            assertThat(reason.type()).isEqualTo("requirement_as_owned");
            assertThat(reason.claim()).isEqualTo("AWS");
            assertThat(reason.severity()).isEqualTo(EvidenceGateDecision.SEVERITY_WARNING);
        });
    }

    @Test
    void requiredSkillClaimedAsOwnedIsReviewCritical() {
        // TypeScript 는 필수 요구이며 userEvidence 에 없는데 '숙련'으로 보유 단정 → critical(지원 판단 오도 위험).
        FitAnalysisAiResult ai = ai(50, List.of("React"), List.of("TypeScript"), "TypeScript 에 숙련되어 있습니다.");
        EvidenceGateDecision decision = gate.evaluate(command(List.of("React", "TypeScript"), List.of(), List.of("React"), List.of()), ai);

        assertThat(decision.gateStatus()).isEqualTo(EvidenceGateDecision.STATUS_REVIEW_REQUIRED);
        assertThat(decision.maxSeverity()).isEqualTo(EvidenceGateDecision.SEVERITY_CRITICAL);
        assertThat(decision.reasons()).singleElement().satisfies(reason ->
                assertThat(reason.severity()).isEqualTo(EvidenceGateDecision.SEVERITY_CRITICAL));
    }

    @Test
    void lackContextIsNotFlagged() {
        // 결핍·부정 표현이 같은 문장에 있으면 위반 아님(false-positive 방지) — E1 과 동일 기준.
        FitAnalysisAiResult ai = ai(50, List.of("React"), List.of("TypeScript"), "TypeScript 경험이 부족하여 보완이 필요합니다.");
        EvidenceGateDecision decision = gate.evaluate(command(List.of("React", "TypeScript"), List.of(), List.of("React"), List.of()), ai);

        assertThat(decision.gateStatus()).isEqualTo(EvidenceGateDecision.STATUS_PASSED);
        assertThat(decision.reasons()).isEmpty();
    }

    @Test
    void heldCertificateAsEvidenceIsNotFlagged() {
        // 보유 자격증은 userEvidence(자격)로 인정 → '정보처리기사 보유' 정상.
        FitAnalysisAiResult ai = ai(60, List.of("React"), List.of("정보처리기사"),
                "정보처리기사를 보유하고 있습니다.");
        EvidenceGateDecision decision = gate.evaluate(
                command(List.of("React"), List.of("정보처리기사"), List.of("React"), List.of("정보처리기사")), ai);

        assertThat(decision.gateStatus()).isEqualTo(EvidenceGateDecision.STATUS_PASSED);
    }

    @Test
    void brokenContractIsRejected() {
        // applyDecision 누락(핵심 계약 위반) → REJECTED(내용 검사 이전 단계, 자동 확정 금지).
        FitAnalysisAiResult ai = ai(60, List.of("React"), List.of("AWS"), "정상 설명", null);
        EvidenceGateDecision decision = gate.evaluate(command(List.of("React"), List.of("AWS"), List.of("React"), List.of()), ai);

        assertThat(decision.gateStatus()).isEqualTo(EvidenceGateDecision.STATUS_REJECTED);
        assertThat(decision.needsHumanReview()).isTrue();
        assertThat(decision.reasons()).singleElement().satisfies(r -> assertThat(r.type()).isEqualTo("structural"));
    }

    @Test
    void outOfRangeScoreIsRejected() {
        FitAnalysisAiResult ai = ai(140, List.of("React"), List.of("AWS"), "정상 설명");
        EvidenceGateDecision decision = gate.evaluate(command(List.of("React"), List.of("AWS"), List.of("React"), List.of()), ai);

        assertThat(decision.gateStatus()).isEqualTo(EvidenceGateDecision.STATUS_REJECTED);
    }

    @Test
    void gateNeverMutatesScoreOrDecision() {
        FitApplyDecision decision = new FitApplyDecision("HOLD", List.of("필수 미충족"), List.of("보완"));
        FitAnalysisAiResult ai = ai(64, List.of("React"), List.of("TypeScript"), "TypeScript 에 숙련되어 있습니다.", decision);

        gate.evaluate(command(List.of("React", "TypeScript"), List.of(), List.of("React"), List.of()), ai);

        // gate 는 순수 read — 점수/판단/매칭/부족이 그대로다.
        assertThat(ai.fitScore()).isEqualTo(64);
        assertThat(ai.applyDecision()).isSameAs(decision);
        assertThat(ai.matchedSkills()).containsExactly("React");
        assertThat(ai.missingSkills()).containsExactly("TypeScript");
    }

    @Test
    void evidenceSourcesAreSnapshotted() {
        FitAnalysisAiResult ai = ai(60, List.of("React"), List.of("AWS"), "정상 설명");
        EvidenceGateDecision decision = gate.evaluate(
                command(List.of("React"), List.of("AWS"), List.of("React", "Git"), List.of("정보처리기사")), ai);

        var user = decision.evidenceSources().stream().filter(s -> s.sourceType().equals("userEvidence")).findFirst().orElseThrow();
        assertThat(user.userOwned()).isTrue();
        assertThat(user.items()).contains("React", "Git", "정보처리기사");
        // RAG off 단계: 카탈로그/회사 컨텍스트는 빈 버킷.
        var catalog = decision.evidenceSources().stream().filter(s -> s.sourceType().equals("catalogFacts")).findFirst().orElseThrow();
        assertThat(catalog.items()).isEmpty();
    }

    @Test
    void holdConstantsConfirmRagAndRewriteOff() {
        assertThat(EvidenceGateDecision.RAG_RUNTIME_ENABLED).isFalse();
        assertThat(EvidenceGateDecision.REWRITE_APPLIED).isFalse();
        assertThat(EvidenceGateDecision.VERSION).isEqualTo("r3-review-first");
    }

    private static final FitApplyDecision DEFAULT_DECISION = new FitApplyDecision("HOLD", List.of(), List.of());

    private static FitAnalysisAiResult ai(int fitScore, List<String> matched, List<String> missing, String fitSummary) {
        return ai(fitScore, matched, missing, fitSummary, DEFAULT_DECISION);
    }

    private static FitAnalysisAiResult ai(int fitScore, List<String> matched, List<String> missing,
                                          String fitSummary, FitApplyDecision decision) {
        return new FitAnalysisAiResult(
                fitScore, matched, missing, List.of(), List.of(), fitSummary,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), decision,
                new CareerAnalysisAiUsage("test-model", 0, 0, 0, false), "SUCCESS", null, false);
    }

    private static FitAnalysisAiCommand command(List<String> required, List<String> preferred,
                                                List<String> profileSkills, List<String> profileCerts) {
        return new FitAnalysisAiCommand("테스트기업", "백엔드 개발자", required, preferred, "REST API 개발",
                profileSkills, profileCerts, "백엔드 개발자");
    }
}
