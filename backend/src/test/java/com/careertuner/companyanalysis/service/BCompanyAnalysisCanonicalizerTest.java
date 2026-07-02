package com.careertuner.companyanalysis.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.careertuner.applicationcase.service.OpenAiResponsesClient.CompanyAnalysisPayload;
import com.careertuner.applicationcase.service.OpenAiResponsesClient.Usage;
import com.careertuner.companyanalysis.ai.prompt.CompanyAnalysisPromptCatalog;
import com.careertuner.companyanalysis.service.BCompanyAnalysisCanonicalizer.CanonicalCompanyAnalysis;
import com.careertuner.companyanalysis.service.BCompanyAnalysisCanonicalizer.GateAction;
import com.careertuner.companyanalysis.service.BCompanyAnalysisCanonicalizer.GateOutcome;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 기업분석 저장 전 canonicalizer — evidence gate·ID 보정·unknowns 접기/펼치기 단위 테스트(6단계 1차안). */
class BCompanyAnalysisCanonicalizerTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final BCompanyAnalysisCanonicalizer canonicalizer = new BCompanyAnalysisCanonicalizer(mapper);

    private static final String POSTING = """
            가온테크 시스템엔지니어 채용
            담당업무: 서버 가상화 및 스토리지 운영 기술 지원
            자격요건: React, TypeScript 기반 프론트엔드 개발 경험 3년 이상 필수
            우대사항: 정보처리기사 자격증 보유자 우대
            홈페이지 Wwwkaoncokr 참조
            tumblbug 서비스 운영 경험 환영
            """;

    // ── evidence gate: 정상 매칭 ──

    @Test
    void evidenceQuotedFromPostingPasses() {
        CanonicalCompanyAnalysis result = canonicalize(payload(
                facts("""
                        [{"fact":"React와 TypeScript 경험을 요구한다",
                          "source":"채용공고",
                          "evidence":"React, TypeScript 기반 프론트엔드 개발 경험 3년 이상 필수"}]
                        """),
                "[]", "[]"));

        JsonNode kept = readArray(result.payload().verifiedFacts());
        assertThat(kept).hasSize(1);
        assertThat(kept.get(0).path("factId").asText()).isEqualTo("F1");
        assertThat(kept.get(0).path("sourceKind").asText()).isEqualTo("JOB_POSTING");
        assertThat(kept.get(0).path("sourceRef").asText()).isEqualTo("jobPosting:123#rev2");
        assertThat(actions(result, "verifiedFacts[0]")).containsExactly(GateOutcome.PASSED);
    }

    @Test
    void ocrWhitespaceAndLineBreakDifferencesAreTolerated() {
        // 원문은 "React, TypeScript 기반" — evidence 는 줄바꿈/공백/문장부호가 다른 OCR 형태.
        CanonicalCompanyAnalysis result = canonicalize(payload(
                facts("""
                        [{"fact":"프론트엔드 경력 요건이 있다",
                          "source":"채용공고",
                          "evidence":"React TypeScript 기반\\n프론트엔드개발 경험 3년이상 필수"}]
                        """),
                "[]", "[]"));

        assertThat(readArray(result.payload().verifiedFacts())).hasSize(1);
        assertThat(actions(result, "verifiedFacts[0]")).containsExactly(GateOutcome.PASSED);
    }

    // ── evidence gate: 반드시 막아야 할 사례(231 문서 5-4 fixture) ──

    @Test
    void semanticExpansionVirtualizationToCryptocurrencyIsBlocked() {
        // 원문 "가상화" → 출력 "가상화폐": 접두어만 같은 의미 확장은 통과시키지 않는다.
        CanonicalCompanyAnalysis result = canonicalize(payload(
                facts("""
                        [{"fact":"가상화폐 기술 지원을 다룬다",
                          "source":"채용공고",
                          "evidence":"가상화폐 기술 지원"}]
                        """),
                "[]", "[]"));

        assertThat(readArray(result.payload().verifiedFacts())).isEmpty();
        assertThat(actions(result, "verifiedFacts[0]")).doesNotContain(GateOutcome.PASSED);
    }

    @Test
    void ocrFragmentUrlReconstructionIsBlocked() {
        // 원문 OCR 토큰 "Wwwkaoncokr" 는 재구성된 URL 의 근거가 아니다.
        CanonicalCompanyAnalysis result = canonicalize(payload(
                facts("""
                        [{"fact":"홈페이지 주소가 확인된다",
                          "source":"채용공고",
                          "evidence":"www.kaoon.com.kr"}]
                        """),
                "[]", "[]"));

        assertThat(readArray(result.payload().verifiedFacts())).isEmpty();
        assertThat(actions(result, "verifiedFacts[0]")).doesNotContain(GateOutcome.PASSED);
    }

    @Test
    void serviceNameReconstructionIsBlocked() {
        // 원문 "tumblbug" → 출력 "티드버그": 서비스명 임의 보정은 통과시키지 않는다.
        CanonicalCompanyAnalysis result = canonicalize(payload(
                facts("""
                        [{"fact":"퍼블리셔 티드버그를 운영한다",
                          "source":"채용공고",
                          "evidence":"퍼블리셔 티드버그"}]
                        """),
                "[]", "[]"));

        assertThat(readArray(result.payload().verifiedFacts())).isEmpty();
    }

    @Test
    void requiredToPreferredStrengthDistortionIsDemoted() {
        // 원문 "필수" 요건을 fact 가 "선호" 로 약화 — evidence 는 매칭돼도 강도 왜곡은 강등한다.
        CanonicalCompanyAnalysis result = canonicalize(payload(
                facts("""
                        [{"fact":"프론트엔드 개발 경험 3년 이상을 선호한다",
                          "source":"채용공고",
                          "evidence":"프론트엔드 개발 경험 3년 이상 필수"}]
                        """),
                "[]", "[]"));

        assertThat(readArray(result.payload().verifiedFacts())).isEmpty();
        assertThat(actions(result, "verifiedFacts[0]")).containsExactly(GateOutcome.DEMOTED);
        JsonNode inferences = readArray(result.payload().aiInferences());
        assertThat(inferences).hasSize(1);
        assertThat(inferences.get(0).path("confidence").asText()).isEqualTo("LOW");
        assertThat(inferences.get(0).path("inference").asText()).contains("선호한다");
    }

    @Test
    void shortSingleTokenEvidenceDoesNotPass() {
        CanonicalCompanyAnalysis result = canonicalize(payload(
                facts("""
                        [{"fact":"우대 조건이 존재한다","source":"채용공고","evidence":"우대"}]
                        """),
                "[]", "[]"));

        assertThat(actions(result, "verifiedFacts[0]")).doesNotContain(GateOutcome.PASSED);
    }

    @Test
    void supportedFactsAreNotOverRemoved() {
        // 기존 SUPPORTED claim 이 gate 강등으로 소실되지 않아야 한다(231 문서 11-3 목표).
        CanonicalCompanyAnalysis result = canonicalize(payload(
                facts("""
                        [{"fact":"서버 가상화와 스토리지 운영을 담당한다","source":"채용공고",
                          "evidence":"서버 가상화 및 스토리지 운영 기술 지원"},
                         {"fact":"정보처리기사 자격증 보유자를 우대한다","source":"채용공고",
                          "evidence":"정보처리기사 자격증 보유자 우대"},
                         {"fact":"tumblbug 서비스 운영 경험을 환영한다","source":"채용공고",
                          "evidence":"tumblbug 서비스 운영 경험 환영"}]
                        """),
                "[]", "[]"));

        assertThat(readArray(result.payload().verifiedFacts())).hasSize(3);
        assertThat(result.gateActions())
                .filteredOn(action -> action.field().equals("verifiedFacts"))
                .allMatch(action -> action.action() == GateOutcome.PASSED);
    }

    @Test
    void evidenceMissingButGroundedFactIsKeptForBackwardCompatibility() {
        // evidence 키가 없는 구 스키마/self-rules 출력 — fact 자체가 원문에 접지되면 유지한다.
        CanonicalCompanyAnalysis result = canonicalize(payload(
                facts("""
                        [{"fact":"서버 가상화 스토리지 운영 기술 지원 업무","source":"채용공고"}]
                        """),
                "[]", "[]"));

        assertThat(readArray(result.payload().verifiedFacts())).hasSize(1);
    }

    // ── ID·sourceKind·basedOn 보정 ──

    @Test
    void missingAndDuplicateIdsAreReassigned() {
        CanonicalCompanyAnalysis result = canonicalize(payload(
                facts("""
                        [{"fact":"서버 가상화 및 스토리지 운영","source":"채용공고","factId":"F7",
                          "evidence":"서버 가상화 및 스토리지 운영 기술 지원"},
                         {"fact":"정보처리기사 자격증 우대","source":"채용공고","factId":"F7",
                          "evidence":"정보처리기사 자격증 보유자 우대"},
                         {"fact":"React TypeScript 요구","source":"채용공고",
                          "evidence":"React, TypeScript 기반 프론트엔드 개발 경험"}]
                        """),
                "[]", "[]"));

        JsonNode kept = readArray(result.payload().verifiedFacts());
        List<String> ids = List.of(
                kept.get(0).path("factId").asText(),
                kept.get(1).path("factId").asText(),
                kept.get(2).path("factId").asText());
        assertThat(ids).doesNotHaveDuplicates();
        assertThat(ids.get(0)).isEqualTo("F7");
    }

    @Test
    void invalidSourceKindIsRestrictedToAllowedValues() {
        CanonicalCompanyAnalysis result = canonicalize(payload(
                facts("""
                        [{"fact":"서버 가상화 및 스토리지 운영","source":"채용공고","sourceKind":"WEB_SEARCH",
                          "evidence":"서버 가상화 및 스토리지 운영 기술 지원"}]
                        """),
                "[]", "[]"));

        assertThat(readArray(result.payload().verifiedFacts()).get(0).path("sourceKind").asText())
                .isEqualTo("JOB_POSTING");
    }

    @Test
    void basedOnReferencesToMissingFactIdsAreRemovedAndDemotedToLow() {
        CanonicalCompanyAnalysis result = canonicalize(payload(
                facts("""
                        [{"fact":"서버 가상화 및 스토리지 운영","source":"채용공고","factId":"F1",
                          "evidence":"서버 가상화 및 스토리지 운영 기술 지원"}]
                        """),
                """
                [{"inference":"인프라 중심 조직일 가능성이 높다","basis":"가상화 업무 비중",
                  "basedOn":["F1","F99"],"confidence":"HIGH"},
                 {"inference":"연봉 수준이 높을 것이다","basis":"경쟁사 대비 추정",
                  "basedOn":["F42"],"confidence":"HIGH"}]
                """,
                "[]"));

        JsonNode inferences = readArray(result.payload().aiInferences());
        assertThat(inferences).hasSize(2);
        // F99 참조만 제거되고 F1 은 유지, confidence 는 그대로.
        assertThat(inferences.get(0).path("basedOn")).hasSize(1);
        assertThat(inferences.get(0).path("basedOn").get(0).asText()).isEqualTo("F1");
        assertThat(inferences.get(0).path("confidence").asText()).isEqualTo("HIGH");
        // basedOn 이 전부 깨진 추론은 LOW 로 강등.
        assertThat(inferences.get(1).path("basedOn")).isEmpty();
        assertThat(inferences.get(1).path("confidence").asText()).isEqualTo("LOW");
        assertThat(inferences.get(1).path("inferenceId").asText()).isNotBlank();
    }

    // ── unknowns 접기/펼치기 ──

    @Test
    void unknownsAreFoldedIntoAiInferencesMarkersAndUnfoldedBack() {
        CanonicalCompanyAnalysis result = canonicalize(payload(
                "[]",
                "[{\"inference\":\"일반 추론\",\"basis\":\"서버 가상화 업무\"}]",
                """
                [{"topic":"매출 규모","reason":"공고문에 관련 정보가 없다","neededSource":"IR 자료 또는 회사 소개서"}]
                """));

        // 저장 payload 의 unknowns 는 접혀서 빈 배열이 된다(DB 무변경).
        assertThat(result.payload().unknowns()).isEqualTo("[]");
        JsonNode inferences = readArray(result.payload().aiInferences());
        assertThat(inferences).hasSize(2);
        JsonNode marker = inferences.get(1);
        assertThat(marker.path("kind").asText()).isEqualTo("UNKNOWN");
        assertThat(marker.path("topic").asText()).isEqualTo("매출 규모");
        assertThat(marker.path("inference").asText()).contains("확인되지 않습니다");
        assertThat(marker.path("basis").asText()).isEqualTo("공고문에 관련 정보가 없다");

        // 조회/응답 직전 펼치기 — 마커는 unknowns 로 분리되고 aiInferences 에서 제거된다.
        String stored = result.payload().aiInferences();
        JsonNode unknowns = readArray(canonicalizer.extractUnknowns(stored));
        assertThat(unknowns).hasSize(1);
        assertThat(unknowns.get(0).path("topic").asText()).isEqualTo("매출 규모");
        assertThat(unknowns.get(0).path("neededSource").asText()).isEqualTo("IR 자료 또는 회사 소개서");
        JsonNode visible = readArray(canonicalizer.withoutUnknownMarkers(stored));
        assertThat(visible).hasSize(1);
        assertThat(visible.get(0).path("inference").asText()).isEqualTo("일반 추론");
    }

    @Test
    void mergeUnknownMarkersReattachesExistingMarkersAfterUserEdit() {
        String previous = """
                [{"inference":"일반 추론","basis":"근거"},
                 {"inference":"매출 규모는 현재 입력 자료로 확인되지 않습니다.","basis":"공고문에 관련 정보가 없다",
                  "kind":"UNKNOWN","topic":"매출 규모"}]
                """;
        String edited = "[{\"inference\":\"사용자 수정 추론\",\"basis\":\"사용자 근거\"}]";

        JsonNode merged = readArray(canonicalizer.mergeUnknownMarkers(edited, previous));

        assertThat(merged).hasSize(2);
        assertThat(merged.get(0).path("inference").asText()).isEqualTo("사용자 수정 추론");
        assertThat(merged.get(1).path("kind").asText()).isEqualTo("UNKNOWN");
    }

    @Test
    void legacyRecordsWithoutNewKeysStillReadable() {
        // 하위 호환: 새 키가 없는 기존 레코드도 읽기 경로가 그대로 동작해야 한다.
        String legacy = "[{\"inference\":\"기존 추론\",\"basis\":\"기존 근거\"}]";
        assertThat(canonicalizer.extractUnknowns(legacy)).isEqualTo("[]");
        assertThat(readArray(canonicalizer.withoutUnknownMarkers(legacy))).hasSize(1);
        assertThat(canonicalizer.extractUnknowns(null)).isEqualTo("[]");
        assertThat(canonicalizer.withoutUnknownMarkers(null)).isNull();
    }

    // ── sources 통일 ──

    @Test
    void stringSourcesAreUnifiedToTypeLabelObjects() {
        String unified = canonicalizer.canonicalizeSources("[\"채용공고\",{\"type\":\"JOB_POSTING\",\"label\":\"공고문\",\"model\":\"self-rules-v1\"}]");
        JsonNode sources = readArray(unified);
        assertThat(sources).hasSize(2);
        assertThat(sources.get(0).path("type").asText()).isEqualTo("JOB_POSTING");
        assertThat(sources.get(0).path("label").asText()).isEqualTo("채용공고");
        assertThat(sources.get(1).path("label").asText()).isEqualTo("공고문");
        assertThat(sources.get(1).has("model")).isFalse();
    }

    @Test
    void missingInferenceConfidenceIsCanonicalized() {
        CanonicalCompanyAnalysis result = canonicalize(payload(
                facts("""
                        [{"fact":"서버 가상화 및 스토리지 운영","source":"채용공고","factId":"F1",
                          "evidence":"서버 가상화 및 스토리지 운영 기술 지원"}]
                        """),
                """
                [{"inference":"인프라 중심 조직일 가능성이 높다","basis":"가상화 업무 비중","basedOn":["F1"]},
                 {"inference":"근거 연결이 없는 낮은 신뢰 추론","basis":"공고문 톤"}]
                """,
                "[]"));

        JsonNode inferences = readArray(result.payload().aiInferences());
        assertThat(inferences.get(0).path("confidence").asText()).isEqualTo("MEDIUM");
        assertThat(inferences.get(1).path("confidence").asText()).isEqualTo("LOW");
    }

    // ── 자유서술 guard + 확인불가 고지 ──

    @Test
    void freeTextSentenceWithUnverifiableUrlIsRemoved() {
        CompanyAnalysisPayload payload = new CompanyAnalysisPayload(
                "가온테크는 시스템 엔지니어를 채용한다. 자세한 내용은 www.kaoon.com.kr에서 확인할 수 있다.",
                "확인 불가.",
                "", "[]", "면접 포인트", "[]", "[]", "[]", "[]", usage());

        CanonicalCompanyAnalysis result = canonicalize(payload);

        assertThat(result.payload().companySummary())
                .contains("시스템 엔지니어를 채용한다")
                .doesNotContain("www.kaoon.com.kr");
        assertThat(result.gateActions())
                .anyMatch(action -> action.field().equals("companySummary") && action.action() == GateOutcome.REMOVED);
    }

    @Test
    void freeTextSentenceWithUnverifiableNumberIsRemoved() {
        CompanyAnalysisPayload payload = new CompanyAnalysisPayload(
                "서버 가상화 업무를 담당한다. 매출 1,200억 규모의 기업이다.",
                "확인 불가.",
                "", "[]", "면접 포인트", "[]", "[]", "[]", "[]", usage());

        CanonicalCompanyAnalysis result = canonicalize(payload);

        assertThat(result.payload().companySummary())
                .contains("서버 가상화 업무를 담당한다")
                .doesNotContain("1,200억");
    }

    @Test
    void blankSummaryAndRecentIssuesBecomeUnavailableNotices() {
        CompanyAnalysisPayload payload = new CompanyAnalysisPayload(
                " ", "", "", "[]", "면접 포인트", "[]", "[]", "[]", "[]", usage());

        CanonicalCompanyAnalysis result = canonicalize(payload);

        assertThat(result.payload().companySummary())
                .isEqualTo(CompanyAnalysisPromptCatalog.COMPANY_SUMMARY_UNAVAILABLE_NOTICE);
        assertThat(result.payload().recentIssues())
                .isEqualTo(CompanyAnalysisPromptCatalog.RECENT_ISSUES_UNAVAILABLE_NOTICE);
    }

    // ── 헬퍼 ──

    private CanonicalCompanyAnalysis canonicalize(CompanyAnalysisPayload payload) {
        return canonicalizer.canonicalizeForStorage(payload, 123L, 2, POSTING, "가온테크", "시스템엔지니어");
    }

    private CompanyAnalysisPayload payload(String verifiedFacts, String aiInferences, String unknowns) {
        return new CompanyAnalysisPayload(
                "가온테크 기업 요약입니다.",
                "확인 불가.",
                "",
                "[]",
                "면접 포인트",
                "[]",
                verifiedFacts,
                aiInferences,
                unknowns,
                usage());
    }

    private static String facts(String json) {
        return json;
    }

    private static Usage usage() {
        return new Usage("test-model", 10, 10, 20);
    }

    private JsonNode readArray(String json) {
        return mapper.readTree(json);
    }

    private List<GateOutcome> actions(CanonicalCompanyAnalysis result, String ref) {
        return result.gateActions().stream()
                .filter(action -> ref.equals(action.ref()))
                .map(GateAction::action)
                .toList();
    }
}
