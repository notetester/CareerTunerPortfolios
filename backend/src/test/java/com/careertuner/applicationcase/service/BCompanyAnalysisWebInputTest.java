package com.careertuner.applicationcase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.applicationcase.service.OpenAiResponsesClient.CompanyAnalysisPayload;
import com.careertuner.applicationcase.service.OpenAiResponsesClient.Usage;
import com.careertuner.companyanalysis.ai.prompt.CompanyAnalysisPromptCatalog;
import com.careertuner.companyanalysis.websearch.CompanyWebEvidence;

import tools.jackson.databind.ObjectMapper;

/**
 * R1 웹근거 입력 배선(company local/Claude 경로 · 235 §1·§3) 단위 테스트 — 비R1·비네트워크.
 * WEB empty 프롬프트/schema 불변, WEB 있음 시 [웹 검색 근거] 블록·WEB enum, URL 없음 제외,
 * 2-param 위임 동일, hosted(OpenAI) 웹 미적용(D-4c 인계)을 mock 으로 고정한다.
 */
class BCompanyAnalysisWebInputTest {

    private static final String VALID_COMPANY_JSON = """
            {"companySummary":"가온테크 요약","recentIssues":"최근 이슈","industry":"IT",
             "competitors":[],"interviewPoints":"면접 준비 포인트",
             "sources":[{"type":"JOB_POSTING","label":"공고"}],
             "verifiedFacts":[],"aiInferences":[],"unknowns":[]}
            """;

    private final ObjectMapper mapper = new ObjectMapper();

    private BAnalysisProperties localEnabledProperties() {
        BAnalysisProperties properties = new BAnalysisProperties();
        properties.getLocalLlm().setEnabled(true);
        properties.getLocalLlm().setMaxRetries(0);
        return properties;
    }

    private static ApplicationCase applicationCase() {
        return ApplicationCase.builder().id(10L).userId(1L)
                .companyName("가온테크").jobTitle("시스템엔지니어").status("DRAFT").build();
    }

    private static CompanyWebEvidence evidence(String url, String title, String snippet) {
        return new CompanyWebEvidence(url, title, snippet, Instant.parse("2026-07-03T00:00:00Z"));
    }

    /** local R1 로 generate 를 돌리고, 캡처된 user prompt 와 schema 를 반환한다(local 성공 경로). */
    private Captured runLocal(List<CompanyWebEvidence> webEvidence, boolean useOverload) {
        return runLocal("채용공고 원문", webEvidence, useOverload);
    }

    private Captured runLocal(String postingText, List<CompanyWebEvidence> webEvidence, boolean useOverload) {
        BLocalLlmClient localLlmClient = mock(BLocalLlmClient.class);
        BAnthropicClient anthropicClient = mock(BAnthropicClient.class);
        OpenAiResponsesClient openAi = mock(OpenAiResponsesClient.class);
        when(localLlmClient.chat(anyString(), anyString(), anyMap())).thenReturn(VALID_COMPANY_JSON);

        BAnalysisGenerationService service = new BAnalysisGenerationService(
                localEnabledProperties(), localLlmClient, new BJobSentenceClassifier(), mapper,
                anthropicClient, openAi);

        if (useOverload) {
            service.generateCompanyAnalysis(applicationCase(), postingText, webEvidence);
        } else {
            service.generateCompanyAnalysis(applicationCase(), postingText);
        }

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> schemaCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(localLlmClient).chat(eq(CompanyAnalysisPromptCatalog.SYSTEM_PROMPT), promptCaptor.capture(), schemaCaptor.capture());
        return new Captured(promptCaptor.getValue(), schemaCaptor.getValue());
    }

    @SuppressWarnings("unchecked")
    private static List<String> sourceKindEnum(Map<String, Object> schema) {
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        Map<String, Object> verifiedFacts = (Map<String, Object>) props.get("verifiedFacts");
        Map<String, Object> items = (Map<String, Object>) verifiedFacts.get("items");
        Map<String, Object> itemProps = (Map<String, Object>) items.get("properties");
        Map<String, Object> sourceKind = (Map<String, Object>) itemProps.get("sourceKind");
        return (List<String>) sourceKind.get("enum");
    }

