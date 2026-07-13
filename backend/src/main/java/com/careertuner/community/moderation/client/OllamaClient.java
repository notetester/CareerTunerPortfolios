package com.careertuner.community.moderation.client;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.careertuner.ai.common.budget.AiTotalTimeBudget;
import com.careertuner.ai.common.gpu.GpuPermitGate;
import com.careertuner.community.moderation.config.OllamaProperties;
import com.careertuner.community.moderation.dto.OllamaChatRequest;
import com.careertuner.community.moderation.dto.OllamaChatRequest.Message;
import com.careertuner.community.moderation.dto.OllamaChatResponse;

/**
 * Ollama /api/chat 호출 클라이언트.
 *
 * RestClient를 사용하며, 타임아웃은 OllamaProperties에서 주입받는다.
 * - 연결 타임아웃 3초: 서버 접속 실패 시 빠르게 포기
 * - 읽기 타임아웃 30초: LLM 추론 대기
 *
 * Ollama 네이티브 API(/api/chat)를 그대로 사용한다.
 * OpenAI 호환 형식(/v1/chat/completions)이 아님에 주의.
 */
@Component
public class OllamaClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaClient.class);

    /** 5xx·접속/읽기 타임아웃 한정 추가 재시도 횟수 (총 시도 = 1 + 2 = 3회). */
    private static final int MAX_RETRIES = 2;
    /** 시도별 백오프 대기(ms): 1차 재시도 전 1s, 2차 재시도 전 3s. */
    private static final long[] BACKOFF_MILLIS = {1000L, 3000L};

    private final RestClient restClient;
    private final OllamaProperties props;
    private final GpuPermitGate gpuPermitGate;

    public OllamaClient(OllamaProperties props, GpuPermitGate gpuPermitGate) {
        this.props = props;
        this.gpuPermitGate = gpuPermitGate;
        // 예산 OFF(기본) 경로가 쓰는 공용 클라이언트 — 기존 read timeout 그대로.
        this.restClient = buildRestClient(props.getReadTimeout());
    }

    /** read timeout 만 달리하는 RestClient 생성 — 예산 ON 이면 시도마다 남은 예산으로 절삭해 새로 만든다. */
    private RestClient buildRestClient(Duration readTimeout) {
        // JDK 21 HttpClient: connectTimeout 설정
        var jdkClient = HttpClient.newBuilder()
                .connectTimeout(props.getConnectTimeout())
                .build();
        // JdkClientHttpRequestFactory: RestClient가 내부적으로 사용할 HTTP 팩토리
        var requestFactory = new JdkClientHttpRequestFactory(jdkClient);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * Ollama /api/chat 호출 — 범용 모델({@code ai.ollama.model}). 태깅·면접추출·신고분류가 쓴다.
     *
     * @param systemPrompt 시스템 프롬프트 (작업 지시)
     * @param userText     대상 텍스트 (호출 전 8000자 제한은 서비스 계층 책임)
     * @param jsonSchema   응답 JSON 스키마 (structured output 강제)
     * @return 응답의 message.content (JSON 문자열)
     * @throws IllegalStateException Ollama 응답이 비정상일 때
     */
    public String chat(String systemPrompt, String userText, Map<String, Object> jsonSchema) {
        return chatWith(props.getModel(), systemPrompt, userText, jsonSchema);
    }

    /**
     * 텍스트 검열 전용 모델({@code ai.ollama.moderation-model}) 호출.
     *
     * <p>요청 형식·옵션은 {@link #chat} 과 동일하고 모델명만 다르다. Modelfile 파라미터
     * (careertuner-mod 의 {@code num_predict=64} 등)는 해당 모델에만 적용되므로,
     * 긴 JSON 을 뽑는 태깅/면접추출 경로가 검열 모델 교체에 영향받지 않는다.
     */
    public String chatModeration(String systemPrompt, String userText, Map<String, Object> jsonSchema) {
        return chatWith(props.getModerationModel(), systemPrompt, userText, jsonSchema);
    }

    /** 모델명만 달리하는 공통 요청 조립 — 옵션(temperature/num_ctx)·stream·think 는 모든 경로 동일. */
    private String chatWith(String model, String systemPrompt, String userText, Map<String, Object> jsonSchema) {
        OllamaChatRequest request = new OllamaChatRequest(
                model,
                false,   // stream: 스트리밍 비활성화
                false,   // think: thinking 비활성화
                Map.of("temperature", 0, "num_ctx", 8192),
                jsonSchema,
                List.of(
                        new Message("system", systemPrompt),
                        new Message("user", userText)
                )
        );

        log.debug("Ollama 요청: model={}, textLength={}", model, userText.length());
        return execute(request);
    }

    /**
     * Ollama /api/chat vision 호출 — user 메시지에 base64 이미지 배열을 실어 멀티모달 판정한다.
     * gemma4 처럼 vision capability 가 있는 모델에서만 유효하다.
     *
     * @param imagesBase64 data: 접두사 없는 순수 base64 문자열 목록(Ollama 네이티브 형식)
     */
    public String chatVision(String systemPrompt, String userText,
                             List<String> imagesBase64, Map<String, Object> jsonSchema) {
        OllamaChatRequest request = new OllamaChatRequest(
                props.getVisionModel(),
                false,
                false,
                Map.of("temperature", 0, "num_ctx", 8192),
                jsonSchema,
                List.of(
                        new Message("system", systemPrompt),
                        new Message("user", userText, imagesBase64)
                )
        );

        log.debug("Ollama vision 검열 요청: model={}, images={}", props.getVisionModel(), imagesBase64.size());
        return execute(request);
    }

    /** 공통 전송 루프 — 텍스트/vision 요청을 동일한 재시도·GPU 게이트·시간예산 정책으로 처리한다. */
    private String execute(OllamaChatRequest request) {
        // 5xx(HttpServerErrorException)·접속/읽기 타임아웃(ResourceAccessException)에 한해
        // 지수 백오프로 최대 2회 추가 재시도(총 3회). 4xx는 즉시 전파한다.
        // 총 시간예산(재시도·백오프 포함 전체 상한). 0/음수면 무제한(OFF, 기존 동작 그대로).
        AiTotalTimeBudget budget = AiTotalTimeBudget.start(props.getTotalTimeBudget());
        RuntimeException lastException = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            // 백오프 후 예산이 소진됐으면 새 시도를 시작하지 않는다(첫 시도는 항상 실행).
            if (attempt > 0 && budget.expired()) {
                log.warn("Ollama 총 시간예산 소진, 재시도 {} 시작 전 중단", attempt);
                break;
            }
            // 예산 ON 이면 per-attempt read timeout 을 남은 예산으로 절삭(OFF 면 공용 클라이언트 그대로).
            RestClient client = budget.unlimited()
                    ? restClient
                    : buildRestClient(budget.cap(props.getReadTimeout()));
            try {
                OllamaChatResponse response;
                try (GpuPermitGate.GpuPermit permit = gpuPermitGate.acquire("moderation")) {
                    response = client.post()
                            .uri("/api/chat")
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(request)
                            .retrieve()
                            .body(OllamaChatResponse.class);
                }

                if (response == null || response.message() == null) {
                    throw new IllegalStateException("Ollama 응답이 비어 있습니다 (message=null)");
                }

                return response.message().content();
            } catch (HttpServerErrorException | ResourceAccessException e) {
                lastException = e;
                if (attempt >= MAX_RETRIES) {
                    break;
                }
                // 예산 소진 시 남은 재시도를 포기하고 기존 실패 경로(lastException 전파)로 나간다.
                if (budget.expired()) {
                    log.warn("Ollama 총 시간예산 소진, 재시도 중단: {}", e.toString());
                    break;
                }
                long backoff = budget.capBackoffMs(BACKOFF_MILLIS[attempt]);
                log.warn("Ollama 호출 실패, 재시도 {}/{} ({}ms 후): {}",
                        attempt + 1, MAX_RETRIES, backoff, e.toString());
                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }

        // 모든 재시도 실패: 마지막 예외를 그대로 전파(기존 호출부 catch 동작 유지).
        throw lastException;
    }
}
