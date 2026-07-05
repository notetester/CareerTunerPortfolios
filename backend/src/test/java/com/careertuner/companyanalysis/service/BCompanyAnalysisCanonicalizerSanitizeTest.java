package com.careertuner.companyanalysis.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.careertuner.applicationcase.service.OpenAiResponsesClient.CompanyAnalysisPayload;
import com.careertuner.applicationcase.service.OpenAiResponsesClient.Usage;
import com.careertuner.companyanalysis.service.BCompanyAnalysisCanonicalizer.CanonicalCompanyAnalysis;
import com.careertuner.companyanalysis.service.BCompanyAnalysisCanonicalizer.GateOutcome;
import com.careertuner.companyanalysis.websearch.CompanyWebEvidence;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * D-6 이슈B: 저장 자유서술·텍스트 필드에 누출된 대괄호 입력블록 라벨({@code [웹 검색 근거]})의 결정적 제거 검증.
 * 핵심 계약: 대괄호 라벨만 제거하고 정상 source 라벨 {@code "웹검색"}(대괄호 없음)·구조 필드
 * (sourceRef/sourceKind/factId/URL)·gate 판정은 불변으로 유지한다.
 */
class BCompanyAnalysisCanonicalizerSanitizeTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final BCompanyAnalysisCanonicalizer canonicalizer = new BCompanyAnalysisCanonicalizer(mapper);

    private static final String LABEL = "[웹 검색 근거]";
    private static final String JOB_POSTING_REF = "jobPosting:123#rev2";

    private static final String POSTING = """
            가온테크 시스템엔지니어 채용
            담당업무: 서버 가상화 및 스토리지 운영 기술 지원
            자격요건: React, TypeScript 기반 프론트엔드 개발 경험 3년 이상 필수
            """;

    private static final CompanyWebEvidence WEB_NEWS = new CompanyWebEvidence(
            "https://news.example.com/gaon-cloud",
            "가온테크, 클라우드 매니지드 서비스 출시",
            "가온테크가 2025년 클라우드 매니지드 서비스 사업을 시작했다고 밝혔다.",
            Instant.parse("2026-07-03T00:00:00Z"));

    // ── case09 형태: recentIssues 저장본에서 토큰 제거 ──

    @Test
    void recentIssuesLeakedInputBlockLabelIsStripped() {
        CompanyAnalysisPayload payload = payloadBuilder()
                .recentIssues("최근 " + LABEL + "의 스니펫으로 확인한 클라우드 사업 확장 이슈가 있다.")
                .build();

        CanonicalCompanyAnalysis result = canonicalize(payload);

        assertThat(result.payload().recentIssues())
                .doesNotContain(LABEL)
                .doesNotContain("웹 검색 근거")
                .contains("클라우드 사업 확장");
    }

    // ── 자유서술 전반 커버 ──

    @Test
    void companySummaryAndInterviewPointsLabelsAreStripped() {
        CompanyAnalysisPayload payload = payloadBuilder()
                .companySummary("가온테크는 " + LABEL + " 시스템 엔지니어를 채용한다.")
                .interviewPoints("면접에서는 " + LABEL + " 인프라 경험을 강조하라.")
                .build();

        CanonicalCompanyAnalysis result = canonicalize(payload);

        assertThat(result.payload().companySummary()).doesNotContain("근거]").contains("시스템 엔지니어를 채용한다");
        assertThat(result.payload().interviewPoints()).doesNotContain("근거]").contains("인프라 경험을 강조하라");
    }

    @Test
    void competitorsArrayElementLabelsAreStripped() {
        CompanyAnalysisPayload payload = payloadBuilder()
                .competitors("[\"" + LABEL + " 경쟁사 A\",\"정상 경쟁사 B\"]")
                .build();

        CanonicalCompanyAnalysis result = canonicalize(payload);

        JsonNode competitors = readArray(result.payload().competitors());
        assertThat(competitors).hasSize(2);
        assertThat(competitors.get(0).asString("")).isEqualTo("경쟁사 A");
        assertThat(competitors.get(1).asString("")).isEqualTo("정상 경쟁사 B");
        assertThat(result.payload().competitors()).doesNotContain("근거]");
    }

    @Test
    void aiInferenceTextFieldsLabelsAreStripped() {
        CompanyAnalysisPayload payload = payloadBuilder()
                .aiInferences("""
                        [{"inference":"%s 인프라 중심 조직일 가능성이 높다",
                          "basis":"%s 서버 가상화 업무 비중","confidence":"MEDIUM"}]
                        """.formatted(LABEL, LABEL))
                .build();

        CanonicalCompanyAnalysis result = canonicalize(payload);

        JsonNode inferences = readArray(result.payload().aiInferences());
        assertThat(inferences).hasSize(1);
        assertThat(inferences.get(0).path("inference").asString("")).doesNotContain("근거]").contains("인프라 중심 조직");
        assertThat(inferences.get(0).path("basis").asString("")).doesNotContain("근거]").contains("서버 가상화 업무 비중");
    }

    @Test
    void foldedUnknownMarkerTextFieldsLabelsAreStripped() {
        CompanyAnalysisPayload payload = payloadBuilder()
                .unknowns("""
                        [{"topic":"%s 매출 규모","reason":"%s 공고문에 정보가 없다","neededSource":"%s IR 자료"}]
                        """.formatted(LABEL, LABEL, LABEL))
                .build();

        CanonicalCompanyAnalysis result = canonicalize(payload);

        JsonNode inferences = readArray(result.payload().aiInferences());
        JsonNode marker = firstUnknownMarker(inferences);
        assertThat(marker).isNotNull();
        assertThat(marker.path("topic").asString("")).doesNotContain("근거]").contains("매출 규모");
        assertThat(marker.path("basis").asString("")).doesNotContain("근거]").contains("공고문에 정보가 없다");
        assertThat(marker.path("neededSource").asString("")).doesNotContain("근거]").contains("IR 자료");
        assertThat(marker.path("inference").asString("")).doesNotContain("근거]").contains("확인되지 않습니다");
    }

    // ── 공백 변형 3종도 제거 ──

    @Test
    void whitespaceVariantsOfLabelAreAllStripped() {
        CompanyAnalysisPayload payload = payloadBuilder()
                .companySummary("요약 [웹검색 근거] 중간 [웹 검색근거] 그리고 [웹검색근거] 끝.")
                .build();

        CanonicalCompanyAnalysis result = canonicalize(payload);

        assertThat(result.payload().companySummary())
                .doesNotContain("근거]")
                .contains("요약")
                .contains("중간")
                .contains("끝");
    }

    // ── ★ 네거티브: 대괄호 없는 정상 source 라벨 "웹검색" 보존(오제거 방지) ──

    @Test
    void plainWebSourceLabelWithoutBracketsIsPreserved() {
        CompanyAnalysisPayload payload = payloadBuilder()
                .verifiedFacts("""
                        [{"fact":"가온테크는 클라우드 매니지드 서비스 사업을 운영한다",
                          "source":"웹검색",
                          "evidence":"가온테크가 2025년 클라우드 매니지드 서비스 사업을 시작했다"}]
                        """)
                .build();

        CanonicalCompanyAnalysis result = canonicalize(payload, List.of(WEB_NEWS));

        JsonNode kept = readArray(result.payload().verifiedFacts());
        assertThat(kept).hasSize(1);
        // 대괄호 없는 정상 라벨은 절대 제거되지 않는다.
        assertThat(kept.get(0).path("source").asString("")).isEqualTo("웹검색");
    }

    // ── ★ 구조 필드 보존 + gate 판정 불변 ──

    @Test
    void webFactStructureFieldsSurviveWhileTextLabelIsStripped() {
        CompanyAnalysisPayload tokenPayload = payloadBuilder()
                .verifiedFacts("""
                        [{"fact":"%s 가온테크는 클라우드 매니지드 서비스 사업을 운영한다",
                          "source":"웹검색",
                          "evidence":"가온테크가 2025년 클라우드 매니지드 서비스 사업을 시작했다"}]
                        """.formatted(LABEL))
                .build();

        CanonicalCompanyAnalysis result = canonicalize(tokenPayload, List.of(WEB_NEWS));

        JsonNode kept = readArray(result.payload().verifiedFacts());
        assertThat(kept).hasSize(1);
        // 구조 필드(WEB sourceKind·URL sourceRef)는 sanitize 로 변하지 않는다.
        assertThat(kept.get(0).path("sourceKind").asString("")).isEqualTo("WEB");
        assertThat(kept.get(0).path("sourceRef").asString("")).isEqualTo("https://news.example.com/gaon-cloud");
        assertThat(kept.get(0).path("factId").asString("")).isEqualTo("F1");
        // 텍스트 필드의 라벨만 제거된다.
        assertThat(kept.get(0).path("fact").asString("")).doesNotContain("근거]").contains("클라우드 매니지드 서비스 사업을 운영");
    }

    @Test
    void jobPostingFactStructureFieldsSurviveWhileTextLabelIsStripped() {
        CompanyAnalysisPayload tokenPayload = payloadBuilder()
                .verifiedFacts("""
                        [{"fact":"%s React와 TypeScript 경험을 요구한다",
                          "source":"채용공고",
                          "evidence":"React, TypeScript 기반 프론트엔드 개발 경험 3년 이상 필수"}]
                        """.formatted(LABEL))
                .build();

        CanonicalCompanyAnalysis result = canonicalize(tokenPayload);

        JsonNode kept = readArray(result.payload().verifiedFacts());
        assertThat(kept).hasSize(1);
        assertThat(kept.get(0).path("sourceKind").asString("")).isEqualTo("JOB_POSTING");
        assertThat(kept.get(0).path("sourceRef").asString("")).isEqualTo(JOB_POSTING_REF);
        assertThat(kept.get(0).path("factId").asString("")).isEqualTo("F1");
        assertThat(kept.get(0).path("fact").asString("")).doesNotContain("근거]").contains("React와 TypeScript 경험을 요구");
    }

    /** ★ 라벨 유무만 다른 두 입력에서 gate 판정(action 목록)이 완전히 동일해야 한다 — sanitize 가 판정을 바꾸지 않는다. */
    @Test
    void gateVerdictsAreIdenticalWithAndWithoutLabel() {
        String cleanFacts = """
                [{"fact":"React와 TypeScript 경험을 요구한다","source":"채용공고",
                  "evidence":"React, TypeScript 기반 프론트엔드 개발 경험 3년 이상 필수"}]
                """;
        String tokenFacts = """
                [{"fact":"%s React와 TypeScript 경험을 요구한다","source":"채용공고 %s",
                  "evidence":"React, TypeScript 기반 프론트엔드 개발 경험 3년 이상 필수"}]
                """.formatted(LABEL, LABEL);

        CanonicalCompanyAnalysis clean = canonicalize(payloadBuilder().verifiedFacts(cleanFacts).build());
        CanonicalCompanyAnalysis token = canonicalize(payloadBuilder().verifiedFacts(tokenFacts).build());

        assertThat(token.gateActions()).isEqualTo(clean.gateActions());
    }

    // ── ★ P2: sanitize 후 중복이 다시 생기지 않는다(라벨 유무만 다른 항목은 중복 제거) ──

    @Test
    void verifiedFactsDuplicateOnlyByLabelAreDedupedNotStoredTwice() {
        CompanyAnalysisPayload payload = payloadBuilder()
                .verifiedFacts("""
                        [{"fact":"React와 TypeScript 경험을 요구한다","source":"채용공고",
                          "evidence":"React, TypeScript 기반 프론트엔드 개발 경험 3년 이상 필수"},
                         {"fact":"%s React와 TypeScript 경험을 요구한다","source":"채용공고",
                          "evidence":"%s React, TypeScript 기반 프론트엔드 개발 경험 3년 이상 필수"}]
                        """.formatted(LABEL, LABEL))
                .build();

        CanonicalCompanyAnalysis result = canonicalize(payload);

        // 라벨만 다른 두 fact 는 sanitize 후 같은 텍스트가 되므로 하나만 저장되어야 한다.
        JsonNode kept = readArray(result.payload().verifiedFacts());
        assertThat(kept).hasSize(1);
        assertThat(kept.get(0).path("fact").asString("")).doesNotContain("근거]");
        assertThat(result.gateActions())
                .anyMatch(action -> "verifiedFacts[1]".equals(action.ref())
                        && action.action() == GateOutcome.REMOVED
                        && action.detail().contains("반복 중복 제거"));
    }

    @Test
    void aiInferencesDuplicateOnlyByLabelAreDedupedNotStoredTwice() {
        CompanyAnalysisPayload payload = payloadBuilder()
                .aiInferences("""
                        [{"inference":"인프라 중심 조직일 가능성이 높다","basis":"서버 가상화 업무 비중"},
                         {"inference":"%s 인프라 중심 조직일 가능성이 높다","basis":"%s 서버 가상화 업무 비중"}]
                        """.formatted(LABEL, LABEL))
                .build();

        CanonicalCompanyAnalysis result = canonicalize(payload);

        JsonNode inferences = readArray(result.payload().aiInferences());
        assertThat(inferences).hasSize(1);
        assertThat(inferences.get(0).path("inference").asString("")).doesNotContain("근거]");
        assertThat(result.gateActions())
                .anyMatch(action -> "aiInferences[1]".equals(action.ref())
                        && action.action() == GateOutcome.REMOVED
                        && action.detail().contains("반복 중복 제거"));
    }

    // ── 헬퍼 ──

    private CanonicalCompanyAnalysis canonicalize(CompanyAnalysisPayload payload) {
        return canonicalizer.canonicalizeForStorage(payload, 123L, 2, POSTING, "가온테크", "시스템엔지니어");
    }

    private CanonicalCompanyAnalysis canonicalize(CompanyAnalysisPayload payload, List<CompanyWebEvidence> webEvidence) {
        return canonicalizer.canonicalizeForStorage(payload, 123L, 2, POSTING, "가온테크", "시스템엔지니어", webEvidence);
    }

    private JsonNode readArray(String json) {
        return mapper.readTree(json);
    }

    private static JsonNode firstUnknownMarker(JsonNode inferences) {
        for (JsonNode node : inferences) {
            if ("UNKNOWN".equals(node.path("kind").asString(""))) {
                return node;
            }
        }
        return null;
    }

    private static PayloadBuilder payloadBuilder() {
        return new PayloadBuilder();
    }

    /** 자유서술/구조 필드 기본값은 라벨 없는 정상 값으로 채우고, 테스트가 필요한 필드만 덮어쓴다. */
    private static final class PayloadBuilder {
        private String companySummary = "가온테크 기업 요약입니다.";
        private String recentIssues = "확인된 최근 이슈입니다.";
        private String industry = "IT";
        private String competitors = "[]";
        private String interviewPoints = "면접 포인트입니다.";
        private String sources = "[]";
        private String verifiedFacts = "[]";
        private String aiInferences = "[]";
        private String unknowns = "[]";

        PayloadBuilder companySummary(String value) { this.companySummary = value; return this; }
        PayloadBuilder recentIssues(String value) { this.recentIssues = value; return this; }
        PayloadBuilder competitors(String value) { this.competitors = value; return this; }
        PayloadBuilder interviewPoints(String value) { this.interviewPoints = value; return this; }
        PayloadBuilder verifiedFacts(String value) { this.verifiedFacts = value; return this; }
        PayloadBuilder aiInferences(String value) { this.aiInferences = value; return this; }
        PayloadBuilder unknowns(String value) { this.unknowns = value; return this; }

        CompanyAnalysisPayload build() {
            return new CompanyAnalysisPayload(
                    companySummary, recentIssues, industry, competitors, interviewPoints,
                    sources, verifiedFacts, aiInferences, unknowns,
                    new Usage("test-model", 10, 10, 20));
        }
    }
}