    private record Captured(String userPrompt, Map<String, Object> schema) {
    }

    // ── WEB empty: 프롬프트·schema 완전 불변 ──

    @Test
    void webEmptyLeavesPromptWithoutBlockAndSchemaWithoutWebEnum() {
        Captured captured = runLocal(List.of(), true);

        assertThat(captured.userPrompt()).doesNotContain("[웹 검색 근거]");
        assertThat(sourceKindEnum(captured.schema()))
                .containsExactly("JOB_POSTING", "UPLOADED_COMPANY_DOC", "USER_MEMO");
    }

    /** 2-param 진입점(auto pipeline 등)은 빈 목록 overload 와 프롬프트·schema 가 동일하다. */
    @Test
    void twoParamEntryDelegatesIdenticallyToEmptyOverload() {
        Captured viaTwoParam = runLocal(List.of(), false);
        Captured viaEmptyOverload = runLocal(List.of(), true);

        assertThat(viaTwoParam.userPrompt()).isEqualTo(viaEmptyOverload.userPrompt());
        assertThat(viaTwoParam.schema()).isEqualTo(viaEmptyOverload.schema());
    }

    // ── WEB 있음: 블록 + WEB enum ──

    @Test
    void webEvidenceAddsSearchBlockWithUrlAndSnippet() {
        Captured captured = runLocal(List.of(
                evidence("https://news.example.com/1", "가온테크 뉴스", "가온테크가 클라우드 서비스를 출시했다")), true);

        assertThat(captured.userPrompt())
                .contains("[웹 검색 근거]")
                .contains("https://news.example.com/1")
                .contains("가온테크가 클라우드 서비스를 출시했다");
    }

    @Test
    void webEvidenceOpensWebSourceKindEnum() {
        Captured captured = runLocal(List.of(
                evidence("https://news.example.com/1", "가온테크", "가온테크 클라우드")), true);

        assertThat(sourceKindEnum(captured.schema()))
                .containsExactly("JOB_POSTING", "UPLOADED_COMPANY_DOC", "USER_MEMO", "WEB");
    }

    // ── 예산 반영: 웹 블록 길이만큼 공고 본문 예산 차감(리뷰 반영) ──

    /**
     * 긴 공고 + 웹 6건일 때, 웹 블록을 붙이면 공고 본문이 그 길이만큼 더 잘린다 —
     * 웹 블록이 num_ctx 예산 밖에서 append 되던 문제(예산 미차감)의 회귀 방지.
     * 공고를 고유 문자('가')로 채워, 블록(ascii)에는 없는 그 문자 수로 본문 길이를 잰다.
     */
    @Test
    void webBlockLengthIsReservedFromPostingBudget() {
        String longPosting = "가".repeat(8000);
        List<CompanyWebEvidence> web = List.of(
                evidence("https://example.com/1", "t", "s".repeat(200)),
                evidence("https://example.com/2", "t", "s".repeat(200)),
                evidence("https://example.com/3", "t", "s".repeat(200)),
                evidence("https://example.com/4", "t", "s".repeat(200)),
                evidence("https://example.com/5", "t", "s".repeat(200)),
                evidence("https://example.com/6", "t", "s".repeat(200)));

        Captured noWeb = runLocal(longPosting, List.of(), true);
        Captured withWeb = runLocal(longPosting, web, true);

        long postingCharsNoWeb = noWeb.userPrompt().chars().filter(c -> c == '가').count();
        long postingCharsWithWeb = withWeb.userPrompt().chars().filter(c -> c == '가').count();

        assertThat(withWeb.userPrompt()).contains("[웹 검색 근거]");
        // 웹 블록 길이만큼 예산이 차감돼 공고 본문이 더 짧아진다.
        assertThat(postingCharsWithWeb).isLessThan(postingCharsNoWeb);
    }

    // ── URL blank/null evidence 제외 ──

