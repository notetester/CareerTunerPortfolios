package com.careertuner.fitanalysis.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.careertuner.analysis.ai.provider.CareerAnalysisAiUsage;
import com.careertuner.fitanalysis.service.EvidenceGateDecision;
import com.careertuner.fitanalysis.service.EvidenceGateService;

/**
 * A-only baseline 실측(reports/80·gold label 확정)에서 발견된 <b>진짜 보유단정 3건</b>을
 * 실제 production 안전장치 코드로 replay 하는 회귀 테스트.
 *
 * <p>reports/80 은 "3건 모두 E1/R3 검출 유형"이라고 휴리스틱 매치 <i>분석</i>으로 서술했다 —
 * 이 테스트는 그 주장을 실행 검증으로 승격한다: 실제 4090 3B LoRA 출력 문장(원문 그대로)을
 * ① E1 hard guard({@link OssFitAnalysisAiService#groundingViolation}) ② R3 gate({@link EvidenceGateService})
 * 에 통과시켜 둘 다 잡는지 단언한다. 이 문장들이 미래에 새는 방향의 변경(휴리스틱 완화 등)을 차단한다.
 */
class EvidenceGateBaselineReplayTest {

    private final EvidenceGateService gate = new EvidenceGateService();

    private static final FitApplyDecision HOLD = new FitApplyDecision("HOLD", List.of(), List.of());

    private static FitAnalysisAiResult ai(List<String> matched, List<String> missing, String fitSummary) {
        return new FitAnalysisAiResult(50, matched, missing, List.of(), List.of(), fitSummary,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), HOLD,
                new CareerAnalysisAiUsage("careertuner-c-career-strategy-3b", 0, 0, 0, false), "SUCCESS", null, false);
    }

    /** EA-A-003 run2 (critical): 필수 Spring Boot 미보유인데 능력 보유로 승격한 실제 출력 문장. */
    @Test
    void eaA003SpringBootClaimIsCaughtByBothLayers() {
        String sentence = "Java 보유 역량으로 이미 Spring Boot를 포함해 서버 개발 업무에 참여할 수 있습니다";
        FitAnalysisAiCommand command = new FitAnalysisAiCommand("샵스트림", "서버 개발자",
                List.of("Spring Boot"), List.of("Kotlin"), "Spring Boot 커머스 서버 유지보수",
                List.of("Java"), List.of(), "백엔드 개발자");

        // E1 hard guard: 부족 역량(Spring Boot) 보유 서술 검출 → production 에선 retry→fallback.
        assertThat(OssFitAnalysisAiService.groundingViolation(
                List.of("Spring Boot", "Kotlin"), sentence, List.of())).isNotNull();
        // R3 gate: REVIEW_REQUIRED + critical(필수 요구).
        EvidenceGateDecision decision = gate.evaluate(command, ai(List.of(), List.of("Spring Boot", "Kotlin"), sentence));
        assertThat(decision.gateStatus()).isEqualTo(EvidenceGateDecision.STATUS_REVIEW_REQUIRED);
        assertThat(decision.maxSeverity()).isEqualTo(EvidenceGateDecision.SEVERITY_CRITICAL);
        assertThat(decision.reasons()).anySatisfy(r -> assertThat(r.claim()).isEqualTo("Spring Boot"));
    }

    /** EA-A-013 run1 (warning): 우대 Redis 미보유인데 "보유 역량에 포함"이라 단정한 실제 출력 문장. */
    @Test
    void eaA013RedisClaimIsCaughtByBothLayers() {
        String sentence = "Redis도 보유 역량에 포함되어 있지만, 공고에서 명시적으로 요구한 기술 중 하나가 아님을 확인해야 합니다";
        FitAnalysisAiCommand command = new FitAnalysisAiCommand("페이플로", "백엔드 개발자",
                List.of("Java", "Kafka"), List.of("Redis"), "결제 이벤트 스트림 처리",
                List.of("Java"), List.of(), "백엔드 개발자");

        assertThat(OssFitAnalysisAiService.groundingViolation(
                List.of("Kafka", "Redis"), sentence, List.of())).isNotNull();
        EvidenceGateDecision decision = gate.evaluate(command, ai(List.of("Java"), List.of("Kafka", "Redis"), sentence));
        assertThat(decision.gateStatus()).isEqualTo(EvidenceGateDecision.STATUS_REVIEW_REQUIRED);
        assertThat(decision.reasons()).anySatisfy(r -> assertThat(r.claim()).isEqualTo("Redis"));
    }

    /** EA-A-004 run2 (warning): 우대 PowerShell 미보유인데 "보유 스킬로 명시"라 단정한 실제 출력 문장. */
    @Test
    void eaA004PowerShellClaimIsCaughtByBothLayers() {
        String sentence = "PowerShell가 보유 스킬로 명시되어 있으나 데이터 엔지니어 직무에서 관련성이 낮은 것으로 파악됩니다";
        FitAnalysisAiCommand command = new FitAnalysisAiCommand("데이터너리", "DBA",
                List.of("MSSQL"), List.of("PowerShell"), "MSSQL 운영과 백업 자동화",
                List.of("SQL"), List.of("SQLD"), "데이터 엔지니어");

        assertThat(OssFitAnalysisAiService.groundingViolation(
                List.of("MSSQL", "PowerShell"), sentence, List.of())).isNotNull();
        EvidenceGateDecision decision = gate.evaluate(command, ai(List.of(), List.of("MSSQL", "PowerShell"), sentence));
        assertThat(decision.gateStatus()).isEqualTo(EvidenceGateDecision.STATUS_REVIEW_REQUIRED);
        assertThat(decision.reasons()).anySatisfy(r -> assertThat(r.claim()).isEqualTo("PowerShell"));
    }

    /** gold 확정 안전 문장(경고문)은 어느 층에도 걸리지 않아야 한다(과탐 회귀 방지). */
    @Test
    void goldSafeWarningSentencePassesBothLayers() {
        String sentence = "Go가 공고 요구 기술로 명시되어 있으나 사용자 프로필에 없어 Go 보유 여부를 확인해야 합니다";
        FitAnalysisAiCommand command = new FitAnalysisAiCommand("옵스컴퍼니", "SRE",
                List.of("Go", "Kubernetes"), List.of(), "IaC 기반 인프라 운영",
                List.of("Python"), List.of(), "클라우드 엔지니어");

        // "없어"(결핍 표현)가 같은 문장에 있어 E1/R3 모두 위반 아님.
        assertThat(OssFitAnalysisAiService.groundingViolation(
                List.of("Go", "Kubernetes"), sentence, List.of())).isNull();
        EvidenceGateDecision decision = gate.evaluate(command, ai(List.of(), List.of("Go", "Kubernetes"), sentence));
        assertThat(decision.gateStatus()).isEqualTo(EvidenceGateDecision.STATUS_PASSED);
    }
}
