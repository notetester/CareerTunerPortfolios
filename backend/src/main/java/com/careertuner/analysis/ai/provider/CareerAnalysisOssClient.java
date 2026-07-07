package com.careertuner.analysis.ai.provider;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.careertuner.ai.common.budget.AiTotalTimeBudget;
import com.careertuner.ai.common.gpu.GpuPermitGate;
import com.careertuner.ai.common.settings.AiRuntimeSettings;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * C 자체 파인튜닝 모델(Ollama, OpenAI 호환 {@code /v1/chat/completions}) 호출 클라이언트.
 *
 * <p>D 의 {@code interview.service.OssLlmGateway} 패턴을 C 도메인으로 미러링했다(D 파일은 수정하지 않음).
 * 모델({@code careertuner-c-career-strategy-3b})은 <b>설명 텍스트만</b> 생성하도록 학습됐다 — 점수/판단은
 * 서버 규칙엔진(Mock)이 계산해 입력으로 준다(뉴로-심볼릭). 따라서 이 클라이언트는 설명 JSON 만 받아온다.
 *
 * <p>base-url 미설정 시 {@link #available()} 가 false 이고 상위 폴백이 OpenAI/Mock 으로 전환한다.
 * 소형 모델 방어: {@code response_format=json_object} + 앞뒤 잡설 제거({@link #extractJsonSpan}) +
 * <b>일시적 실패(5xx/네트워크/JSON 깨짐) 재시도</b>({@link #withRetry}). 3B 는 같은 입력에도 가끔 JSON 이
 * 깨지므로(stochastic), 재시도가 폴백 전 성공률을 끌어올린다. 점수/판단은 어느 경로든 규칙엔진 값이라
 * 재시도가 결과 일관성을 해치지 않는다.
 */
@Service
public class CareerAnalysisOssClient {

    private static final Logger log = LoggerFactory.getLogger(CareerAnalysisOssClient.class);

    /** 모델 설명 JSON 에 절대 들어오면 안 되는 키(점수/판단은 서버 권위). 들어와도 화이트리스트 병합이 무시하지만, 관측을 위해 로깅한다. */
    private static final Set<String> FORBIDDEN_KEYS = Set.of("fitScore", "score", "applyDecision", "decision");

    private final CareerAnalysisAiProviderProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final GpuPermitGate gpuPermitGate;
    private final AiRuntimeSettings aiRuntimeSettings;

    public CareerAnalysisOssClient(CareerAnalysisAiProviderProperties properties, ObjectMapper objectMapper,
                                   GpuPermitGate gpuPermitGate, AiRuntimeSettings aiRuntimeSettings) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.gpuPermitGate = gpuPermitGate;
        this.aiRuntimeSettings = aiRuntimeSettings;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getOss().getTimeout())
                .build();
    }

    /** base-url 설정 여부 — 폴백 디스패처가 OSS 시도 가능 여부 판단에 쓴다. */
    public boolean available() {
        return properties.getOss().configured();
    }

    /**
     * 적합도 설명 생성 호출. 설명 JSON(fitSummary/strengths/risks/strategyActions/learningTaskReasons)을 반환한다.
     * 일시적 실패는 설정된 횟수만큼 재시도하고, 모두 실패하면 {@link BusinessException} 으로 던져 상위 폴백을 유도한다.
     */
    public JsonNode requestFitExplain(String systemPrompt, String userPrompt) {
        CareerAnalysisAiProviderProperties.Oss oss = properties.getOss();
        if (!oss.configured()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "C 자체모델(OSS) base-url 이 설정되어 있지 않습니다.");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", oss.getModel());
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)));
        body.put("temperature", oss.getTemperature());
        // 출력 truncation 방지(설명이 길다 → 최소 1024). Ollama 는 max_tokens 를 num_predict 로 매핑한다.
        body.put("max_tokens", oss.getMaxTokens());
        body.put("response_format", Map.of("type", "json_object"));

        final String payload;
        try {
            payload = objectMapper.writeValueAsString(body);
        } catch (JacksonException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "C 자체모델 요청 직렬화에 실패했습니다.");
        }

        int attempts = Math.max(1, oss.getMaxRetries() + 1);
        long backoffMs = Math.max(0, oss.getRetryBackoff().toMillis());
        // 총 시간예산: 재시도·백오프를 포함한 전체 상한(E totalTimeBudget 패턴). 0/음수면 무제한(OFF).
        // DB 런타임 설정(ai.analysis.oss-total-time-budget-seconds) 우선, 행이 없으면 정적 oss.totalTimeBudget.
        Duration ossBudget = aiRuntimeSettings.analysisOssTotalTimeBudget();
        AiTotalTimeBudget budget = AiTotalTimeBudget.start(ossBudget);
        try {
            return withRetryWithinBudget(attempts, backoffMs, oss.getTimeout(), budget,
                    timeout -> sendOnce(oss, payload, timeout));
        } catch (OssTransientException ex) {
            // 일시적 실패가 재시도/예산까지 모두 소진 → 폴백 유도.
            log.warn("C 자체모델 시도 소진(최대 {}회/예산 {}) → OpenAI/Mock 폴백 유도: {}",
                    attempts, budget.unlimited() ? "무제한" : ossBudget.toSeconds() + "s",
                    ex.getMessage());
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, ex.getMessage());
        }
    }

    /** 단일 시도. 일시적 실패(5xx/네트워크/JSON 깨짐/빈응답)는 {@link OssTransientException}(재시도 대상)로, 4xx/중단은 {@link BusinessException}(즉시 폴백)로 던진다. */
    private JsonNode sendOnce(CareerAnalysisAiProviderProperties.Oss oss, String payload, Duration timeout) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(chatUrl()))
                    .timeout(positive(timeout))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));
            if (oss.getApiKey() != null && !oss.getApiKey().isBlank()) {
                builder.header("Authorization", "Bearer " + oss.getApiKey());
            }
            HttpResponse<String> response;
            try (GpuPermitGate.GpuPermit permit = gpuPermitGate.acquire("analysis")) {
                response = httpClient.send(builder.build(),
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            }
            int status = response.statusCode();
            if (status >= 500) {
                // 서버측 일시 오류(예: Ollama 500, 긴 출력 생성 실패) → 재시도 가치 있음.
                throw new OssTransientException("C 자체모델 요청 실패 (" + status + ")");
            }
            if (status < 200 || status >= 300) {
                // 4xx(잘못된 요청 등)는 재시도해도 동일 → 즉시 폴백.
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "C 자체모델 요청 실패 (" + status + ")");
            }
            JsonNode root = objectMapper.readTree(response.body());
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            return parseContent(content);
        } catch (IOException ex) {
            // 네트워크/타임아웃 등 → 재시도.
            throw new OssTransientException("C 자체모델 응답을 처리하지 못했습니다.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "C 자체모델 호출이 중단되었습니다.");
        }
    }

    /** 모델 content 를 JSON 으로 파싱. 빈 응답/JSON 깨짐은 소형 모델 특성상 재시도 가치가 있으므로 {@link OssTransientException} 로 던진다. */
    private JsonNode parseContent(String content) {
        String text = content == null ? "" : content.trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim();
        }
        text = extractJsonSpan(text);
        if (text.isBlank()) {
            throw new OssTransientException("C 자체모델 응답이 비어 있습니다.");
        }
        try {
            JsonNode node = objectMapper.readTree(text);
            warnIfForbiddenKeys(node);
            return node;
        } catch (JacksonException ex) {
            // post-R3 재벤치마크 실측: 3B PARSE_FAIL 은 전부 "닫는 괄호가 잘린 truncation"이었다
            // (decision_hold 류 긴 설명에서 발생). 닫힘 괄호 보충만으로 복구 가능한 손상은 여기서
            // 수리해 재시도/폴백 없이 살린다 — 판단값은 규칙엔진 소유라 설명 JSON 수리가 안전하다.
            String repaired = repairTruncatedJson(text);
            if (repaired != null) {
                try {
                    JsonNode node = objectMapper.readTree(repaired);
                    log.info("C 자체모델 truncated JSON 수리 성공(닫힘 괄호 {}자 보충).",
                            repaired.length() - text.length());
                    warnIfForbiddenKeys(node);
                    return node;
                } catch (JacksonException ignored) {
                    // 수리 실패 — 아래 기존 경로(재시도 대상)로.
                }
            }
            throw new OssTransientException("C 자체모델 응답이 JSON 형식이 아닙니다.");
        }
    }

    /**
     * truncation 손상 한정 JSON 수리 — 문자열/이스케이프 상태를 추적하며 {@code {}/[]} 스택을 세고,
     * 끝에서 열린 괄호만 남았으면 닫힘 괄호를 보충한다. 괄호 불일치(스택 오염)나 문자열 중간 절단
     * (닫히지 않은 따옴표)은 수리 대상이 아니므로 null 을 돌려 기존 실패 경로를 유지한다.
     */
    static String repairTruncatedJson(String text) {
        Deque<Character> stack = new ArrayDeque<>();
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (inString) {
                if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            switch (c) {
                case '"' -> inString = true;
                case '{' -> stack.push('}');
                case '[' -> stack.push(']');
                case '}', ']' -> {
                    if (stack.isEmpty() || stack.peek() != c) {
                        return null; // 괄호 불일치 — truncation 이 아닌 구조 손상
                    }
                    stack.pop();
                }
                default -> { }
            }
        }
        if (inString || stack.isEmpty()) {
            return null; // 문자열 중간 절단이거나 이미 균형(다른 원인의 파싱 실패)
        }
        StringBuilder repaired = new StringBuilder(text);
        // 잘린 꼬리의 ,(trailing comma) 는 닫힘 보충 전에 제거
        int end = repaired.length();
        while (end > 0 && Character.isWhitespace(repaired.charAt(end - 1))) {
            end--;
        }
        if (end > 0 && repaired.charAt(end - 1) == ',') {
            repaired.delete(end - 1, repaired.length());
        }
        while (!stack.isEmpty()) {
            repaired.append(stack.pop());
        }
        return repaired.toString();
    }

    /** 모델 출력에 금지키(점수/판단)가 섞였는지 관측 로깅 — 화이트리스트 병합이 무시하지만 실패 분류/감사에 쓴다. */
    private void warnIfForbiddenKeys(JsonNode node) {
        if (node == null || !node.isObject()) {
            return;
        }
        for (String key : FORBIDDEN_KEYS) {
            if (node.has(key)) {
                log.warn("C 자체모델 출력에 금지키 '{}' 포함 — 화이트리스트 병합으로 무시됨(점수/판단은 규칙엔진 권위).", key);
            }
        }
    }

    /**
     * 일시적 실패({@link OssTransientException})만 최대 {@code attempts} 회까지, <b>총 시간예산 안에서만</b>
     * 재시도한다(선형 백오프 — 단, 남은 예산으로 절삭). 예산이 소진되면 남은 시도 여부와 무관하게 중단한다.
     * 예산이 0/음수(무제한, {@link AiTotalTimeBudget#unlimited()})면 예산 체크 없이 기존 무예산 경로처럼 동작한다.
     * attempt 콜백은 남은 예산으로 절삭된 per-attempt 타임아웃(Duration)을 받는다.
     * 그 외 예외(예: {@link BusinessException} 4xx)는 재시도 없이 즉시 전파한다.
     */
    static <T> T withRetryWithinBudget(int attempts, long backoffMs, Duration perAttemptTimeout,
                                       AiTotalTimeBudget budget, Function<Duration, T> attempt) {
        OssTransientException last = null;
        for (int i = 0; i < attempts; i++) {
            if (budget.expired()) {
                throw last != null ? last : new OssTransientException("C 자체모델 총 시간예산이 소진되었습니다.");
            }
            try {
                return attempt.apply(budget.cap(perAttemptTimeout));
            } catch (OssTransientException ex) {
                last = ex;
                if (i < attempts - 1 && backoffMs > 0) {
                    long sleepMs = budget.capBackoffMs(backoffMs * (i + 1L));
                    if (sleepMs > 0) {
                        try {
                            Thread.sleep(sleepMs);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw ex;
                        }
                    }
                }
            }
        }
        throw last;
    }

    private static Duration positive(Duration value) {
        return value == null || value.isZero() || value.isNegative() ? Duration.ofMillis(1) : value;
    }

    /** 소형 모델이 JSON 앞뒤에 붙이는 잡설을 제거 — 첫 {/[ 부터 마지막 }/] 까지만 취한다(D 패턴 동일). */
    static String extractJsonSpan(String text) {
        int objStart = text.indexOf('{');
        int arrStart = text.indexOf('[');
        int start = objStart < 0 ? arrStart : (arrStart < 0 ? objStart : Math.min(objStart, arrStart));
        int end = Math.max(text.lastIndexOf('}'), text.lastIndexOf(']'));
        return (start >= 0 && end > start) ? text.substring(start, end + 1) : text;
    }

    private String chatUrl() {
        String base = properties.getOss().getBaseUrl().replaceAll("/+$", "");
        return base + "/chat/completions";
    }

    /** 재시도 대상(일시적) 실패 신호. requestFitExplain 안에서만 쓰이고, 소진 시 BusinessException 으로 변환된다. */
    static class OssTransientException extends RuntimeException {
        OssTransientException(String message) {
            super(message);
        }
    }
}