    @Test
    void blankOrNullUrlEvidenceIsExcludedFromBlockAndSchema() {
        Captured captured = runLocal(List.of(
                evidence("", "URL 없는 뉴스", "가온테크 관련이지만 URL 없음"),
                evidence(null, "URL null 뉴스", "가온테크 관련이지만 URL null")), true);

        assertThat(captured.userPrompt()).doesNotContain("[웹 검색 근거]");
        assertThat(sourceKindEnum(captured.schema()))
                .doesNotContain("WEB")
                .containsExactly("JOB_POSTING", "UPLOADED_COMPANY_DOC", "USER_MEMO");
    }

    // ── schema 오버로드 직접 검증 ──

    @Test
    void noArgSchemaEqualsFalseOverloadAndWebOnlyAddsSourceKindWeb() {
        BAnalysisGenerationService service = new BAnalysisGenerationService(
                localEnabledProperties(), mock(BLocalLlmClient.class), new BJobSentenceClassifier(), mapper,
                mock(BAnthropicClient.class), mock(OpenAiResponsesClient.class));

        assertThat(service.companyAnalysisSchema()).isEqualTo(service.companyAnalysisSchema(false));
        assertThat(sourceKindEnum(service.companyAnalysisSchema(false)))
                .containsExactly("JOB_POSTING", "UPLOADED_COMPANY_DOC", "USER_MEMO");
        assertThat(sourceKindEnum(service.companyAnalysisSchema(true)))
                .containsExactly("JOB_POSTING", "UPLOADED_COMPANY_DOC", "USER_MEMO", "WEB");
    }

    // ── hosted(OpenAI) 폴백 경계: 웹 미적용(D-4c 인계) ──

    /**
     * Phase 2(D-4c 경계 개방): local 비활성 + Claude 미설정 → OpenAI hosted 경로도 웹 근거 블록을 받는다.
     * 공고문 텍스트는 그대로 2번째 인자로, {@code [웹 검색 근거]} 블록(url·snippet)은 4번째 인자로 전달된다
     * (R1/Claude 와 동일한 블록 포맷 재사용). 3번째 인자는 model override.
     */
    @Test
    void openAiHostedReceivesWebInput() {
        BAnalysisProperties properties = new BAnalysisProperties();
        properties.getLocalLlm().setEnabled(false); // local 비활성 → Claude(미설정) → OpenAI
        BLocalLlmClient localLlmClient = mock(BLocalLlmClient.class);
        BAnthropicClient anthropicClient = mock(BAnthropicClient.class);
        when(anthropicClient.configured()).thenReturn(false);
        OpenAiResponsesClient openAi = mock(OpenAiResponsesClient.class);
        when(openAi.configured()).thenReturn(true);
        when(openAi.analyzeCompany(any(ApplicationCase.class), anyString(), any(), any())).thenReturn(hostedPayload());

        BAnalysisGenerationService service = new BAnalysisGenerationService(
                properties, localLlmClient, new BJobSentenceClassifier(), mapper, anthropicClient, openAi);

        service.generateCompanyAnalysis(applicationCase(), "채용공고 원문",
                List.of(evidence("https://news.example.com/1", "가온테크", "클라우드 매니지드 서비스 출시")));

        // 공고문은 2번째 인자로 그대로, 웹 블록은 4번째 인자로 전달된다(url·snippet 포함).
        ArgumentCaptor<String> blockCaptor = ArgumentCaptor.forClass(String.class);
        verify(openAi, times(1)).analyzeCompany(
                any(ApplicationCase.class), eq("채용공고 원문"), any(), blockCaptor.capture());
        assertThat(blockCaptor.getValue())
                .contains("[웹 검색 근거]")
                .contains("https://news.example.com/1")
                .contains("클라우드 매니지드 서비스 출시");
    }

    private static CompanyAnalysisPayload hostedPayload() {
        return new CompanyAnalysisPayload(
                "요약", "이슈", "IT", "[]", "면접", "[]", "[]", "[]", "[]",
                new Usage("gpt-test", 10, 5, 15));
    }
}
