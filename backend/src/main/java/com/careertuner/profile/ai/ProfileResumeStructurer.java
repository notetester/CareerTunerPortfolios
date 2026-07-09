package com.careertuner.profile.ai;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.careertuner.profile.dto.ProfileAnalyzeDraft;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * 이력서 텍스트 → education/career/projects (LLM) + skills 스캔 + portfolio URL.
 * Ollama 요청 형태는 BLocalLlmClient 와 동일(think:false, format=JSON Schema).
 * <p>기본 엔드포인트·모델은 <b>챗봇과 동일</b> ({@code AI_OLLAMA_BASE_URL} + {@code AI_AGENT_MODEL}/qwen3:8b)
 * 로 맞춰 Tailscale 공유 Ollama 재적재를 피한다. B 분석 전용 모델(jobposting-r1)과 분리.
 * LLM 실패 시 skills/portfolioLinks 만 채운 degraded draft 를 반환한다.
 */
@Slf4j
@Component
public class ProfileResumeStructurer {

    private static final Pattern URL_PATTERN = Pattern.compile(
            "https?://[\\w.-]+(?:/[\\w./%?=&+#~-]*)?",
            Pattern.CASE_INSENSITIVE);

    private static final int MAX_SOURCE_CHARS = 12000;

    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String model;
    private final int numCtx;
    private final int numPredict;
    private final Duration readTimeout;
    private final boolean llmEnabled;

    public ProfileResumeStructurer(
            ObjectMapper objectMapper,
            @Value("${careertuner.profile.resume-structurer.enabled:true}") boolean llmEnabled,
            // 챗봇(langchain4j)과 같은 Tailscale Ollama — B_ANALYSIS 전용 URL/모델과 섞지 않는다.
            @Value("${careertuner.profile.resume-structurer.base-url:${AI_OLLAMA_BASE_URL:http://localhost:11434}}")
            String baseUrl,
            @Value("${careertuner.profile.resume-structurer.model:${PROFILE_RESUME_OLLAMA_MODEL:${AI_AGENT_MODEL:qwen3:8b}}}")
            String model,
            @Value("${careertuner.profile.resume-structurer.num-ctx:8192}") int numCtx,
            @Value("${careertuner.profile.resume-structurer.num-predict:2048}") int numPredict,
            @Value("${careertuner.profile.resume-structurer.timeout:120s}") Duration readTimeout) {
        this.objectMapper = objectMapper;
        this.llmEnabled = llmEnabled;
        this.baseUrl = baseUrl;
        this.model = model;
        this.numCtx = numCtx;
        this.numPredict = numPredict;
        this.readTimeout = readTimeout;
        log.info("이력서 구조화 Ollama: enabled={} baseUrl={} model={} numCtx={}",
                llmEnabled, baseUrl, model, numCtx);
    }

    /**
     * 원문에서 구조화 초안을 만든다. DB 에 쓰지 않는다.
     * LLM 불가/실패여도 skills 스캔 + URL 추출은 수행한다.
     */
    public ProfileAnalyzeDraft structure(String resumeText) {
        String source = resumeText == null ? "" : resumeText;
        if (source.length() > MAX_SOURCE_CHARS) {
            source = source.substring(0, MAX_SOURCE_CHARS);
        }

        List<String> skills = ProfileKnownSkillsScanner.scan(source);
        List<String> portfolioLinks = extractUrls(source);

        Object educationRaw = List.of();
        Object careerRaw = List.of();
        Object projectsRaw = List.of();

        if (llmEnabled && !source.isBlank()) {
            try {
                Map<String, Object> llm = callLlm(source);
                educationRaw = llm.getOrDefault("education", List.of());
                careerRaw = llm.getOrDefault("career", List.of());
                projectsRaw = llm.getOrDefault("projects", List.of());
                log.info("이력서 구조화 LLM raw sizes edu={} career={} projects={} sourceChars={}",
                        sizeOf(educationRaw), sizeOf(careerRaw), sizeOf(projectsRaw), source.length());
            } catch (RuntimeException ex) {
                log.warn("이력서 구조화 LLM 실패 — skills/URL 만 반환: {} ({})",
                        ex.getMessage(), ex.getClass().getSimpleName(), ex);
            }
        }

        List<Map<String, String>> education = ProfileResumePostProcessor.processEducation(educationRaw, source);
        List<Map<String, String>> career = ProfileResumePostProcessor.processCareer(careerRaw, source);
        List<Map<String, String>> projects = ProfileResumePostProcessor.processProjects(projectsRaw, source);
        log.info("이력서 구조화 post-process kept edu={} career={} projects={} skills={}",
                education.size(), career.size(), projects.size(), skills.size());

        return new ProfileAnalyzeDraft(education, career, projects, skills, portfolioLinks);
    }

