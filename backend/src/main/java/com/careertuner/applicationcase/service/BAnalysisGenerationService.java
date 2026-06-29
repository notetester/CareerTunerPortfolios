package com.careertuner.applicationcase.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.applicationcase.service.BJobSentenceClassifier.Classification;
import com.careertuner.applicationcase.service.OpenAiResponsesClient.CompanyAnalysisPayload;
import com.careertuner.applicationcase.service.OpenAiResponsesClient.JobAnalysisPayload;
import com.careertuner.applicationcase.service.OpenAiResponsesClient.Usage;
import com.careertuner.companyanalysis.ai.prompt.CompanyAnalysisPromptCatalog;
import com.careertuner.jobanalysis.ai.prompt.JobAnalysisPromptCatalog;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class BAnalysisGenerationService {

    public static final String SELF_RULES_MODEL = "self-rules-v1";

    // 경력 연차는 연차 숫자가 경력 키워드와 "결합"된 경우만 인정한다(단순 "N년" 근접 매칭은 오탐이 많음).
    // "설립 10년차"·"서비스 운영 5년"·"5년 연속 성장"(연혁/기간), "operating for 5 yrs"(단위만 있는 영어 기간)는
    // 구조적으로 배제하고, "5+ yrs exp" 같은 영어 결합은 인정한다.
    // - NOT_IRRELEVANT: 경력 키워드 뒤 부정어를 경력 연차로 오인("10년차 경력 무관")하지 않도록 배제.
    //   조사(은/는/이/가/도/에/과/와)·라벨 구분자([:：-])·띄어쓰기 변형과
    //   "무관/관계 없/상관 없/제한 없/불문" 어휘까지 포괄("경력: 무관"·"경력에 상관없이"·"경력과 무관하게" 등).
    // - \b...\b: "exp"가 "exported"·"expert" 같은 다른 단어에 걸리지 않도록 단어 경계 강제.
    // - [^\d\n]{0,N}: 다른 숫자나 줄바꿈을 넘지 않는 좁은 간격만 허용.
    private static final String NOT_IRRELEVANT =
            "(?!(?:은|는|이|가|도|에|과|와)?\\s*[:：\\-]?\\s*(?:무관|관계\\s*없|상관\\s*없|제한\\s*없|불문))";
    private static final Pattern EXPERIENCE_YEARS_PATTERN = Pattern.compile(
            "(?i)"
            + "(?:경력|경험|실무)" + NOT_IRRELEVANT + "[^\\d\\n]{0,6}(\\d+)\\s*년"            // 1: 경력 … N년
            + "|(\\d+)\\s*년[^\\d\\n]{0,8}(?:경력|경험|실무)" + NOT_IRRELEVANT                 // 2: N년 … 경력
            + "|\\b(?:experience|exp)\\b[^\\d\\n]{0,12}(\\d+)\\s*\\+?\\s*(?:years?|yrs?)"      // 3: experience … N years
            + "|(\\d+)\\s*\\+?\\s*(?:years?|yrs?)[^\\d\\n]{0,8}\\b(?:experience|exp)\\b");     // 4: N years … experience
    private static final int MAX_PROMPT_TEXT_LENGTH = 12_000;
    // 경력 연차 보정: 5년 이상이면 SENIOR로 본다(self-rules experienceLevel과 동일 기준).
    private static final int SENIOR_YEARS_THRESHOLD = 5;
    // "2024년" 같은 날짜/연도 오탐을 거르기 위한 현실적인 경력 연차 상한.
    private static final int MAX_REALISTIC_YEARS = 30;
    // requiredSkills 문장 필터: 이보다 길거나 단어가 많으면 스킬이 아니라 업무 문장으로 본다.
    private static final int SKILL_MAX_LENGTH = 30;
    private static final int SKILL_MAX_WORDS = 4;
    private static final Pattern SKILL_SENTENCE_PATTERN =
            Pattern.compile("및|또는|등의|에 대한|설계 및|개발 및|구축 및|담당");
    private static final List<String> KNOWN_SKILLS = List.of(
            "Java", "Spring", "Spring Boot", "MyBatis", "JPA", "SQL", "MySQL", "PostgreSQL",
            "Redis", "Kafka", "RabbitMQ", "Docker", "Kubernetes", "AWS", "Azure", "GCP",
            "Linux", "React", "TypeScript", "JavaScript", "Vue", "Node.js", "Python",
            "Django", "FastAPI", "REST", "GraphQL", "Git", "CI/CD", "Jenkins", "GitHub Actions",
            "JUnit", "Mockito", "Testing", "Monitoring", "Prometheus", "Grafana", "Elasticsearch",
            "Security", "OAuth", "JWT", "Agile", "Scrum", "Figma");

    private final BAnalysisProperties properties;
    private final BLocalLlmClient localLlmClient;
    private final BJobSentenceClassifier sentenceClassifier;
    private final ObjectMapper objectMapper;
    private final BAnthropicClient anthropicClient;
    private final OpenAiResponsesClient openAiResponsesClient;

    /**
     * 공고 분석 폴백 체인: 자체모델(Ollama) → Claude(Haiku) → OpenAI → self-rules-v1.
     * self-rules-v1 은 외부 호출 없는 결정적 규칙 생성기라 절대 예외로 끝나지 않는 최종 안전망이다.
     * 어떤 AI 도 시도되지 않은 경우(전부 비활성/미설정)는 의도된 기본 동작이라 fallback 으로 표시하지 않는다.
     */
    public GeneratedJobAnalysis generateJobAnalysis(ApplicationCase applicationCase, String postingText) {
        Classification classification = sentenceClassifier.classify(postingText);
        String lastError = null;

        // 1) 자체모델(Ollama) — provider 활성 시 재시도. 실패하면 아래로 폴백.
        if (properties.getLocalLlm().isEnabled()) {
            int maxAttempts = 1 + properties.getLocalLlm().getMaxRetries();
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                try {
                    String content = localLlmClient.chat(
                            JobAnalysisPromptCatalog.SYSTEM_PROMPT,
                            jobPrompt(applicationCase, postingText, classification),
                            jobAnalysisSchema());
                    JobAnalysisPayload payload = parseLocalJobPayload(content, postingText,
                            properties.getLocalLlm().getModel());
                    validateGrounding(payload, postingText);
                    log.info("Local LLM job analysis succeeded (attempt={}/{})", attempt, maxAttempts);
                    return new GeneratedJobAnalysis(payload, null, null);
                } catch (RuntimeException ex) {
                    lastError = safeMessage(ex);
                    log.warn("Local LLM job analysis attempt {}/{} failed: {}", attempt, maxAttempts, lastError);
                }
            }
        }

        // 2) 1차 폴백: Claude(Haiku) — 공통 키라 가장 안정적. 같은 스키마/검증 재사용.
        if (anthropicClient.configured()) {
            try {
                String content = anthropicClient.chat(
                        JobAnalysisPromptCatalog.SYSTEM_PROMPT,
                        jobPrompt(applicationCase, postingText, classification),
                        jobAnalysisSchema());
                JobAnalysisPayload payload = parseLocalJobPayload(content, postingText, anthropicClient.model());
                validateGrounding(payload, postingText);
                log.info("Claude job analysis succeeded");
                return new GeneratedJobAnalysis(payload, null, null);
            } catch (RuntimeException ex) {
                lastError = safeMessage(ex);
                log.warn("Claude job analysis failed: {}", lastError);
            }
        }

        // 3) 2차 폴백: OpenAI.
        if (openAiResponsesClient.configured()) {
            try {
                JobAnalysisPayload payload = openAiResponsesClient.analyzeJobPosting(applicationCase, postingText);
                log.info("OpenAI job analysis succeeded");
                return new GeneratedJobAnalysis(payload, null, null);
            } catch (RuntimeException ex) {
                lastError = safeMessage(ex);
                log.warn("OpenAI job analysis failed: {}", lastError);
            }
        }

        // 4) 최종 안전망: self-rules-v1.
        if (lastError == null) {
            // 아무 AI provider 도 시도되지 않음(전부 비활성/미설정) → 의도된 기본 동작.
            return new GeneratedJobAnalysis(
                    selfRulesJobAnalysis(applicationCase, postingText, classification), null, null);
        }
        String reason = "Local/Claude/OpenAI job analysis unavailable; fallback to self-rules-v1: " + lastError;
        log.warn("{}", reason);
        return new GeneratedJobAnalysis(
                selfRulesJobAnalysis(applicationCase, postingText, classification),
                reason,
                properties.getLocalLlm().getModel());
    }

    /**
     * 회사 분석 폴백 체인: 자체모델(Ollama) → Claude(Haiku) → OpenAI → self-rules-v1(최종 안전망).
     */
    public GeneratedCompanyAnalysis generateCompanyAnalysis(ApplicationCase applicationCase, String postingText) {
        Classification classification = sentenceClassifier.classify(postingText);
        String lastError = null;

        // 1) 자체모델(Ollama).
        if (properties.getLocalLlm().isEnabled()) {
            try {
                String content = localLlmClient.chat(
                        CompanyAnalysisPromptCatalog.SYSTEM_PROMPT,
                        companyPrompt(applicationCase, postingText, classification),
                        companyAnalysisSchema());
                CompanyAnalysisPayload payload = parseLocalCompanyPayload(content, applicationCase, postingText,
                        properties.getLocalLlm().getModel());
                return new GeneratedCompanyAnalysis(payload, null, null);
            } catch (RuntimeException ex) {
                lastError = safeMessage(ex);
                log.warn("Local LLM company analysis failed: {}", lastError);
            }
        }

        // 2) 1차 폴백: Claude(Haiku).
        if (anthropicClient.configured()) {
            try {
                String content = anthropicClient.chat(
                        CompanyAnalysisPromptCatalog.SYSTEM_PROMPT,
                        companyPrompt(applicationCase, postingText, classification),
                        companyAnalysisSchema());
                CompanyAnalysisPayload payload = parseLocalCompanyPayload(content, applicationCase, postingText,
                        anthropicClient.model());
                log.info("Claude company analysis succeeded");
                return new GeneratedCompanyAnalysis(payload, null, null);
            } catch (RuntimeException ex) {
                lastError = safeMessage(ex);
                log.warn("Claude company analysis failed: {}", lastError);
            }
        }

        // 3) 2차 폴백: OpenAI.
        if (openAiResponsesClient.configured()) {
            try {
                CompanyAnalysisPayload payload = openAiResponsesClient.analyzeCompany(applicationCase, postingText);
                log.info("OpenAI company analysis succeeded");
                return new GeneratedCompanyAnalysis(payload, null, null);
            } catch (RuntimeException ex) {
                lastError = safeMessage(ex);
                log.warn("OpenAI company analysis failed: {}", lastError);
            }
        }

        // 4) 최종 안전망: self-rules-v1.
        if (lastError == null) {
            return new GeneratedCompanyAnalysis(
                    selfRulesCompanyAnalysis(applicationCase, postingText, classification), null, null);
        }
        String reason = "Local/Claude/OpenAI company analysis unavailable; fallback to self-rules-v1: " + lastError;
        log.warn("{}", reason);
        return new GeneratedCompanyAnalysis(
                selfRulesCompanyAnalysis(applicationCase, postingText, classification),
                reason,
                properties.getLocalLlm().getModel());
    }

    private JobAnalysisPayload selfRulesJobAnalysis(ApplicationCase applicationCase,
                                                   String postingText,
                                                   Classification classification) {
        List<String> requiredSkills = extractRequiredSkills(postingText);
        List<String> preferredSkills = extractPreferredSkills(postingText, requiredSkills);
        String duties = joinClassified(classification, BJobSentenceClassifier.RESPONSIBILITY);
        String qualifications = firstNonBlank(
                joinClassified(classification, BJobSentenceClassifier.REQUIRED),
                joinClassified(classification, BJobSentenceClassifier.QUALIFICATION),
                extractSection(postingText, List.of("qualifications", "requirements", "required", "skills", "자격", "요건", "필수")));
        String summary = "%s %s posting extracted by the self-hosted pipeline. Required signals: %s."
                .formatted(defaultText(applicationCase.getCompanyName(), "Target company"),
                        defaultText(applicationCase.getJobTitle(), "Target role"),
                        joinPreview(requiredSkills));
        return new JobAnalysisPayload(
                employmentType(postingText),
                experienceLevel(postingText),
                toJson(requiredSkills),
                toJson(preferredSkills),
                defaultText(duties, firstSentences(postingText, 2)),
                defaultText(qualifications, "Review the posting text for detailed qualification wording."),
                difficulty(postingText, requiredSkills),
                summary,
                toJson(evidence(requiredSkills, postingText)),
                toJson(ambiguousConditions(postingText)),
                usage(SELF_RULES_MODEL, postingText.length(), summary.length() + estimateLength(requiredSkills) + estimateLength(preferredSkills)));
    }

    private CompanyAnalysisPayload selfRulesCompanyAnalysis(ApplicationCase applicationCase,
                                                           String postingText,
                                                           Classification classification) {
        String companyName = defaultText(applicationCase.getCompanyName(), "Target company");
        String companyInfo = joinClassified(classification, BJobSentenceClassifier.COMPANY_INFO);
        String summary = companyInfo.isBlank()
                ? "%s is represented from the uploaded job posting only. No external company API or OpenAI fallback was used."
                        .formatted(companyName)
                : "%s information was summarized from the uploaded job posting only: %s"
                        .formatted(companyName, truncate(companyInfo, 260));
        String interviewPoints = "Prepare to explain why this role matches your experience, how you handle the listed responsibilities, "
                + "and which evidence supports the required skills: " + joinPreview(extractRequiredSkills(postingText)) + ".";
        return new CompanyAnalysisPayload(
                summary,
                "Not externally researched in the default pipeline. Validate latest company news during user review.",
                industry(postingText),
                toJson(List.of()),
                interviewPoints,
                toJson(List.of(Map.of(
                        "type", "JOB_POSTING",
                        "label", "Uploaded job posting",
                        "model", SELF_RULES_MODEL))),
                toJson(verifiedFacts(applicationCase, postingText)),
                toJson(List.of(Map.of(
                        "inference", "Interview preparation should focus on job-posting responsibilities and skill evidence.",
                        "basis", "Derived from extracted posting sections by local rules."))),
                usage(SELF_RULES_MODEL, postingText.length(), summary.length() + interviewPoints.length()));
    }

    private JobAnalysisPayload parseLocalJobPayload(String content, String postingText, String modelLabel) {
        JsonNode root = parseObject(content);
        JobAnalysisPayload payload = new JobAnalysisPayload(
                text(root, "employmentType", employmentType(postingText)),
                reconcileExperienceLevel(text(root, "experienceLevel", experienceLevel(postingText)), postingText),
                filterSkillItems(arrayJson(root, "requiredSkills"), postingText),
                filterSkillItems(arrayJson(root, "preferredSkills"), null),
                requiredText(root, "duties"),
                requiredText(root, "qualifications"),
                normalizeDifficulty(text(root, "difficulty", "NORMAL")),
                requiredText(root, "summary"),
                objectArrayJson(root, "evidence", "field", "quote"),
                objectArrayJson(root, "ambiguousConditions", "condition", "assumption"),
                usage(modelLabel, postingText.length(), content.length()));
        validateJobPayload(payload);
        return payload;
    }

    /**
     * R1 모델이 "경력 5년 이상" 공고를 JUNIOR로 오분류하는 사례가 있어, 공고 원문에 명시된
     * 경력 연차를 정규식으로 파싱해 experienceLevel을 보정한다. 명시된 연차가 없으면 모델 값을 그대로 둔다.
     */
    private String reconcileExperienceLevel(String llmValue, String postingText) {
        String normalized = normalizeExperienceLevel(llmValue);
        Integer years = maxStatedYears(postingText);
        if (years == null) {
            return normalized;
        }
        if (years >= SENIOR_YEARS_THRESHOLD) {
            return "SENIOR";
        }
        if (years >= 1 && "JUNIOR".equals(normalized)) {
            return "MID";
        }
        return normalized;
    }

    /**
     * R1 모델이 enum 대신 "intermediate", "MEDIUM", 숫자 등 비표준 값을 반환하는 사례가 있어,
     * experienceLevel을 JUNIOR/MID/SENIOR 표준 값으로 정규화한다. 분류 불가 값은 MID로 수렴한다.
     */
    private String normalizeExperienceLevel(String value) {
        String lower = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (lower.equals("junior") || lower.equals("mid") || lower.equals("senior")) {
            return lower.toUpperCase(Locale.ROOT);
        }
        if (lower.contains("senior") || lower.contains("시니어") || lower.contains("lead")
                || lower.contains("principal") || lower.contains("staff") || lower.contains("expert")) {
            return "SENIOR";
        }
        if (lower.contains("junior") || lower.contains("entry") || lower.contains("intern")
                || lower.contains("new grad") || lower.contains("신입") || lower.contains("주니어")) {
            return "JUNIOR";
        }
        return "MID";
    }

    /**
     * 공고 원문에서 경력 연차를 추출한다. 연차 숫자가 경력 키워드(경력/경험/실무, experience/exp)와 결합된
     * 경우만 인정하므로, "2024년"(날짜)·"설립 10년차"(연혁)·"서비스 운영 5년"(기간)·"5년 연속 성장",
     * "operating for 5 yrs"(영어 기간)는 자연히 배제된다. 여러 건이면 가장 큰 연차를 반환한다.
     */
    private Integer maxStatedYears(String text) {
        Matcher matcher = EXPERIENCE_YEARS_PATTERN.matcher(text);
        Integer max = null;
        while (matcher.find()) {
            String digits = firstNonNull(matcher.group(1), matcher.group(2), matcher.group(3), matcher.group(4));
            if (digits == null || digits.length() > 2) {
                // 2자리 초과(>=100)는 경력 연차로 비현실적 → 무시(파싱 오버플로 방지 겸).
                continue;
            }
            int value = Integer.parseInt(digits);
            if (value < 1 || value > MAX_REALISTIC_YEARS) {
                continue;
            }
            if (max == null || value > max) {
                max = value;
            }
        }
        return max;
    }

    private String firstNonNull(String... values) {
        for (String value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /**
     * R1 모델이 requiredSkills/preferredSkills에 "결제 시스템 백엔드 API 설계 및 개발" 같은 업무 문장을
     * 스킬로 섞어 넣는 경우가 있어, 스킬처럼 보이지 않는 항목을 후처리로 걸러낸다.
     * 모든 항목이 걸러지면 fallbackPostingText 기반 규칙 추출로 폴백해 빈 배열을 막는다(폴백 텍스트가 없으면 원본 유지).
     */
    private String filterSkillItems(String skillsJson, String fallbackPostingText) {
        List<String> skills = parseList(skillsJson);
        if (skills.isEmpty()) {
            return skillsJson;
        }
        List<String> filtered = skills.stream().filter(this::looksLikeSkill).toList();
        if (filtered.size() == skills.size()) {
            return skillsJson;
        }
        if (filtered.isEmpty()) {
            return fallbackPostingText != null ? toJson(extractRequiredSkills(fallbackPostingText)) : skillsJson;
        }
        log.debug("Filtered {} non-skill item(s) from LLM skills: {} -> {}",
                skills.size() - filtered.size(), skills, filtered);
        return toJson(filtered);
    }

    private boolean looksLikeSkill(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty() || trimmed.length() > SKILL_MAX_LENGTH) {
            return false;
        }
        if (trimmed.split("\\s+").length > SKILL_MAX_WORDS) {
            return false;
        }
        return !SKILL_SENTENCE_PATTERN.matcher(trimmed).find();
    }

    private CompanyAnalysisPayload parseLocalCompanyPayload(String content,
                                                           ApplicationCase applicationCase,
                                                           String postingText,
                                                           String modelLabel) {
        JsonNode root = parseObject(content);
        CompanyAnalysisPayload payload = new CompanyAnalysisPayload(
                requiredText(root, "companySummary"),
                text(root, "recentIssues", "No external research was performed. Confirm recent issues during review."),
                text(root, "industry", industry(postingText)),
                arrayJson(root, "competitors"),
                requiredText(root, "interviewPoints"),
                objectArrayJson(root, "sources", "type", "label"),
                objectArrayJson(root, "verifiedFacts", "fact", "source"),
                objectArrayJson(root, "aiInferences", "inference", "basis"),
                usage(modelLabel, postingText.length(), content.length()));
        validateCompanyPayload(payload, applicationCase);
        return payload;
    }

    private void validateJobPayload(JobAnalysisPayload payload) {
        if (parseList(payload.requiredSkills()).isEmpty()) {
            throw new IllegalStateException("Local LLM job analysis has no requiredSkills.");
        }
        if (isBlank(payload.summary()) || payload.summary().length() < 20) {
            throw new IllegalStateException("Local LLM job analysis summary is too short.");
        }
        if (isBlank(payload.duties()) || isBlank(payload.qualifications())) {
            throw new IllegalStateException("Local LLM job analysis is missing duties or qualifications.");
        }
    }

    private void validateGrounding(JobAnalysisPayload payload, String postingText) {
        List<String> allSkills = new ArrayList<>();
        allSkills.addAll(parseList(payload.requiredSkills()));
        allSkills.addAll(parseList(payload.preferredSkills()));
        if (allSkills.isEmpty()) {
            return;
        }
        String normalized = postingText.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        int grounded = 0;
        for (String skill : allSkills) {
            if (isSkillGrounded(skill, normalized)) {
                grounded++;
            }
        }
        double ratio = (double) grounded / allSkills.size();
        double threshold = properties.getLocalLlm().getGroundingThreshold();
        if (ratio < threshold) {
            throw new IllegalStateException(
                    "Grounding check failed: %.0f%% < %.0f%% threshold (%d/%d skills grounded)"
                            .formatted(ratio * 100, threshold * 100, grounded, allSkills.size()));
        }
        log.debug("Grounding check passed: {}/{} skills ({}%)", grounded, allSkills.size(), (int) (ratio * 100));
    }

    private boolean isSkillGrounded(String skill, String normalizedSource) {
        String[] tokens = skill.toLowerCase(Locale.ROOT).split("[^0-9a-zA-Z가-힣]+");
        List<String> meaningful = new ArrayList<>();
        for (String token : tokens) {
            if (token.length() >= 2) {
                meaningful.add(token);
            }
        }
        if (meaningful.isEmpty()) {
            return false;
        }
        int hits = 0;
        for (String token : meaningful) {
            if (normalizedSource.contains(token)) {
                hits++;
            }
        }
        return meaningful.size() <= 2 ? hits >= 1 : (double) hits / meaningful.size() >= 0.5;
    }

    private void validateCompanyPayload(CompanyAnalysisPayload payload, ApplicationCase applicationCase) {
        if (isBlank(payload.companySummary()) || payload.companySummary().length() < 20) {
            throw new IllegalStateException("Local LLM company analysis summary is too short.");
        }
        if (isBlank(payload.interviewPoints())) {
            throw new IllegalStateException("Local LLM company analysis is missing interviewPoints.");
        }
        if (!hasArrayItems(payload.verifiedFacts()) && isBlank(applicationCase.getCompanyName())) {
            throw new IllegalStateException("Local LLM company analysis has no verifiable company fact.");
        }
    }

    private String jobPrompt(ApplicationCase applicationCase, String postingText, Classification classification) {
        return """
                회사명: %s
                직무명: %s

                문장 분류 신호(JSON):
                %s

                채용공고 원문:
                %s
                """.formatted(
                defaultText(applicationCase.getCompanyName(), "unknown"),
                defaultText(applicationCase.getJobTitle(), "unknown"),
                truncate(toJson(classification.asMap()), 4_000),
                truncate(postingText, MAX_PROMPT_TEXT_LENGTH));
    }

    private String companyPrompt(ApplicationCase applicationCase, String postingText, Classification classification) {
        return """
                회사명: %s
                직무명: %s

                COMPANY_INFO 분류 문장:
                %s

                채용공고 원문:
                %s
                """.formatted(
                defaultText(applicationCase.getCompanyName(), "unknown"),
                defaultText(applicationCase.getJobTitle(), "unknown"),
                String.join("\n", classification.textsByLabel(BJobSentenceClassifier.COMPANY_INFO)),
                truncate(postingText, MAX_PROMPT_TEXT_LENGTH));
    }

    private Map<String, Object> jobAnalysisSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("employmentType", stringSchema());
        properties.put("experienceLevel", Map.of("type", "string", "enum", List.of("JUNIOR", "MID", "SENIOR")));
        properties.put("requiredSkills", stringArraySchema());
        properties.put("preferredSkills", stringArraySchema());
        properties.put("duties", stringSchema());
        properties.put("qualifications", stringSchema());
        properties.put("difficulty", Map.of("type", "string", "enum", List.of("EASY", "NORMAL", "HARD")));
        properties.put("summary", stringSchema());
        properties.put("evidence", objectArraySchema(Map.of("field", stringSchema(), "quote", stringSchema()), List.of("field", "quote")));
        properties.put("ambiguousConditions", objectArraySchema(Map.of("condition", stringSchema(), "assumption", stringSchema()), List.of("condition", "assumption")));
        return objectSchema(properties, List.of(
                "employmentType", "experienceLevel", "requiredSkills", "preferredSkills",
                "duties", "qualifications", "difficulty", "summary", "evidence", "ambiguousConditions"));
    }

    private Map<String, Object> companyAnalysisSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("companySummary", stringSchema());
        properties.put("recentIssues", stringSchema());
        properties.put("industry", stringSchema());
        properties.put("competitors", stringArraySchema());
        properties.put("interviewPoints", stringSchema());
        properties.put("sources", objectArraySchema(Map.of("type", stringSchema(), "label", stringSchema()), List.of("type", "label")));
        properties.put("verifiedFacts", objectArraySchema(Map.of("fact", stringSchema(), "source", stringSchema()), List.of("fact", "source")));
        properties.put("aiInferences", objectArraySchema(Map.of("inference", stringSchema(), "basis", stringSchema()), List.of("inference", "basis")));
        return objectSchema(properties, List.of(
                "companySummary", "recentIssues", "industry", "competitors", "interviewPoints",
                "sources", "verifiedFacts", "aiInferences"));
    }

    private Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", properties,
                "required", required);
    }

    private Map<String, Object> objectArraySchema(Map<String, Object> properties, List<String> required) {
        return Map.of("type", "array", "items", objectSchema(properties, required));
    }

    private Map<String, Object> stringSchema() {
        return Map.of("type", "string");
    }

    private Map<String, Object> stringArraySchema() {
        return Map.of("type", "array", "items", stringSchema());
    }

    private List<String> extractRequiredSkills(String text) {
        LinkedHashSet<String> found = new LinkedHashSet<>();
        String lower = text.toLowerCase(Locale.ROOT);
        for (String skill : KNOWN_SKILLS) {
            if (lower.contains(skill.toLowerCase(Locale.ROOT))) {
                found.add(skill);
            }
        }
        if (found.isEmpty()) {
            found.add("Job posting analysis");
        }
        return found.stream().limit(10).toList();
    }

    private List<String> extractPreferredSkills(String text, List<String> requiredSkills) {
        String preferredText = extractSection(text, List.of("preferred", "nice to have", "plus", "우대"));
        LinkedHashSet<String> found = new LinkedHashSet<>();
        String lower = preferredText.toLowerCase(Locale.ROOT);
        for (String skill : KNOWN_SKILLS) {
            if (lower.contains(skill.toLowerCase(Locale.ROOT)) && !containsIgnoreCase(requiredSkills, skill)) {
                found.add(skill);
            }
        }
        return found.stream().limit(6).toList();
    }

    private String extractSection(String text, List<String> markers) {
        if (isBlank(text)) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            String lower = trimmed.toLowerCase(Locale.ROOT);
            if (!trimmed.isEmpty() && markers.stream().anyMatch(marker -> lower.contains(marker.toLowerCase(Locale.ROOT)))) {
                lines.add(trimmed);
            }
        }
        if (lines.isEmpty()) {
            return "";
        }
        return String.join("\n", lines.subList(0, Math.min(6, lines.size())));
    }

    private String employmentType(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("intern") || lower.contains("인턴")) {
            return "INTERN";
        }
        if (lower.contains("contract") || lower.contains("계약직")) {
            return "CONTRACT";
        }
        if (lower.contains("part-time") || lower.contains("part time")) {
            return "PART_TIME";
        }
        return "FULL_TIME";
    }

    private String experienceLevel(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        Integer years = maxStatedYears(text);
        if (lower.contains("senior") || lower.contains("lead") || lower.contains("시니어")
                || (years != null && years >= SENIOR_YEARS_THRESHOLD)) {
            return "SENIOR";
        }
        if (lower.contains("junior") || lower.contains("entry") || lower.contains("new grad")
                || lower.contains("신입") || lower.contains("주니어")) {
            return "JUNIOR";
        }
        return "MID";
    }

    private String difficulty(String text, List<String> requiredSkills) {
        String level = experienceLevel(text);
        if ("SENIOR".equals(level) || requiredSkills.size() >= 8) {
            return "HARD";
        }
        if ("JUNIOR".equals(level) && requiredSkills.size() <= 4) {
            return "EASY";
        }
        return "NORMAL";
    }

    private String industry(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("fintech") || lower.contains("payment") || lower.contains("bank") || lower.contains("금융")) {
            return "FINTECH";
        }
        if (lower.contains("commerce") || lower.contains("retail") || lower.contains("marketplace") || lower.contains("커머스")) {
            return "COMMERCE";
        }
        if (lower.contains("health") || lower.contains("medical") || lower.contains("헬스케어")) {
            return "HEALTHCARE";
        }
        if (lower.contains("education") || lower.contains("learning") || lower.contains("교육")) {
            return "EDTECH";
        }
        return "TECH";
    }

    private List<Map<String, String>> evidence(List<String> requiredSkills, String postingText) {
        List<Map<String, String>> rows = new ArrayList<>();
        for (String skill : requiredSkills.stream().limit(5).toList()) {
            rows.add(Map.of(
                    "field", "requiredSkills",
                    "quote", quoteFor(skill, postingText)));
        }
        return rows;
    }

    private List<Map<String, String>> verifiedFacts(ApplicationCase applicationCase, String postingText) {
        List<Map<String, String>> rows = new ArrayList<>();
        rows.add(Map.of(
                "fact", "Job title: " + defaultText(applicationCase.getJobTitle(), "unknown"),
                "source", "application_case"));
        rows.add(Map.of(
                "fact", "Company: " + defaultText(applicationCase.getCompanyName(), "unknown"),
                "source", "application_case"));
        rows.add(Map.of(
                "fact", "Posting text was extracted and quality-gated before analysis.",
                "source", quoteFor(defaultText(applicationCase.getJobTitle(), ""), postingText)));
        return rows;
    }

    private List<Map<String, String>> ambiguousConditions(String text) {
        List<Map<String, String>> rows = new ArrayList<>();
        if (!text.toLowerCase(Locale.ROOT).contains("salary") && !text.contains("연봉")) {
            rows.add(Map.of("condition", "salary", "assumption", "not specified in posting"));
        }
        if (!text.toLowerCase(Locale.ROOT).contains("remote") && !text.contains("재택")) {
            rows.add(Map.of("condition", "work location", "assumption", "confirm during interview"));
        }
        return rows;
    }

    private String quoteFor(String target, String text) {
        if (isBlank(target) || isBlank(text)) {
            return firstSentences(text, 1);
        }
        String lowerTarget = target.toLowerCase(Locale.ROOT);
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.toLowerCase(Locale.ROOT).contains(lowerTarget)) {
                return truncate(trimmed, 240);
            }
        }
        return firstSentences(text, 1);
    }

    private String joinClassified(Classification classification, String label) {
        return String.join("\n", classification.textsByLabel(label).stream().limit(8).toList());
    }

    private JsonNode parseObject(String content) {
        try {
            JsonNode root = objectMapper.readTree(content);
            if (!root.isObject()) {
                throw new IllegalStateException("Local LLM response is not a JSON object.");
            }
            return root;
        } catch (JacksonException ex) {
            throw new IllegalStateException("Local LLM response JSON cannot be parsed.", ex);
        }
    }

    private String requiredText(JsonNode root, String field) {
        String value = text(root, field, "");
        if (isBlank(value)) {
            throw new IllegalStateException("Local LLM response is missing " + field + ".");
        }
        return value;
    }

    private String text(JsonNode root, String field, String fallback) {
        JsonNode value = root.path(field);
        if (!value.isString()) {
            return fallback;
        }
        return value.asText().trim();
    }

    private String arrayJson(JsonNode root, String field) {
        JsonNode value = root.path(field);
        if (!value.isArray()) {
            throw new IllegalStateException("Local LLM response field is not an array: " + field);
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Local LLM response array cannot be serialized: " + field, ex);
        }
    }

    private String objectArrayJson(JsonNode root, String field, String... requiredKeys) {
        JsonNode value = root.path(field);
        if (!value.isArray()) {
            throw new IllegalStateException("Local LLM response field is not an array: " + field);
        }
        for (JsonNode item : value) {
            if (!item.isObject()) {
                throw new IllegalStateException("Local LLM response array item is not an object: " + field);
            }
            for (String key : requiredKeys) {
                if (!item.path(key).isString()) {
                    throw new IllegalStateException("Local LLM response object is missing " + field + "." + key);
                }
            }
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Local LLM response object array cannot be serialized: " + field, ex);
        }
    }

    private List<String> parseList(String json) {
        if (isBlank(json)) {
            return List.of();
        }
        try {
            List<String> values = objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
            return values == null ? List.of() : values.stream()
                    .filter(value -> !isBlank(value))
                    .map(String::trim)
                    .toList();
        } catch (JacksonException ex) {
            return List.of();
        }
    }

    private boolean hasArrayItems(String json) {
        if (isBlank(json)) {
            return false;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            return root.isArray() && !root.isEmpty();
        } catch (JacksonException ex) {
            return false;
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (JacksonException ex) {
            return "[]";
        }
    }

    private Usage usage(String model, int inputSize, int outputSize) {
        int inputTokens = estimateTokens(inputSize);
        int outputTokens = estimateTokens(outputSize);
        return new Usage(model, inputTokens, outputTokens, inputTokens + outputTokens);
    }

    private String firstSentences(String text, int maxSentences) {
        if (isBlank(text)) {
            return "";
        }
        String compact = text.trim().replaceAll("\\s+", " ");
        String[] parts = compact.split("(?<=[.!?])\\s+");
        List<String> selected = new ArrayList<>();
        for (String part : parts) {
            if (!part.isBlank()) {
                selected.add(part.trim());
            }
            if (selected.size() >= maxSentences) {
                break;
            }
        }
        String result = selected.isEmpty() ? compact : String.join(" ", selected);
        return truncate(result, 500);
    }

    private String joinPreview(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "none";
        }
        return String.join(", ", values.subList(0, Math.min(5, values.size())));
    }

    private int estimateLength(List<String> values) {
        return values == null ? 0 : values.stream().mapToInt(value -> value == null ? 0 : value.length()).sum();
    }

    private int estimateTokens(int chars) {
        return Math.max(0, (int) Math.ceil(Math.max(0, chars) / 4.0));
    }

    private String normalizeDifficulty(String value) {
        return switch (value) {
            case "EASY", "NORMAL", "HARD" -> value;
            default -> "NORMAL";
        };
    }

    private static boolean containsIgnoreCase(List<String> values, String target) {
        return values.stream().anyMatch(value -> value.equalsIgnoreCase(target));
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private static String defaultText(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private static String safeMessage(Throwable ex) {
        return isBlank(ex.getMessage()) ? ex.getClass().getSimpleName() : ex.getMessage();
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record GeneratedJobAnalysis(
            JobAnalysisPayload payload,
            String fallbackReason,
            String fallbackAttemptedModel
    ) {
        public boolean fellBack() {
            return fallbackReason != null;
        }
    }

    public record GeneratedCompanyAnalysis(
            CompanyAnalysisPayload payload,
            String fallbackReason,
            String fallbackAttemptedModel
    ) {
        public boolean fellBack() {
            return fallbackReason != null;
        }
    }
}
