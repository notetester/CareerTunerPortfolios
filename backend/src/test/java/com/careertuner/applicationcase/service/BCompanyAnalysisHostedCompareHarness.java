package com.careertuner.applicationcase.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.applicationcase.service.BAnalysisGenerationService.GeneratedCompanyAnalysis;
import com.careertuner.applicationcase.service.OpenAiResponsesClient.CompanyAnalysisPayload;

import tools.jackson.databind.ObjectMapper;

/**
 * 기업분석 hosted provider 비교 하네스(수동 실행 전용) — Phase 1 "OpenAI vs Claude 결과 비교".
 *
 * <p>같은 실측 공고를 <b>실제 Claude</b> 와 <b>실제 OpenAI</b> 양쪽에 각각 돌려(둘 다 grounding 완화판
 * {@link com.careertuner.companyanalysis.ai.prompt.CompanyAnalysisPromptCatalog#HOSTED_SYSTEM_PROMPT} 사용)
 * 필드별 출력을 나란히 markdown 으로 덤프한다. 어느 쪽 결과가 더 좋은지 사람이 눈으로 비교해 1순위를 정하기 위한 도구다.
 * 자동 채점은 하지 않는다(빈 판정칸만 찍는다).
 *
 * <p>각 provider 는 서로 격리한다: Claude 런은 OpenAI 클라이언트를 미설정(configured=false) mock 으로,
 * OpenAI 런은 Anthropic 클라이언트를 미설정 mock 으로 두어 해당 provider 만 호출되게 한다. 자체 R1(local)은
 * 비활성이라 호출되지 않는다. 웹 근거는 이번 비교 범위 밖(공고문만 입력) — 프로덕션 OpenAI 경로와 동일 조건.
 *
 * <p>실제 API 를 호출하므로 키가 있을 때만 의미가 있다. 키가 없는 provider 는 그 런을 SKIP 하고 리포트에 표시한다.
 * 산출물은 {@code .tmp/job_posting_real_regression_set/company_harness_hosted_compare/} 하위에만 쓰며 커밋하지 않는다.
 *
 * <p>일반 테스트/CI 에 영향이 없도록 {@code B_HOSTED_COMPARE=true} 일 때만 실행된다.
 * 실행(1건): {@code $env:B_HOSTED_COMPARE="true"; $env:B_HOSTED_COMPARE_TARGET="1"; $env:ANTHROPIC_API_KEY="..."; $env:OPENAI_API_KEY="..."; .\gradlew.bat test --tests *BCompanyAnalysisHostedCompareHarness}
 * 배치: {@code B_HOSTED_COMPARE_TARGET="1-3"} 또는 {@code "1,4,10"}. 전체 10건: {@code B_HOSTED_COMPARE_ALL=true}.
 * 모델 override: {@code ANTHROPIC_MODEL}, {@code OPENAI_MODEL}(기본 gpt-5).
 */
@EnabledIfEnvironmentVariable(named = "B_HOSTED_COMPARE", matches = "true")
class BCompanyAnalysisHostedCompareHarness {

    private static final String ENV_TARGET = "B_HOSTED_COMPARE_TARGET";
    private static final String ENV_ALL = "B_HOSTED_COMPARE_ALL";

    private record TargetCase(int index, String fileName, String companyName, String jobTitle) {
    }

