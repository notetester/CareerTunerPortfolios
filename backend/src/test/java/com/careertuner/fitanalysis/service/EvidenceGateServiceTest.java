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

    // ── 한글 전사 별칭(FP triage, reports/84): 정당 보유자의 표기 차이 FP 해소 + confusion FN 미도입 ──

    @Test
    void koreanTransliterationProfileResolvesLatinPostingFp() {
        // 프로필 "스프링부트"(한글) vs 공고 "Spring Boot"(라틴) — 정당 보유인데 표기만 다름 → PASSED 여야 한다.
        FitAnalysisAiResult ai = ai(75, List.of("Spring Boot"), List.of(),
                "Spring Boot 역량을 보유하고 있습니다.");
        EvidenceGateDecision decision = gate.evaluate(
                command(List.of("Spring Boot"), List.of(), List.of("스프링부트"), List.of()), ai);

        assertThat(decision.gateStatus()).isEqualTo(EvidenceGateDecision.STATUS_PASSED);
    }

    @Test
    void koreanJavascriptDoesNotExemptJavaClaim() {
        // FN 가드: 프로필 "자바스크립트"가 Java 요구 보유 주장을 면제하면 안 된다(자바↔자바스크립트 confusion).
        FitAnalysisAiResult ai = ai(50, List.of(), List.of("Java"), "Java 역량을 보유하고 있습니다.");
        EvidenceGateDecision decision = gate.evaluate(
                command(List.of("Java"), List.of(), List.of("자바스크립트"), List.of()), ai);

        assertThat(decision.gateStatus()).isEqualTo(EvidenceGateDecision.STATUS_REVIEW_REQUIRED);
    }

    @Test
    void koreanReactDoesNotExemptReactNativeClaim() {
        // FN 가드: 프로필 "리액트"가 React Native 매칭/보유 주장을 면제하면 안 된다.
        FitAnalysisAiResult ai = ai(55, List.of("React Native"), List.of(), "전반적으로 적합합니다.");
        EvidenceGateDecision decision = gate.evaluate(
                command(List.of("React Native"), List.of(), List.of("리액트"), List.of()), ai);

        assertThat(decision.gateStatus()).isEqualTo(EvidenceGateDecision.STATUS_REVIEW_REQUIRED);
        assertThat(decision.maxSeverity()).isEqualTo(EvidenceGateDecision.SEVERITY_CRITICAL);
    }

    @Test
    void koreanCertAbbreviationResolvesFp() {
        // "정처기"(축약) 보유자가 "정보처리기사" 요구 공고에서 FP 로 잡히지 않아야 한다.
        FitAnalysisAiResult ai = ai(70, List.of("정보처리기사"), List.of(), "정보처리기사를 보유하고 있습니다.");
        EvidenceGateDecision decision = gate.evaluate(
                command(List.of(), List.of("정보처리기사"), List.of("Java"), List.of("정처기")), ai);

        assertThat(decision.gateStatus()).isEqualTo(EvidenceGateDecision.STATUS_PASSED);
    }

    @Test
    void holdConstantsConfirmRagAndRewriteOff() {
        assertThat(EvidenceGateDecision.RAG_RUNTIME_ENABLED).isFalse();
        assertThat(EvidenceGateDecision.REWRITE_APPLIED).isFalse();
        assertThat(EvidenceGateDecision.VERSION).isEqualTo("r3-review-first");
    }

    // ── #174 후속 hotfix(reports/62): userEvidence 에서 ai.matchedSkills 제거 회귀 ──

    @Test
    void aiMatchedSkillWithoutUserEvidenceIsReviewCritical() {
        // 순환 오류 핵심: AI 가 Spark 를 matched 로 만들었지만 사용자 원본 근거는 Java 뿐 → 검토 필요(critical).
        FitAnalysisAiResult ai = ai(60, List.of("Spark"), List.of(), "Spark 역량을 보유하고 있습니다.");
        EvidenceGateDecision decision = gate.evaluate(command(List.of("Spark"), List.of(), List.of("Java"), List.of()), ai);

        assertThat(decision.gateStatus()).isEqualTo(EvidenceGateDecision.STATUS_REVIEW_REQUIRED);
        assertThat(decision.needsHumanReview()).isTrue();
        assertThat(decision.reasons()).anySatisfy(reason -> {
            assertThat(reason.claim()).isEqualTo("Spark");
            assertThat(reason.severity()).isEqualTo(EvidenceGateDecision.SEVERITY_CRITICAL);
        });
    }

    @Test
    void aiMatchedSkillFlaggedEvenWithoutPossessionClaim() {
        // 텍스트에 보유 서술이 없어도 matched 자체가 근거 없는 단정이면 검출한다.
        FitAnalysisAiResult ai = ai(60, List.of("Spark"), List.of(), "전반적으로 적합합니다.");
        EvidenceGateDecision decision = gate.evaluate(command(List.of("Spark"), List.of(), List.of("Java"), List.of()), ai);

        assertThat(decision.gateStatus()).isEqualTo(EvidenceGateDecision.STATUS_REVIEW_REQUIRED);
        assertThat(decision.reasons()).anySatisfy(reason ->
                assertThat(reason.type()).isEqualTo("matched_skill_without_user_evidence"));
    }

    @Test
    void scoreBasisPossessionClaimIsReview() {
        FitAnalysisAiResult ai = aiFull(60, List.of(), List.of(), "전반적으로 적합합니다.",
                List.of("Spark 경험을 보유해 데이터 처리 요건에 부합합니다."), List.of(), DEFAULT_DECISION);
        EvidenceGateDecision decision = gate.evaluate(command(List.of("Spark"), List.of(), List.of("Java"), List.of()), ai);

        assertThat(decision.gateStatus()).isEqualTo(EvidenceGateDecision.STATUS_REVIEW_REQUIRED);
    }

    @Test
    void strategyActionsPossessionClaimIsReview() {
        FitAnalysisAiResult ai = aiFull(60, List.of(), List.of(), "전반적으로 적합합니다.",
                List.of(), List.of("Spark 강점을 중심으로 자기소개서를 작성하세요."), DEFAULT_DECISION);
        EvidenceGateDecision decision = gate.evaluate(command(List.of("Spark"), List.of(), List.of("Java"), List.of()), ai);

        assertThat(decision.gateStatus()).isEqualTo(EvidenceGateDecision.STATUS_REVIEW_REQUIRED);
    }

    @Test
    void applyDecisionReasonPossessionClaimIsReview() {
        FitApplyDecision decisionCard = new FitApplyDecision(
                "APPLY", List.of("Spark 역량을 보유해 지원 가치가 있습니다."), List.of());
        FitAnalysisAiResult ai = aiFull(60, List.of(), List.of(), "전반적으로 적합합니다.", List.of(), List.of(), decisionCard);
        EvidenceGateDecision decision = gate.evaluate(command(List.of("Spark"), List.of(), List.of("Java"), List.of()), ai);

        assertThat(decision.gateStatus()).isEqualTo(EvidenceGateDecision.STATUS_REVIEW_REQUIRED);
    }

    @Test
    void applyDecisionActionsPossessionClaimIsReview() {
        // applyDecision.actions() 도 사용자 노출 텍스트라 보유 단정을 검출해야 한다(reasons() 와 별개 경로).
        FitApplyDecision decisionCard = new FitApplyDecision(
                "COMPLEMENT", List.of(), List.of("Spark 강점을 먼저 준비하세요."));
        FitAnalysisAiResult ai = aiFull(60, List.of(), List.of(), "전반적으로 적합합니다.", List.of(), List.of(), decisionCard);
        EvidenceGateDecision decision = gate.evaluate(command(List.of("Spark"), List.of(), List.of("Java"), List.of()), ai);

        assertThat(decision.gateStatus()).isEqualTo(EvidenceGateDecision.STATUS_REVIEW_REQUIRED);
    }

    @Test
    void genuineUserEvidencePasses() {
        // 사용자 원본 근거에 실제로 Spark 가 있으면 matched/보유 서술 모두 정상.
        FitAnalysisAiResult ai = ai(70, List.of("Spark"), List.of(), "Spark 역량을 보유하고 있습니다.");
        EvidenceGateDecision decision = gate.evaluate(command(List.of("Spark"), List.of(), List.of("Spark"), List.of()), ai);

        assertThat(decision.gateStatus()).isEqualTo(EvidenceGateDecision.STATUS_PASSED);
    }

    @Test
    void lackContextWithoutMatchedPasses() {
        FitAnalysisAiResult ai = ai(50, List.of(), List.of("Spark"), "Spark 경험이 부족하여 보완이 필요합니다.");
        EvidenceGateDecision decision = gate.evaluate(command(List.of("Spark"), List.of(), List.of("Java"), List.of()), ai);

        assertThat(decision.gateStatus()).isEqualTo(EvidenceGateDecision.STATUS_PASSED);
    }

    // ── #175 후속(reports/63): curated alias canonical key 비교 ──

    @Test
    void apacheSparkAliasPassesMatchedAudit() {
        FitAnalysisAiResult ai = ai(70, List.of("Spark"), List.of(), "Spark 역량을 보유하고 있습니다.");
        EvidenceGateDecision decision = gate.evaluate(command(List.of("Spark"), List.of(), List.of("Apache Spark"), List.of()), ai);

        assertThat(decision.gateStatus()).isEqualTo(EvidenceGateDecision.STATUS_PASSED);
        assertThat(decision.reasons()).isEmpty();
    }

    @Test
    void apacheSparkAliasPassesScoreBasisClaimAudit() {
        FitAnalysisAiResult ai = aiFull(70, List.of(), List.of(), "전반적으로 적합합니다.",
                List.of("Spark 경험을 보유해 데이터 처리 요건에 부합합니다."), List.of(), DEFAULT_DECISION);
        EvidenceGateDecision decision = gate.evaluate(command(List.of("Spark"), List.of(), List.of("Apache Spark"), List.of()), ai);

        assertThat(decision.gateStatus()).isEqualTo(EvidenceGateDecision.STATUS_PASSED);
        assertThat(decision.reasons()).isEmpty();
    }

    @Test
    void javascriptDoesNotSatisfyJava() {
        FitAnalysisAiResult ai = ai(70, List.of("Java"), List.of(), "Java 역량을 보유하고 있습니다.");
        EvidenceGateDecision decision = gate.evaluate(command(List.of("Java"), List.of(), List.of("JavaScript"), List.of()), ai);

        assertThat(decision.gateStatus()).isEqualTo(EvidenceGateDecision.STATUS_REVIEW_REQUIRED);
        assertThat(decision.reasons()).anySatisfy(reason -> {
            assertThat(reason.claim()).isEqualTo("Java");
            assertThat(reason.severity()).isEqualTo(EvidenceGateDecision.SEVERITY_CRITICAL);
        });
    }

    @Test
    void genericSqlDoesNotPassWithMssql() {
        FitAnalysisAiResult ai = ai(70, List.of("SQL"), List.of(), "SQL 역량을 보유하고 있습니다.");
        EvidenceGateDecision decision = gate.evaluate(command(List.of("SQL"), List.of(), List.of("MSSQL"), List.of()), ai);

        assertThat(decision.gateStatus()).isEqualTo(EvidenceGateDecision.STATUS_REVIEW_REQUIRED);
        assertThat(decision.reasons()).anySatisfy(reason -> assertThat(reason.claim()).isEqualTo("SQL"));
    }

    @Test
    void postgresAliasPasses() {
        FitAnalysisAiResult ai = ai(70, List.of("Postgres"), List.of(), "Postgres 경험을 보유하고 있습니다.");
        EvidenceGateDecision decision = gate.evaluate(command(List.of("Postgres"), List.of(), List.of("PostgreSQL"), List.of()), ai);

        assertThat(decision.gateStatus()).isEqualTo(EvidenceGateDecision.STATUS_PASSED);
        assertThat(decision.reasons()).isEmpty();
    }

    @Test
    void kubernetesAliasPasses() {
        FitAnalysisAiResult ai = ai(70, List.of("K8s"), List.of(), "K8s 운영 경험을 보유하고 있습니다.");
        EvidenceGateDecision decision = gate.evaluate(command(List.of("K8s"), List.of(), List.of("Kubernetes"), List.of()), ai);

        assertThat(decision.gateStatus()).isEqualTo(EvidenceGateDecision.STATUS_PASSED);
        assertThat(decision.reasons()).isEmpty();
    }

    @Test
    void springDoesNotSatisfySpringBoot() {
        FitAnalysisAiResult ai = ai(70, List.of("Spring Boot"), List.of(), "Spring Boot 역량을 보유하고 있습니다.");
        EvidenceGateDecision decision = gate.evaluate(command(List.of("Spring Boot"), List.of(), List.of("Spring"), List.of()), ai);

        assertThat(decision.gateStatus()).isEqualTo(EvidenceGateDecision.STATUS_REVIEW_REQUIRED);
        assertThat(decision.reasons()).anySatisfy(reason -> assertThat(reason.claim()).isEqualTo("Spring Boot"));
    }

    @Test
    void reactDoesNotSatisfyReactNative() {
        FitAnalysisAiResult ai = ai(70, List.of("React Native"), List.of(), "React Native 역량을 보유하고 있습니다.");
        EvidenceGateDecision decision = gate.evaluate(command(List.of("React Native"), List.of(), List.of("React"), List.of()), ai);

        assertThat(decision.gateStatus()).isEqualTo(EvidenceGateDecision.STATUS_REVIEW_REQUIRED);
        assertThat(decision.reasons()).anySatisfy(reason -> assertThat(reason.claim()).isEqualTo("React Native"));
    }

    // ── #180 후속(reports/64): mention-boundary false-positive 보강 ──

    @Test
    void nextJsDoesNotSatisfyJavascriptMention() {
        FitAnalysisAiResult ai = ai(70, List.of(), List.of(), "Next.js 경험을 보유하고 있습니다.");
        EvidenceGateDecision decision = gate.evaluate(command(List.of("JavaScript"), List.of(), List.of("Java"), List.of()), ai);

        assertThat(decision.gateStatus()).isEqualTo(EvidenceGateDecision.STATUS_PASSED);
        assertThat(decision.reasons()).isEmpty();
    }

    @Test
    void standaloneJsSatisfiesJavascriptMention() {
        FitAnalysisAiResult ai = ai(70, List.of(), List.of(), "JS 경험을 보유하고 있습니다.");
        EvidenceGateDecision decision = gate.evaluate(command(List.of("JavaScript"), List.of(), List.of("Java"), List.of()), ai);

        assertThat(decision.gateStatus()).isEqualTo(EvidenceGateDecision.STATUS_REVIEW_REQUIRED);
        assertThat(decision.reasons()).anySatisfy(reason -> assertThat(reason.claim()).isEqualTo("JavaScript"));
    }

    @Test
    void reactNativeDoesNotSatisfyReactMention() {
        FitAnalysisAiResult ai = ai(70, List.of(), List.of(), "React Native 경험을 보유하고 있습니다.");
        EvidenceGateDecision decision = gate.evaluate(command(List.of("React"), List.of(), List.of("Vue"), List.of()), ai);

        assertThat(decision.gateStatus()).isEqualTo(EvidenceGateDecision.STATUS_PASSED);
        assertThat(decision.reasons()).isEmpty();
    }

    @Test
    void standaloneReactSatisfiesReactMention() {
        FitAnalysisAiResult ai = ai(70, List.of(), List.of(), "React 경험을 보유하고 있습니다.");
        EvidenceGateDecision decision = gate.evaluate(command(List.of("React"), List.of(), List.of("Vue"), List.of()), ai);

        assertThat(decision.gateStatus()).isEqualTo(EvidenceGateDecision.STATUS_REVIEW_REQUIRED);
        assertThat(decision.reasons()).anySatisfy(reason -> assertThat(reason.claim()).isEqualTo("React"));
    }

    @Test
    void springBootDoesNotSatisfySpringMention() {
        FitAnalysisAiResult ai = ai(70, List.of(), List.of(), "Spring Boot 경험을 보유하고 있습니다.");
        EvidenceGateDecision decision = gate.evaluate(command(List.of("Spring"), List.of(), List.of("Java"), List.of()), ai);

        assertThat(decision.gateStatus()).isEqualTo(EvidenceGateDecision.STATUS_PASSED);
        assertThat(decision.reasons()).isEmpty();
    }

    @Test
    void standaloneSpringSatisfiesSpringMention() {
        FitAnalysisAiResult ai = ai(70, List.of(), List.of(), "Spring 경험을 보유하고 있습니다.");
        EvidenceGateDecision decision = gate.evaluate(command(List.of("Spring"), List.of(), List.of("Java"), List.of()), ai);

        assertThat(decision.gateStatus()).isEqualTo(EvidenceGateDecision.STATUS_REVIEW_REQUIRED);
        assertThat(decision.reasons()).anySatisfy(reason -> assertThat(reason.claim()).isEqualTo("Spring"));
    }

    @Test
    void postgresqlDoesNotSatisfyGenericSqlMention() {
        FitAnalysisAiResult ai = ai(70, List.of(), List.of(), "PostgreSQL 경험을 보유하고 있습니다.");
        EvidenceGateDecision decision = gate.evaluate(command(List.of("SQL"), List.of(), List.of("Java"), List.of()), ai);

        assertThat(decision.gateStatus()).isEqualTo(EvidenceGateDecision.STATUS_PASSED);
        assertThat(decision.reasons()).isEmpty();
    }

    @Test
    void standaloneSqlSatisfiesGenericSqlMention() {
        FitAnalysisAiResult ai = ai(70, List.of(), List.of(), "SQL 경험을 보유하고 있습니다.");
        EvidenceGateDecision decision = gate.evaluate(command(List.of("SQL"), List.of(), List.of("Java"), List.of()), ai);

        assertThat(decision.gateStatus()).isEqualTo(EvidenceGateDecision.STATUS_REVIEW_REQUIRED);
        assertThat(decision.reasons()).anySatisfy(reason -> assertThat(reason.claim()).isEqualTo("SQL"));
    }

    private static final FitApplyDecision DEFAULT_DECISION = new FitApplyDecision("HOLD", List.of(), List.of());

    // ── 계측/특성화: 같은 문장 LACK 억제로 인한 알려진 false-negative (EA-GV2-109 실측, 2026-07-07) ──
    // "고칠 버그"를 단정하는 게 아니라 현재 heuristic 동작을 못박는 회귀 앵커다. 같은 문장에 '보유'(미보유
    // 요구 역량의 자기모순 단정)와 '없'(다른 스킬 결핍)이 섞이면, 문장 단위 'LACK 있으면 위반 아님' 규칙
    // (FP 방지 목적, POSSESSION/LACK 주석 참조)이 보유 단정을 억제해 PASS 한다. 향후 heuristic 개선 시
    // 이 두 앵커(억제 O/X)로 역효과(FP 증가·FN 잔존)를 검증한다.

    @Test
    void mixedSentenceLackSuppressesPossessionClaim_knownFalseNegative() {
        FitAnalysisAiResult ai = ai(45, List.of("Git"), List.of("NestJS"),
                "NestJS와 Git가 필수 스킬로 요구되며 프로필에서 두 가지 모두 보유하고 있어 절반을 충족하지만, 우대 스킬이 없어 경쟁력이 낮습니다.");
        EvidenceGateDecision decision = gate.evaluate(
                command(List.of("NestJS"), List.of(), List.of("Git", "Express"), List.of()), ai);

        // 현재 동작: 같은 문장 LACK("없어") 억제로 NestJS 보유 단정이 미검출 → PASSED.
        assertThat(decision.gateStatus()).isEqualTo(EvidenceGateDecision.STATUS_PASSED);
    }

    @Test
    void sameClaimWithoutMixedLackIsFlagged_isolatesMechanism() {
        // 동일한 NestJS 보유 단정이나 같은 문장에 결핍 표현이 없으면 정상 검출(REVIEW). 억제 원인이 LACK 임을 격리.
        FitAnalysisAiResult ai = ai(45, List.of("Git"), List.of("NestJS"),
                "NestJS와 Git가 필수 스킬로 요구되며 프로필에서 두 가지 모두 보유하고 있어 요건을 충족합니다.");
        EvidenceGateDecision decision = gate.evaluate(
                command(List.of("NestJS"), List.of(), List.of("Git", "Express"), List.of()), ai);

        assertThat(decision.gateStatus()).isEqualTo(EvidenceGateDecision.STATUS_REVIEW_REQUIRED);
        assertThat(decision.reasons()).anySatisfy(reason -> assertThat(reason.claim()).isEqualTo("NestJS"));
    }

    private static FitAnalysisAiResult ai(int fitScore, List<String> matched, List<String> missing, String fitSummary) {
        return ai(fitScore, matched, missing, fitSummary, DEFAULT_DECISION);
    }

    private static FitAnalysisAiResult ai(int fitScore, List<String> matched, List<String> missing,
                                          String fitSummary, FitApplyDecision decision) {
        return aiFull(fitScore, matched, missing, fitSummary, List.of(), List.of(), decision);
    }

    private static FitAnalysisAiResult aiFull(int fitScore, List<String> matched, List<String> missing,
                                              String fitSummary, List<String> scoreBasis,
                                              List<String> strategyActions, FitApplyDecision decision) {
        return new FitAnalysisAiResult(
                fitScore, matched, missing, List.of(), List.of(), fitSummary,
                scoreBasis, List.of(), List.of(), List.of(), strategyActions, List.of(), decision,
                new CareerAnalysisAiUsage("test-model", 0, 0, 0, false), "SUCCESS", null, false);
    }

    private static FitAnalysisAiCommand command(List<String> required, List<String> preferred,
                                                List<String> profileSkills, List<String> profileCerts) {
        return new FitAnalysisAiCommand("테스트기업", "백엔드 개발자", required, preferred, "REST API 개발",
                profileSkills, profileCerts, "백엔드 개발자");
    }
}
