package com.careertuner.community.moderation.client;

import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

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

    private final RestClient restClient;
    private final OllamaProperties props;

    public OllamaClient(OllamaProperties props) {
        this.props = props;

        // JDK 21 HttpClient: connectTimeout 설정
        var jdkClient = HttpClient.newBuilder()
                .connectTimeout(props.getConnectTimeout())
                .build();
        // JdkClientHttpRequestFactory: RestClient가 내부적으로 사용할 HTTP 팩토리
        var requestFactory = new JdkClientHttpRequestFactory(jdkClient);
        requestFactory.setReadTimeout(props.getReadTimeout());

        this.restClient = RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * Ollama /api/chat 호출.
     *
     * @param systemPrompt 시스템 프롬프트 (검열 지시)
     * @param userText     검열 대상 텍스트 (호출 전 8000자 제한은 서비스 계층 책임)
     * @param jsonSchema   응답 JSON 스키마 (structured output 강제)
     * @return 응답의 message.content (JSON 문자열)
     * @throws IllegalStateException Ollama 응답이 비정상일 때
     */
    public String chat(String systemPrompt, String userText, Map<String, Object> jsonSchema) {
        OllamaChatRequest request = new OllamaChatRequest(
                props.getModel(),
                false,   // stream: 스트리밍 비활성화
                false,   // think: thinking 비활성화
                Map.of("temperature", 0, "num_ctx", 8192),
                jsonSchema,
                List.of(
                        new Message("system", systemPrompt),
                        new Message("user", userText)
                )
        );

        log.debug("Ollama 검열 요청: model={}, textLength={}", props.getModel(), userText.length());

        OllamaChatResponse response = restClient.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(OllamaChatResponse.class);

        if (response == null || response.message() == null) {
            throw new IllegalStateException("Ollama 응답이 비어 있습니다 (message=null)");
        }

        return response.message().content();
    }
}