    // 기업분석 실측 하네스와 동일 10건 세트(순서 유지).
    private static final List<TargetCase> TARGETS = List.of(
            new TargetCase(1, "가온테크 시스템엔지니어 - 고용24.txt", "가온테크", "시스템엔지니어"),
            new TargetCase(2, "포스타입 프론트엔드 엔지니어 공고문 - 원티드.txt", "포스타입", "프론트엔드 엔지니어"),
            new TargetCase(3, "티시스아이티 AI서비스기획 공고문 - 원티드.txt", "티시스아이티", "AI서비스기획"),
            new TargetCase(4, "모스원 마케팅기획 공고문 - 인크루트.txt", "모스원", "마케팅기획"),
            new TargetCase(5, "금융21 공고문 - 고용24.txt", "금융21", "건설수주 영업"),
            new TargetCase(6, "카카오 모빌리티 QA 엔지니어 공고문 - 자체공고.txt", "카카오모빌리티", "QA 엔지니어"),
            new TargetCase(7, "위버스컴퍼니 백엔드 공고문 - 원티드.txt", "위버스컴퍼니", "백엔드 개발자"),
            new TargetCase(8, "딥그로브 AI엔지니어 인턴 공고문 - 원티드.txt", "딥그로브", "AI엔지니어 인턴"),
            new TargetCase(9, "백패커 클라우드 엔지니어 - 원티드.txt", "백패커", "클라우드 엔지니어"),
            new TargetCase(10, "동국제약 공고문-사람인.txt", "동국제약", "신입/경력 공채(글로벌/개발/디자인/영업/마케팅/관리)"));

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void compareHostedProviders() throws Exception {
        Path root = resolveRoot();
        Path ocrDir = root.resolve(".tmp/job_posting_real_regression_set/ocr");
        if (!Files.isDirectory(ocrDir)) {
            throw new AssertionError("OCR 세트 디렉터리가 없어 중단: " + ocrDir.toAbsolutePath());
        }
        Path outDir = root.resolve(".tmp/job_posting_real_regression_set/company_harness_hosted_compare");
        Files.createDirectories(outDir);

        boolean anthropicKey = present(System.getenv("ANTHROPIC_API_KEY"));
        boolean openAiKey = present(System.getenv("OPENAI_API_KEY"));
        if (!anthropicKey && !openAiKey) {
            throw new AssertionError("ANTHROPIC_API_KEY / OPENAI_API_KEY 둘 다 없음 — 비교할 provider 가 없다.");
        }

        List<TargetCase> targets = selectedTargets();
        if (targets.isEmpty()) {
            throw new AssertionError("대상 없음. B_HOSTED_COMPARE_TARGET=1, 1-3, 1,4,10 또는 B_HOSTED_COMPARE_ALL=true.");
        }

        BAnalysisGenerationService claudeService = anthropicKey ? claudeOnlyService() : null;
        BAnalysisGenerationService openAiService = openAiKey ? openAiOnlyService() : null;

        StringBuilder report = new StringBuilder();
        report.append("# 기업분석 hosted 비교 (Claude vs OpenAI)\n\n");
        report.append("- 실행 시각: ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\n");
        report.append("- 프롬프트: HOSTED_SYSTEM_PROMPT(grounding 완화판, verifiedFacts 는 근거-only)\n");
        report.append("- Claude 모델: ").append(anthropicKey ? envOrDefault("ANTHROPIC_MODEL", "claude-haiku-4-5-20251001") : "(키 없음 — SKIP)").append("\n");
        report.append("- OpenAI 모델: ").append(openAiKey ? envOrDefault("OPENAI_MODEL", "gpt-5") : "(키 없음 — SKIP)").append("\n");
        report.append("- 입력: 공고문만(웹 근거 미포함). fellBack=true 면 provider 실패로 self-rules 폴백 → 비교 표본 아님.\n\n");

        for (TargetCase target : targets) {
            Path file = ocrDir.resolve(target.fileName());
            report.append("## %02d. %s\n\n".formatted(target.index(), target.fileName()));
            report.append("- 회사명: ").append(target.companyName()).append(" / 직무명: ").append(target.jobTitle()).append("\n\n");
            if (!Files.exists(file)) {
                report.append("SKIP (공고 파일 없음)\n\n");
                System.out.println("RESULT|" + target.fileName() + "|SKIP(missing)");
                continue;
            }
            String posting = Files.readString(file, StandardCharsets.UTF_8);
            ApplicationCase ac = ApplicationCase.builder()
                    .id((long) target.index()).userId(1L)
                    .companyName(target.companyName()).jobTitle(target.jobTitle()).status("DRAFT")
                    .build();

            RunResult claude = run(claudeService, ac, posting, "Claude");
            RunResult openAi = run(openAiService, ac, posting, "OpenAI");

            report.append("- Claude: ").append(claude.status).append(" (ms=").append(claude.ms).append(")")
                    .append(" / OpenAI: ").append(openAi.status).append(" (ms=").append(openAi.ms).append(")\n\n");
            report.append(fieldTable(claude.payload, openAi.payload));
            report.append("\n### 비교 판정 〔빈칸 = 미채점〕\n\n");
            report.append("| 항목 | 우세(Claude/OpenAI/무승부) | 사유 | reviewer |\n");
            report.append("|---|---|---|---|\n");
            report.append("| 사실 정확성(verifiedFacts) | | | |\n");
            report.append("| 정보 풍부함(summary/recentIssues) | | | |\n");
            report.append("| 면접 포인트 유용성 | | | |\n");
            report.append("| 환각/오정보 없음 | | | |\n");
            report.append("| 종합 | | | |\n\n");

            System.out.println("RESULT|" + target.fileName()
                    + "|claude=" + claude.status + "(" + claude.ms + "ms)"
                    + "|openai=" + openAi.status + "(" + openAi.ms + "ms)");
        }

        Path outFile = outDir.resolve("hosted_compare.md");
        Files.writeString(outFile, report.toString(), StandardCharsets.UTF_8);
        System.out.println("REPORT_WRITTEN|" + outFile.toAbsolutePath());
    }

