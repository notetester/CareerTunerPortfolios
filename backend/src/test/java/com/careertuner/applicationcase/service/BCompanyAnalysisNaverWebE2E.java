package com.careertuner.applicationcase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.applicationcase.service.BAnalysisGenerationService.GeneratedCompanyAnalysis;
import com.careertuner.companyanalysis.websearch.CompanyEvidenceCollector;
import com.careertuner.companyanalysis.websearch.CompanyIdentity;
import com.careertuner.companyanalysis.websearch.CompanySourceResolver;
import com.careertuner.companyanalysis.websearch.CompanyWebEvidence;
import com.careertuner.companyanalysis.websearch.CompanyWebSearchClient;
import com.careertuner.companyanalysis.websearch.CompanyWebSearchResult;
import com.careertuner.companyanalysis.websearch.NaverSearchCategory;
import com.careertuner.companyanalysis.websearch.NaverSearchProperties;

import tools.jackson.databind.ObjectMapper;

/**
 * Phase 2 풀 파이프라인 E2E(수동 실행 전용) — <b>실제 NAVER 검색</b> → 실제 웹 근거 → <b>실제 OpenAI</b> 인용.
 *
 * <p>{@link BCompanyAnalysisOpenAiWebE2E}(합성 근거 주입)와 달리, {@code CompanyAnalysisService.collectWebEvidence}
 * 를 DB 캐시만 제외하고 재현한다: 실제 NAVER Search API 로 회사 뉴스/웹 문서를 검색 →
 * {@link CompanySourceResolver}/{@link CompanyEvidenceCollector} 로 정제/수집 → 그 evidence 를
 * {@code generateCompanyAnalysis(ac, posting, webEvidence)}(provider=openai) 에 넘겨 실제 OpenAI 응답을 받는다.
 * 리포트에 수집 evidence 와 결과 verifiedFacts(WEB 인용 포함)를 덤프한다.
 *
 * <p>키가 있을 때만 실행: {@code B_NAVER_WEB_E2E=true} + {@code OPENAI_API_KEY} + {@code NAVER_SEARCH_CLIENT_ID}/{@code _SECRET}.
 * 모델은 {@code OPENAI_MODEL}(기본 gpt-5.4-mini), 회사는 {@code B_NAVER_WEB_E2E_COMPANY}(기본 카카오모빌리티).
 * 산출물 {@code .tmp/.../company_harness_hosted_compare/naver_web_e2e.md} — 커밋하지 않는다.
 */
@EnabledIfEnvironmentVariable(named = "B_NAVER_WEB_E2E", matches = "true")
class BCompanyAnalysisNaverWebE2E {

