package com.careertuner.companyanalysis.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.careertuner.applicationcase.service.OpenAiResponsesClient.CompanyAnalysisPayload;
import com.careertuner.companyanalysis.service.BCompanyAnalysisCanonicalizer.CanonicalCompanyAnalysis;
import com.careertuner.companyanalysis.service.BCompanyAnalysisCanonicalizer.GateAction;
import com.careertuner.companyanalysis.service.BCompanyAnalysisCanonicalizer.GateOutcome;
import com.careertuner.companyanalysis.websearch.CompanyWebEvidence;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * evidence gate 2소스 확장(235 §3 · D-2) 단위 테스트.
 * WEB 근거 SUPPORTED 경로 추가·URL sourceRef 보존·WEB sourceRef 누락 처리·
 * WEB 빈 목록 시 기존 단일소스 동작 동일(무회귀)·guardFreeText 공고 corpus 유지를 고정한다.
 */
class BCompanyAnalysisCanonicalizerWebGateTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final BCompanyAnalysisCanonicalizer canonicalizer = new BCompanyAnalysisCanonicalizer(mapper);

    private static final String POSTING = """
            가온테크 시스템엔지니어 채용
            담당업무: 서버 가상화 및 스토리지 운영 기술 지원
            자격요건: React, TypeScript 기반 프론트엔드 개발 경험 3년 이상 필수
            우대사항: 정보처리기사 자격증 보유자 우대
            """;

    private static final String JOB_POSTING_REF = "jobPosting:123#rev2";

    private static final CompanyWebEvidence WEB_NEWS = new CompanyWebEvidence(
            "https://news.example.com/gaon-cloud",
            "가온테크, 클라우드 매니지드 서비스 출시",
            "가온테크가 2025년 클라우드 매니지드 서비스 사업을 시작했다고 밝혔다.",
            Instant.parse("2026-07-03T00:00:00Z"));

    private CanonicalCompanyAnalysis canonicalize(String verifiedFactsJson, List<CompanyWebEvidence> webEvidence) {
        return canonicalize("요약입니다.", verifiedFactsJson, webEvidence);
    }

    private CanonicalCompanyAnalysis canonicalize(String companySummary,
                                                  String verifiedFactsJson,
                                                  List<CompanyWebEvidence> webEvidence) {
        CompanyAnalysisPayload payload = new CompanyAnalysisPayload(
                companySummary, "최근 이슈입니다.", "IT", "[]", "면접 포인트입니다.", "[]",
                verifiedFactsJson, "[]", "[]", null);
        return canonicalizer.canonicalizeForStorage(
                payload, 123L, 2, POSTING, "가온테크", "시스템엔지니어", webEvidence);
    }

    private JsonNode readArray(String json) {
        return mapper.readTree(json);
    }

    private List<GateOutcome> factActions(CanonicalCompanyAnalysis result) {
        return result.gateActions().stream()
                .filter(action -> "verifiedFacts".equals(action.field()))
                .map(GateAction::action)
                .toList();
    }

    // ── WEB SUPPORTED 경로 + URL sourceRef 보존 ──

    @Test
    void webOnlySupportedFactPassesAndKeepsUrlSourceRef() {
        CanonicalCompanyAnalysis result = canonicalize("""
                [{"fact":"가온테크는 클라우드 매니지드 서비스 사업을 운영한다",
                  "source":"웹검색",
                  "evidence":"가온테크가 2025년 클라우드 매니지드 서비스 사업을 시작했다"}]
                """, List.of(WEB_NEWS));

        JsonNode kept = readArray(result.payload().verifiedFacts());
        assertThat(kept).hasSize(1);
        assertThat(kept.get(0).path("sourceKind").asString("")).isEqualTo("WEB");
        assertThat(kept.get(0).path("sourceRef").asString("")).isEqualTo("https://news.example.com/gaon-cloud");
        assertThat(kept.get(0).path("sourceRef").asString("")).isNotEqualTo(JOB_POSTING_REF);
        assertThat(factActions(result)).containsExactly(GateOutcome.PASSED);
        assertThat(result.gateActions().get(0).detail()).contains("WEB 근거 매칭");
    }

    @Test
    void blankEvidenceFactGroundedOnlyInWebPassesAsWeb() {
        CanonicalCompanyAnalysis result = canonicalize("""
                [{"fact":"가온테크가 클라우드 매니지드 서비스 사업을 시작했다","source":"뉴스"}]
                """, List.of(WEB_NEWS));

        JsonNode kept = readArray(result.payload().verifiedFacts());
        assertThat(kept).hasSize(1);
        assertThat(kept.get(0).path("sourceKind").asString("")).isEqualTo("WEB");
        assertThat(kept.get(0).path("sourceRef").asString("")).isEqualTo("https://news.example.com/gaon-cloud");
        assertThat(result.gateActions().get(0).detail()).contains("WEB 근거 접지 확인");
    }

    /** 모델이 주장만 한 sourceKind=WEB — 공고로 통과하면 JOB_POSTING 으로 정규화되고 가짜 sourceRef 는 덮인다. */
    @Test
    void declaredWebClaimSupportedByPostingIsNormalizedToJobPosting() {
        CanonicalCompanyAnalysis result = canonicalize("""
                [{"fact":"React와 TypeScript 경험을 요구한다",
                  "sourceKind":"WEB",
                  "sourceRef":"https://unverified.example.com",
                  "evidence":"React, TypeScript 기반 프론트엔드 개발 경험 3년 이상 필수"}]
                """, List.of(WEB_NEWS));

        JsonNode kept = readArray(result.payload().verifiedFacts());
        assertThat(kept).hasSize(1);
        assertThat(kept.get(0).path("sourceKind").asString("")).isEqualTo("JOB_POSTING");
        assertThat(kept.get(0).path("sourceRef").asString("")).isEqualTo(JOB_POSTING_REF);
    }

    // ── 완화 금지: 근거 없는 claim 은 여전히 강등/제거 ──

    @Test
    void ungroundedClaimStillRemovedDespiteWebEvidence() {
        CanonicalCompanyAnalysis result = canonicalize("""
                [{"fact":"가온테크는 나스닥 상장사다","source":"뉴스","evidence":"나스닥 시장에 상장되어 있다"}]
                """, List.of(WEB_NEWS));

        assertThat(readArray(result.payload().verifiedFacts())).isEmpty();
        assertThat(factActions(result)).containsExactly(GateOutcome.REMOVED);
        assertThat(result.gateActions().get(0).detail())
                .contains("evidence·fact 모두 원문 미확인")
                .doesNotContain("WEB sourceRef 누락");
    }

    @Test
    void strengthDistortionOnWebPathStillDemotes() {
        CompanyWebEvidence blogEvidence = new CompanyWebEvidence(
                "https://blog.example.com/gaon",
                "가온테크 채용 후기",
                "클라우드 운영 경험 우대 조건으로 채용을 진행한다.",
                Instant.parse("2026-07-03T00:00:00Z"));

        CanonicalCompanyAnalysis result = canonicalize("""
                [{"fact":"클라우드 운영 경험이 필수다","source":"웹검색","evidence":"클라우드 운영 경험 우대 조건"}]
                """, List.of(blogEvidence));

        assertThat(readArray(result.payload().verifiedFacts())).isEmpty();
        assertThat(factActions(result)).containsExactly(GateOutcome.DEMOTED);
        assertThat(result.gateActions().get(0).detail()).contains("요건 강도 왜곡");
    }

    // ── WEB sourceRef 누락(URL 없는 근거) 처리 ──

    @Test
    void blankUrlWebMatchIsNotSupportedAndLeavesMissingRefDetail() {
        CompanyWebEvidence noUrlEvidence = new CompanyWebEvidence(
                "", "가온테크 웹문서", "가온테크의 클라우드 매니지드 서비스 사업 소개",
                Instant.parse("2026-07-03T00:00:00Z"));

        CanonicalCompanyAnalysis result = canonicalize("""
                [{"fact":"프론트엔드 개발 경험 3년 이상 필수를 요구한다",
                  "source":"웹검색",
                  "evidence":"가온테크의 클라우드 매니지드 서비스 사업 소개"}]
                """, List.of(noUrlEvidence));

        // SUPPORTED 통과 금지 + JOB_POSTING sourceRef 대체 금지 — 기존 규칙대로 강등된다.
        assertThat(readArray(result.payload().verifiedFacts())).isEmpty();
        assertThat(factActions(result)).containsExactly(GateOutcome.DEMOTED);
        assertThat(result.gateActions().get(0).detail())
                .contains("evidence 원문 매칭 실패")
                .contains("WEB sourceRef 누락");
        JsonNode inferences = readArray(result.payload().aiInferences());
        assertThat(inferences).hasSize(1);
        assertThat(inferences.get(0).path("inference").asString(""))
                .isEqualTo("프론트엔드 개발 경험 3년 이상 필수를 요구한다");
        assertThat(inferences.get(0).path("confidence").asString("")).isEqualTo("LOW");
    }

    // ── 회귀: WEB 빈 목록 = 기존 단일소스 동작과 동일 ──

    @Test
    void emptyWebEvidenceBehavesIdenticallyToLegacySingleSourcePath() {
        String facts = """
                [{"fact":"React와 TypeScript 경험을 요구한다","source":"채용공고",
                  "evidence":"React, TypeScript 기반 프론트엔드 개발 경험 3년 이상 필수"},
                 {"fact":"프론트엔드 개발 경험 3년 이상 필수를 요구한다","source":"채용공고",
                  "evidence":"존재하지 않는 근거 인용문"},
                 {"fact":"가온테크는 나스닥 상장사다","source":"뉴스","evidence":"나스닥 시장에 상장"}]
                """;
        CompanyAnalysisPayload payload = new CompanyAnalysisPayload(
                "요약입니다.", "최근 이슈입니다.", "IT", "[]", "면접 포인트입니다.", "[]",
                facts, "[]", "[]", null);

        CanonicalCompanyAnalysis legacy = canonicalizer.canonicalizeForStorage(
                payload, 123L, 2, POSTING, "가온테크", "시스템엔지니어");
        CanonicalCompanyAnalysis extended = canonicalizer.canonicalizeForStorage(
                payload, 123L, 2, POSTING, "가온테크", "시스템엔지니어", List.of());

        assertThat(extended.payload()).isEqualTo(legacy.payload());
        assertThat(extended.gateActions()).isEqualTo(legacy.gateActions());
        // 판정 자체도 기대대로: PASSED / DEMOTED / REMOVED (WEB 이 없어도 완화 없음)
        assertThat(factActions(legacy))
                .containsExactly(GateOutcome.PASSED, GateOutcome.DEMOTED, GateOutcome.REMOVED);
    }

    // ── guardFreeText 는 공고 corpus 기준 유지(WEB 미사용) ──

    @Test
    void guardFreeTextIgnoresWebEvidenceCorpus() {
        CompanyWebEvidence urlEvidence = new CompanyWebEvidence(
                "https://news.example.com/gaon-cloud",
                "가온테크 소식",
                "자세한 내용은 news.example.com/gaon-cloud 에서 확인된다.",
                Instant.parse("2026-07-03T00:00:00Z"));

        CanonicalCompanyAnalysis result = canonicalize(
                "가온테크는 시스템엔지니어를 채용한다. 자세한 내용은 news.example.com/gaon-cloud 에서 확인된다.",
                "[]", List.of(urlEvidence));

        // WEB 스니펫에 URL 이 있어도 자유서술 guard 는 공고 corpus 만 본다 → 문장 제거 유지.
        assertThat(result.payload().companySummary()).isEqualTo("가온테크는 시스템엔지니어를 채용한다.");
        assertThat(result.gateActions())
                .anyMatch(action -> "companySummary".equals(action.field())
                        && action.action() == GateOutcome.REMOVED
                        && action.detail().contains("원문에 없는 URL"));
    }
}