    private record RunResult(String status, long ms, CompanyAnalysisPayload payload) {
    }

    /** provider 서비스로 1회 생성. service==null 이면 키 없음 SKIP. */
    private RunResult run(BAnalysisGenerationService service, ApplicationCase ac, String posting, String label) {
        if (service == null) {
            return new RunResult("SKIP(no key)", 0, null);
        }
        long t0 = System.currentTimeMillis();
        try {
            GeneratedCompanyAnalysis result = service.generateCompanyAnalysis(ac, posting);
            long ms = System.currentTimeMillis() - t0;
            String status = result.fellBack() ? "FELL_BACK(self-rules)" : "OK";
            return new RunResult(status, ms, result.payload());
        } catch (RuntimeException ex) {
            long ms = System.currentTimeMillis() - t0;
            System.out.println("ERROR|" + label + "|" + ex.getMessage());
            return new RunResult("ERROR: " + oneLine(ex.getMessage()), ms, null);
        }
    }

    /** local 비활성 + OpenAI 미설정 mock → Claude 만 호출되는 서비스. */
    private BAnalysisGenerationService claudeOnlyService() {
        BAnalysisProperties properties = hostedOnlyProperties();
        BAnthropicProperties anthropicProperties = new BAnthropicProperties();
        anthropicProperties.setApiKey(System.getenv("ANTHROPIC_API_KEY"));
        anthropicProperties.setModel(envOrDefault("ANTHROPIC_MODEL", anthropicProperties.getModel()));
        BAnthropicClient anthropicClient = new BAnthropicClient(anthropicProperties, mapper);
        OpenAiResponsesClient openAiClient = mock(OpenAiResponsesClient.class);
        when(openAiClient.configured()).thenReturn(false);
        return new BAnalysisGenerationService(
                properties, mock(BLocalLlmClient.class), new BJobSentenceClassifier(),
                mapper, anthropicClient, openAiClient);
    }

    /** local 비활성 + Anthropic 미설정 mock → OpenAI 만 호출되는 서비스. */
    private BAnalysisGenerationService openAiOnlyService() {
        BAnalysisProperties properties = hostedOnlyProperties();
        OpenAiProperties openAiProperties = new OpenAiProperties();
        openAiProperties.setApiKey(System.getenv("OPENAI_API_KEY"));
        openAiProperties.setModel(envOrDefault("OPENAI_MODEL", openAiProperties.getModel()));
        OpenAiResponsesClient openAiClient = new OpenAiResponsesClient(openAiProperties, mapper);
        BAnthropicClient anthropicClient = mock(BAnthropicClient.class);
        when(anthropicClient.configured()).thenReturn(false);
        return new BAnalysisGenerationService(
                properties, mock(BLocalLlmClient.class), new BJobSentenceClassifier(),
                mapper, anthropicClient, openAiClient);
    }

