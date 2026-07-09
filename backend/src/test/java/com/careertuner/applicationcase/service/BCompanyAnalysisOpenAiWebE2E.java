package com.careertuner.applicationcase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.applicationcase.service.BAnalysisGenerationService.GeneratedCompanyAnalysis;
import com.careertuner.companyanalysis.websearch.CompanyWebEvidence;

import tools.jackson.databind.ObjectMapper;

/**
 * Phase 2 E2E(수동 실행 전용) — OpenAI 기업분석 경로가 {@code [웹 검색 근거]} 블록을 실제로 소비·인용하는지 검증.
 *
 * <p>NAVER 실검색 없이도 Phase 2 범위("OpenAI 가 웹 블록을 소비")를 증명한다: <b>공고문엔 없는</b> 웹 전용 사실 +
 * 고유 URL 을 합성 {@link CompanyWebEvidence} 로 주입하고, <b>실제 OpenAI</b>(provider=openai, local 비활성,
 * Claude 미설정)로 {@code generateCompanyAnalysis(ac, posting, webEvidence)} 를 돌린다. 생성된 verifiedFacts 가
 * 그 고유 URL 을 {@code sourceKind=WEB}/{@code sourceRef} 로 인용하면, 웹 블록이 OpenAI 프롬프트까지 배선되어
 * 모델이 이를 근거로 썼음을 뜻한다(webEvidenceBlock → analyzeCompany 4-arg → user prompt append).
 *
 * <p>{@code B_OPENAI_WEB_E2E=true} + {@code OPENAI_API_KEY} 있을 때만 실행. 모델은 {@code OPENAI_MODEL}(기본 gpt-5.4-mini).
 * 실행: {@code $env:B_OPENAI_WEB_E2E="true"; $env:OPENAI_MODEL="gpt-5.4-mini"; $env:OPENAI_API_KEY="..."; .\gradlew.bat test --tests *BCompanyAnalysisOpenAiWebE2E --no-daemon}
 * 산출물은 {@code .tmp/.../company_harness_hosted_compare/openai_web_e2e.md} 에만 쓰며 커밋하지 않는다.
 */
@EnabledIfEnvironmentVariable(named = "B_OPENAI_WEB_E2E", matches = "true")
class BCompanyAnalysisOpenAiWebE2E {

    // 공고문에 없고 일반 지식으로도 알 수 없는, 오직 웹 근거에만 있는 고유 사실 + 고유 URL.
    private static final String WEB_URL = "https://news.example.com/postype-series-c-2026";
    private static final String WEB_SNIPPET =
            "포스타입이 2026년 6월 300억 원 규모의 시리즈 C 투자를 유치했다고 발표했다.";

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void openAiCitesInjectedWebEvidence() throws Exception {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new AssertionError("OPENAI_API_KEY 미설정 — E2E 불가.");
        }
        String model = envOrDefault("OPENAI_MODEL", "gpt-5.4-mini");

        OpenAiProperties openAiProperties = new OpenAiProperties();
        openAiProperties.setApiKey(apiKey);
        openAiProperties.setModel(model);
        OpenAiResponsesClient openAi = new OpenAiResponsesClient(openAiProperties, mapper);

        BAnalysisProperties properties = new BAnalysisProperties();
        properties.getLocalLlm().setEnabled(false);
        properties.getCompany().setProvider("openai");
        properties.getCompany().setOpenAiModel(model);

        BAnthropicClient anthropic = mock(BAnthropicClient.class);
        when(anthropic.configured()).thenReturn(false);

        BAnalysisGenerationService service = new BAnalysisGenerationService(
                properties, mock(BLocalLlmClient.class), new BJobSentenceClassifier(), mapper, anthropic, openAi);

        ApplicationCase ac = ApplicationCase.builder()
                .id(1L).userId(1L).companyName("포스타입").jobTitle("프론트엔드 엔지니어").status("DRAFT").build();
        // 공고문에는 시리즈 C 정보가 전혀 없다 — 그 사실은 오직 웹 근거에만 존재한다.
        String posting = """
                포스타입 프론트엔드 엔지니어(0~5년) 채용.
                TypeScript, React, NextJS 기반 웹/앱 플랫폼을 개발합니다.
                자격요건: 신입 또는 경력 5년 이하, 컴퓨터공학 관련 전공.
                """;
        CompanyWebEvidence webEvidence = new CompanyWebEvidence(
                WEB_URL, "포스타입 시리즈 C 유치", WEB_SNIPPET, Instant.parse("2026-06-20T00:00:00Z"));

        GeneratedCompanyAnalysis result = service.generateCompanyAnalysis(ac, posting, List.of(webEvidence));

        String verifiedFacts = result.payload().verifiedFacts();
        writeReport(result, verifiedFacts);

        // Phase 2 핵심 검증: OpenAI 폴백이 아니고(=실제 OpenAI 성공), 주입한 고유 URL 을 sourceKind=WEB fact 로 인용했다.
        boolean webCited = hasWebFactCiting(verifiedFacts, WEB_URL);
        assertThat(result.fellBack())
                .as("OpenAI 성공이어야 함(fellBack=%s, reason=%s)", result.fellBack(), result.fallbackReason())
                .isFalse();
        assertThat(webCited)
                .as("verifiedFacts 에 sourceKind=WEB 이고 sourceRef=%s 인 fact 가 있어야 함:\n%s", WEB_URL, verifiedFacts)
                .isTrue();
        System.out.println("RESULT|openAiCitesInjectedWebEvidence|model=" + model
                + "|fellBack=" + result.fellBack() + "|webCited=" + webCited);
    }

    /** verifiedFacts JSON 에 {@code sourceKind="WEB"} 이고 {@code sourceRef} 가 주어진 URL 인 fact 가 있으면 true. */
    private boolean hasWebFactCiting(String verifiedFactsJson, String url) {
        try {
            var node = mapper.readTree(verifiedFactsJson);
            if (!node.isArray()) {
                return false;
            }
            for (var fact : node) {
                if ("WEB".equals(fact.path("sourceKind").asText(""))
                        && url.equals(fact.path("sourceRef").asText(""))) {
                    return true;
                }
            }
            return false;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private void writeReport(GeneratedCompanyAnalysis result, String verifiedFacts) throws Exception {
        Path root = resolveRoot();
        Path outDir = root.resolve(".tmp/job_posting_real_regression_set/company_harness_hosted_compare");
        Files.createDirectories(outDir);
        String md = """
                # Phase 2 E2E — OpenAI 웹 근거 인용 검증

                - 주입 웹 URL: %s
                - 주입 웹 snippet: %s
                - fellBack: %s
                - URL 인용됨: %s

                ## companySummary
                %s

                ## verifiedFacts
                %s

                ## aiInferences
                %s

                ## sources
                %s
                """.formatted(
                WEB_URL, WEB_SNIPPET, result.fellBack(), verifiedFacts.contains(WEB_URL),
                result.payload().companySummary(), verifiedFacts,
                result.payload().aiInferences(), result.payload().sources());
        Files.writeString(outDir.resolve("openai_web_e2e.md"), md, StandardCharsets.UTF_8);
    }

    private static Path resolveRoot() {
        Path cur = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 4 && cur != null; i++) {
            if (Files.exists(cur.resolve(".tmp/job_posting_real_regression_set"))) {
                return cur;
            }
            cur = cur.getParent();
        }
        return Paths.get("").toAbsolutePath().getParent();
    }

    private static String envOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return (value != null && !value.isBlank()) ? value.trim() : fallback;
    }
}