    /** 단위 테스트·결정적 경로: LLM 없이 후처리+스캔만. */
    public ProfileAnalyzeDraft structureDeterministic(String resumeText, Object educationRaw,
                                                      Object careerRaw, Object projectsRaw) {
        String source = resumeText == null ? "" : resumeText;
        return new ProfileAnalyzeDraft(
                ProfileResumePostProcessor.processEducation(educationRaw, source),
                ProfileResumePostProcessor.processCareer(careerRaw, source),
                ProfileResumePostProcessor.processProjects(projectsRaw, source),
                ProfileKnownSkillsScanner.scan(source),
                extractUrls(source));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> callLlm(String source) {
        var jdkClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        var requestFactory = new JdkClientHttpRequestFactory(jdkClient);
        requestFactory.setReadTimeout(readTimeout);

        RestClient restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", model);
        request.put("stream", false);
        request.put("think", false);
        request.put("options", Map.of(
                "temperature", 0,
                "num_ctx", numCtx,
                "num_predict", numPredict));
        request.put("format", jsonSchema());
        request.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt()),
                Map.of("role", "user", "content", "다음 이력서 원문에서 학력·경력·프로젝트를 추출하세요.\n\n" + source)));

        Map<?, ?> response = restClient.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(Map.class);

        if (response == null || response.get("message") == null) {
            throw new IllegalStateException("Ollama response is empty.");
        }
        Object message = response.get("message");
        String content;
        if (message instanceof Map<?, ?> msgMap) {
            content = String.valueOf(msgMap.get("content"));
        } else {
            throw new IllegalStateException("Ollama message shape unexpected.");
        }
        if (content == null || content.isBlank() || "null".equals(content)) {
            throw new IllegalStateException("Ollama content is empty.");
        }
        try {
            return objectMapper.readValue(content, Map.class);
        } catch (Exception e) {
            throw new IllegalStateException("Ollama JSON parse failed: " + e.getMessage(), e);
        }
    }

    private static String systemPrompt() {
        return """
                당신은 이력서 파서입니다. 주어진 원문에 실제로 있는 정보만 추출하세요.
                없는 항목은 빈 배열로 두세요. 지어내지 마세요.
                날짜는 YYYY-MM 형식만 사용하세요.
                education 항목 키: school, major, startDate, endDate, status
                status 허용값: 졸업, 재학, 휴학, 중퇴, 수료, 졸업예정
                career 항목 키: company, role, startDate, endDate, tasks, achievements
                projects 항목 키: title, type, role, startDate, endDate, description, result
                period 필드는 넣지 마세요(서버가 생성합니다).
                skills, preferences, desiredJob 은 추출하지 마세요.
                """;
    }

    private static Map<String, Object> jsonSchema() {
        // List.of / Map.of 는 null 원소를 허용하지 않음 — status enum 에 null 넣으면 NPE(메시지 null).
        Map<String, Object> stringOrNull = Map.of("type", List.of("string", "null"));
        Map<String, Object> statusType = new LinkedHashMap<>();
        statusType.put("type", List.of("string", "null"));
        statusType.put("enum", List.of("졸업", "재학", "휴학", "중퇴", "수료", "졸업예정"));

        Map<String, Object> educationProps = new LinkedHashMap<>();
        educationProps.put("school", stringOrNull);
        educationProps.put("major", stringOrNull);
        educationProps.put("startDate", stringOrNull);
        educationProps.put("endDate", stringOrNull);
        educationProps.put("status", statusType);
        Map<String, Object> educationItem = Map.of(
                "type", "object",
                "properties", educationProps,
                "additionalProperties", false);

        Map<String, Object> careerItem = Map.of(
                "type", "object",
                "properties", Map.of(
                        "company", stringOrNull,
                        "role", stringOrNull,
                        "startDate", stringOrNull,
                        "endDate", stringOrNull,
                        "tasks", stringOrNull,
                        "achievements", stringOrNull),
                "additionalProperties", false);
        Map<String, Object> projectItem = Map.of(
                "type", "object",
                "properties", Map.of(
                        "title", stringOrNull,
                        "type", stringOrNull,
                        "role", stringOrNull,
                        "startDate", stringOrNull,
                        "endDate", stringOrNull,
                        "description", stringOrNull,
                        "result", stringOrNull),
                "additionalProperties", false);
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "education", Map.of("type", "array", "items", educationItem),
                        "career", Map.of("type", "array", "items", careerItem),
                        "projects", Map.of("type", "array", "items", projectItem)),
                "required", List.of("education", "career", "projects"),
                "additionalProperties", false);
    }

    private static int sizeOf(Object raw) {
        return raw instanceof List<?> list ? list.size() : 0;
    }

    public static List<String> extractUrls(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        Set<String> urls = new LinkedHashSet<>();
        Matcher m = URL_PATTERN.matcher(text);
        while (m.find()) {
            String url = m.group().replaceAll("[),.;]+$", "");
            if (!url.isBlank()) {
                urls.add(url);
            }
            if (urls.size() >= 20) {
                break;
            }
        }
        return new ArrayList<>(urls);
    }
}