    private BAnalysisProperties hostedOnlyProperties() {
        BAnalysisProperties properties = new BAnalysisProperties();
        properties.getLocalLlm().setEnabled(false);
        return properties;
    }

    private String fieldTable(CompanyAnalysisPayload c, CompanyAnalysisPayload o) {
        StringBuilder sb = new StringBuilder();
        sb.append("| 필드 | Claude | OpenAI |\n");
        sb.append("|---|---|---|\n");
        row(sb, "companySummary", c == null ? null : c.companySummary(), o == null ? null : o.companySummary());
        row(sb, "industry", c == null ? null : c.industry(), o == null ? null : o.industry());
        row(sb, "recentIssues", c == null ? null : c.recentIssues(), o == null ? null : o.recentIssues());
        row(sb, "competitors", c == null ? null : c.competitors(), o == null ? null : o.competitors());
        row(sb, "interviewPoints", c == null ? null : c.interviewPoints(), o == null ? null : o.interviewPoints());
        row(sb, "verifiedFacts", c == null ? null : c.verifiedFacts(), o == null ? null : o.verifiedFacts());
        row(sb, "aiInferences", c == null ? null : c.aiInferences(), o == null ? null : o.aiInferences());
        row(sb, "unknowns", c == null ? null : c.unknowns(), o == null ? null : o.unknowns());
        row(sb, "sources", c == null ? null : c.sources(), o == null ? null : o.sources());
        return sb.toString();
    }

    private void row(StringBuilder sb, String field, String claude, String openAi) {
        sb.append("| ").append(field).append(" | ")
                .append(cell(claude)).append(" | ")
                .append(cell(openAi)).append(" |\n");
    }

    private String cell(String value) {
        if (value == null) {
            return "_(없음)_";
        }
        return oneLine(value).replace("|", "\\|");
    }

    private static List<TargetCase> selectedTargets() {
        if (truthy(System.getenv(ENV_ALL))) {
            return TARGETS;
        }
        String target = System.getenv(ENV_TARGET);
        if (isBlank(target)) {
            return List.of();
        }
        Set<TargetCase> selected = new LinkedHashSet<>();
        for (String rawToken : target.split(",")) {
            String token = rawToken.trim();
            if (token.isEmpty()) {
                continue;
            }
            if (token.matches("\\d+\\s*-\\s*\\d+")) {
                String[] bounds = token.split("\\s*-\\s*");
                int from = Math.min(Integer.parseInt(bounds[0]), Integer.parseInt(bounds[1]));
                int to = Math.max(Integer.parseInt(bounds[0]), Integer.parseInt(bounds[1]));
                TARGETS.stream().filter(t -> t.index() >= from && t.index() <= to).forEach(selected::add);
            } else if (token.matches("\\d+")) {
                int index = Integer.parseInt(token);
                TARGETS.stream().filter(t -> t.index() == index).forEach(selected::add);
            } else {
                String normalized = token.toLowerCase(Locale.ROOT);
                TARGETS.stream()
                        .filter(t -> t.fileName().toLowerCase(Locale.ROOT).contains(normalized)
                                || t.companyName().toLowerCase(Locale.ROOT).contains(normalized))
                        .forEach(selected::add);
            }
        }
        return new ArrayList<>(selected);
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
        return present(value) ? value.trim() : fallback;
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean truthy(String value) {
        if (isBlank(value)) {
            return false;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "1", "true", "y", "yes", "on" -> true;
            default -> false;
        };
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String oneLine(String s) {
        if (s == null) {
            return "";
        }
        String flat = s.replaceAll("\\s+", " ").trim();
        return flat.length() > 1500 ? flat.substring(0, 1500) + "…" : flat;
    }
}
