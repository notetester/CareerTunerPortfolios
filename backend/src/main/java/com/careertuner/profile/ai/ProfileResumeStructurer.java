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

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.careertuner.ai.common.ollama.OllamaEndpointResolver;
import com.careertuner.profile.dto.ProfileAnalyzeDraft;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * 이력서 텍스트 → education/career/projects (LLM) + skills 스캔 + portfolio URL.
 * Ollama 요청 형태는 BLocalLlmClient 와 동일(think:false, format=JSON Schema).
 * <p>기본 엔드포인트·모델은 <b>챗봇과 동일</b> ({@code AI_OLLAMA_BASE_URL} + {@code AI_AGENT_MODEL}/qwen3:8b)
 * 로 맞춰 Tailscale 공유 Ollama 재적재를 피한다. B 분석 전용 모델(jobposting-r1)과 분리.
 * LLM 실패 시 skills/portfolioLinks 만 채운 degraded draft 를 반환한다.
 * <p>공유 엔드포인트를 따르는 경우(전용 URL 미지정) {@link OllamaEndpointResolver} 폴백 체인에 참여한다
 * — 실패 → reportFailure → 재프로브 후 다른 후보면 1회 재시도. 전용 URL 명시 시엔 그 값에 고정.
 * <p>로컬 Ollama 전 후보가 실패하면 챗봇과 동일한 호스티드 폴백(Claude → OpenAI, 키 없으면 스킵)을 탄다
 * — {@code ChatModelFallbackConfig} 의 빈 재사용. ⚠️이력서 원문이 외부 API 로 전송되므로
 * {@code PROFILE_RESUME_HOSTED_FALLBACK_ENABLED=false} 로 끌 수 있다. 그마저 실패하면 degraded draft.
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
    private final boolean hostedFallbackEnabled;

    /** 공용 Ollama 엔드포인트 폴백 리졸버(4090 사망 시 localhost 재프로브). 테스트에선 null 허용. */
    private final OllamaEndpointResolver endpointResolver;
    /** 전용 URL 미지정(=챗봇과 같은 공유 Ollama)일 때만 리졸버 체인을 따른다. */
    private final boolean followsSharedEndpoint;
    /** 챗봇 폴백과 동일한 호스티드 모델 빈(ChatModelFallbackConfig). 키 없으면 null. */
    private final ChatModel anthropic;
    private final ChatModel openAi;

    public ProfileResumeStructurer(
            ObjectMapper objectMapper,
            OllamaEndpointResolver endpointResolver,
            @Qualifier("anthropicChatModel") ObjectProvider<ChatModel> anthropicProvider,
            @Qualifier("openAiChatModel") ObjectProvider<ChatModel> openAiProvider,
            @Value("${careertuner.profile.resume-structurer.enabled:true}") boolean llmEnabled,
            @Value("${careertuner.profile.resume-structurer.hosted-fallback:true}") boolean hostedFallbackEnabled,
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
        this.hostedFallbackEnabled = hostedFallbackEnabled;
        this.baseUrl = baseUrl;
        this.model = model;
        this.numCtx = numCtx;
        this.numPredict = numPredict;
        this.readTimeout = readTimeout;
        this.endpointResolver = endpointResolver;
        this.followsSharedEndpoint = endpointResolver != null
                && stripTrailingSlash(baseUrl).equals(endpointResolver.primaryBaseUrl());
        this.anthropic = anthropicProvider == null ? null : anthropicProvider.getIfAvailable();
        this.openAi = openAiProvider == null ? null : openAiProvider.getIfAvailable();
        log.info("이력서 구조화 Ollama: enabled={} baseUrl={} model={} numCtx={} 공유엔드포인트폴백={} "
                        + "호스티드폴백={}(claude={}, openai={})",
                llmEnabled, baseUrl, model, numCtx, followsSharedEndpoint,
                hostedFallbackEnabled, anthropic != null, openAi != null);
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
            Map<String, Object> llm = null;
            try {
                llm = callLlm(source);
            } catch (RuntimeException ex) {
                log.warn("이력서 구조화 Ollama 실패 — 호스티드 폴백 시도: {} ({})",
                        ex.getMessage(), ex.getClass().getSimpleName());
                llm = callHostedFallback(source);
            }
            if (llm != null) {
                educationRaw = llm.getOrDefault("education", List.of());
                careerRaw = llm.getOrDefault("career", List.of());
                projectsRaw = llm.getOrDefault("projects", List.of());
                log.info("이력서 구조화 LLM raw sizes edu={} career={} projects={} sourceChars={}",
                        sizeOf(educationRaw), sizeOf(careerRaw), sizeOf(projectsRaw), source.length());
            } else {
                log.warn("이력서 구조화 전 provider 실패/미설정 — skills/URL 만 반환");
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

    /**
     * 로컬 Ollama 호출. 공유 엔드포인트를 따르면 문서화된 리졸버 패턴대로
     * "실패 → reportFailure → resolve 재시도 1회" — 4090이 죽어 있으면 localhost 후보로 넘어간다.
     */
    private Map<String, Object> callLlm(String source) {
        String first = activeBaseUrl();
        try {
            return callOllamaAt(first, source);
        } catch (RuntimeException ex) {
            if (!followsSharedEndpoint) {
                throw ex;
            }
            endpointResolver.reportFailure(first);
            String second = activeBaseUrl();
            if (second.equals(first)) {
                throw ex;
            }
            log.warn("이력서 구조화 Ollama {} 실패 → 폴백 {} 로 1회 재시도: {}", first, second, ex.getMessage());
            return callOllamaAt(second, source);
        }
    }

    private String activeBaseUrl() {
        return followsSharedEndpoint ? endpointResolver.resolve() : baseUrl;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> callOllamaAt(String targetBaseUrl, String source) {
        var jdkClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        var requestFactory = new JdkClientHttpRequestFactory(jdkClient);
        requestFactory.setReadTimeout(readTimeout);

        RestClient restClient = RestClient.builder()
                .baseUrl(targetBaseUrl)
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

    /**
     * 챗봇과 동일한 호스티드 폴백: Claude → OpenAI 순서(FallbackChatModel 과 동일), 키 없는 provider 는 스킵.
     * 호스티드 경로는 Ollama 의 format(JSON Schema) 강제가 없어 프롬프트로 JSON 만 요구하고,
     * 결과는 어차피 ProfileResumePostProcessor 가 원문 대조로 걸러낸다. 전부 실패하면 null.
     */
    private Map<String, Object> callHostedFallback(String source) {
        if (!hostedFallbackEnabled) {
            log.info("이력서 구조화 호스티드 폴백 비활성(PROFILE_RESUME_HOSTED_FALLBACK_ENABLED=false)");
            return null;
        }
        ChatRequest request = ChatRequest.builder()
                .messages(
                        SystemMessage.from(systemPrompt() + HOSTED_JSON_ONLY_SUFFIX),
                        UserMessage.from("다음 이력서 원문에서 학력·경력·프로젝트를 추출하세요.\n\n" + source))
                .build();
        for (var provider : List.of(
                Map.entry("Claude", java.util.Optional.ofNullable(anthropic)),
                Map.entry("OpenAI", java.util.Optional.ofNullable(openAi)))) {
            ChatModel chatModel = provider.getValue().orElse(null);
            if (chatModel == null) {
                continue;
            }
            try {
                ChatResponse response = chatModel.chat(request);
                Map<String, Object> parsed = parseJsonObject(response.aiMessage().text());
                log.info("이력서 구조화 호스티드 폴백 성공: provider={}", provider.getKey());
                return parsed;
            } catch (RuntimeException ex) {
                log.warn("이력서 구조화 {} 폴백 실패: {}", provider.getKey(), ex.getMessage());
            }
        }
        return null;
    }

    private static final String HOSTED_JSON_ONLY_SUFFIX = """

            응답은 반드시 JSON 객체 하나만 출력하세요. 마크다운 코드펜스·설명·주석을 붙이지 마세요.
            최상위 키는 education, career, projects 세 개이며 각 값은 배열입니다. 없으면 빈 배열.
            """;

    /** 호스티드 응답에서 JSON 객체만 발라 파싱(코드펜스·앞뒤 설명 허용). */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonObject(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("hosted response is empty.");
        }
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalStateException("hosted response has no JSON object.");
        }
        try {
            return objectMapper.readValue(content.substring(start, end + 1), Map.class);
        } catch (Exception e) {
            throw new IllegalStateException("hosted JSON parse failed: " + e.getMessage(), e);
        }
    }

    private static String stripTrailingSlash(String url) {
        return url == null ? "" : url.trim().replaceAll("/+$", "");
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