    private static final int MAX_SEARCH_CALLS = 8;
    private static final int MAX_RESULTS = 10;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void naverSearchFeedsOpenAiCompanyAnalysis() throws Exception {
        String openAiKey = requireEnv("OPENAI_API_KEY");
        String naverId = requireEnv("NAVER_SEARCH_CLIENT_ID");
        String naverSecret = requireEnv("NAVER_SEARCH_CLIENT_SECRET");
        String model = envOrDefault("OPENAI_MODEL", "gpt-5.4-mini");
        String company = envOrDefault("B_NAVER_WEB_E2E_COMPANY", "카카오모빌리티");

        // 1) 실제 NAVER 검색 → evidence 수집 (CompanyAnalysisService.collectWebEvidence 재현, DB 캐시 제외).
        NaverSearchProperties naverProps = new NaverSearchProperties();
        naverProps.setClientId(naverId);
        naverProps.setClientSecret(naverSecret);
        CompanyWebSearchClient searchClient = new CompanyWebSearchClient(naverProps, mapper);
        CompanySourceResolver resolver = new CompanySourceResolver();
        CompanyEvidenceCollector collector = new CompanyEvidenceCollector(resolver);

        CompanyIdentity identity = new CompanyIdentity(company, "", "");
        LinkedHashMap<String, CompanyWebSearchResult> byUrl = new LinkedHashMap<>();
        int calls = 0;
        outer:
        for (String query : resolver.buildQueries(identity)) {
            for (NaverSearchCategory category : NaverSearchCategory.values()) {
                if (calls >= MAX_SEARCH_CALLS || byUrl.size() >= MAX_RESULTS) {
                    break outer;
                }
                calls++;
                for (CompanyWebSearchResult r : searchClient.search(category, query)) {
                    if (r.link() != null && !r.link().isBlank()) {
                        byUrl.putIfAbsent(r.link(), r);
                    }
                }
            }
        }
        List<CompanyWebSearchResult> gated =
                resolver.retainIdentifiableResults(identity, List.copyOf(byUrl.values()));
        List<CompanyWebEvidence> evidence = collector.collect(identity, gated);
        System.out.println("RESULT|naver|company=" + company + "|calls=" + calls
                + "|rawResults=" + byUrl.size() + "|gated=" + gated.size() + "|evidence=" + evidence.size());

        // 2) 실제 OpenAI 기업분석 (provider=openai, local 비활성, Claude 미설정) — 위 evidence 를 넘긴다.
        OpenAiProperties openAiProps = new OpenAiProperties();
        openAiProps.setApiKey(openAiKey);
        openAiProps.setModel(model);
        OpenAiResponsesClient openAi = new OpenAiResponsesClient(openAiProps, mapper);

        BAnalysisProperties properties = new BAnalysisProperties();
        properties.getLocalLlm().setEnabled(false);
        properties.getCompany().setProvider("openai");
        properties.getCompany().setOpenAiModel(model);
        BAnthropicClient anthropic = mock(BAnthropicClient.class);
        when(anthropic.configured()).thenReturn(false);

        BAnalysisGenerationService service = new BAnalysisGenerationService(
                properties, mock(BLocalLlmClient.class), new BJobSentenceClassifier(), mapper, anthropic, openAi);

        ApplicationCase ac = ApplicationCase.builder()
                .id(1L).userId(1L).companyName(company).jobTitle("QA 엔지니어").status("DRAFT").build();
        String posting = "%s QA 엔지니어(경력 2년 이상) 채용. 서비스 품질관리와 테스트 전략 수립을 담당합니다.".formatted(company);

        GeneratedCompanyAnalysis result = service.generateCompanyAnalysis(ac, posting, evidence);

        String verifiedFacts = result.payload().verifiedFacts();
        long webFactCount = countWebFacts(verifiedFacts);
        writeReport(company, model, evidence, result, verifiedFacts, webFactCount);
        System.out.println("RESULT|openai|fellBack=" + result.fellBack() + "|webFacts=" + webFactCount);

        // 실제 검색이 근거를 냈고(파이프라인 동작) OpenAI 가 폴백 없이 성공했으며, 그 웹 근거를 실제로 인용했는지 검증.
        assertThat(result.fellBack())
                .as("OpenAI 성공이어야 함(reason=%s)", result.fallbackReason())
                .isFalse();
        assertThat(evidence)
                .as("실제 NAVER 검색이 %s 에 대한 웹 근거를 최소 1건 수집해야 함", company)
                .isNotEmpty();
        assertThat(webFactCount)
                .as("OpenAI 가 수집된 웹 근거를 sourceKind=WEB fact 로 최소 1건 인용해야 함:\n%s", verifiedFacts)
                .isGreaterThan(0);
    }

    private long countWebFacts(String verifiedFactsJson) {
        try {
            var node = mapper.readTree(verifiedFactsJson);
            if (!node.isArray()) {
                return 0;
            }
            long count = 0;
            for (var fact : node) {
                if ("WEB".equals(fact.path("sourceKind").asText(""))) {
                    count++;
                }
            }
            return count;
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    private void writeReport(String company, String model, List<CompanyWebEvidence> evidence,
                             GeneratedCompanyAnalysis result, String verifiedFacts, long webFactCount) throws Exception {
        Path outDir = resolveRoot().resolve(".tmp/job_posting_real_regression_set/company_harness_hosted_compare");
        Files.createDirectories(outDir);
        StringBuilder ev = new StringBuilder();
        for (CompanyWebEvidence e : evidence) {
            ev.append("- ").append(e.url()).append("\n  ").append(oneLine(e.title()))
                    .append(" | ").append(oneLine(e.snippet())).append("\n");
        }
        String md = """
                # Phase 2 풀 파이프라인 E2E — 실제 NAVER 검색 → OpenAI 인용

                - 회사: %s / 모델: %s
                - 수집 웹 근거: %d 건
                - fellBack: %s / WEB sourceKind fact 수: %d

                ## 수집된 웹 근거(실제 NAVER)
                %s

                ## companySummary
                %s

                ## verifiedFacts
                %s

                ## sources
                %s
                """.formatted(
                company, model, evidence.size(), result.fellBack(), webFactCount,
                ev.length() == 0 ? "(없음)" : ev.toString(),
                result.payload().companySummary(), verifiedFacts, result.payload().sources());
        Files.writeString(outDir.resolve("naver_web_e2e.md"), md, StandardCharsets.UTF_8);
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

    private static String requireEnv(String name) {
        String v = System.getenv(name);
        if (v == null || v.isBlank()) {
            throw new AssertionError(name + " 미설정 — E2E 불가.");
        }
        return v.trim();
    }

    private static String envOrDefault(String name, String fallback) {
        String v = System.getenv(name);
        return (v != null && !v.isBlank()) ? v.trim() : fallback;
    }

    private static String oneLine(String s) {
        if (s == null) {
            return "";
        }
        String flat = s.replaceAll("\\s+", " ").trim();
        return flat.length() > 300 ? flat.substring(0, 300) + "…" : flat;
    }
}
