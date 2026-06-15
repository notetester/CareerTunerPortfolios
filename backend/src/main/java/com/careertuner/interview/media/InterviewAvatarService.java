package com.careertuner.interview.media;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.interview.domain.InterviewQuestion;
import com.careertuner.interview.domain.InterviewSession;
import com.careertuner.interview.mapper.InterviewMapper;
import com.careertuner.interview.media.dto.AvatarSessionResponse;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 아바타 화상 면접 — HeyGen LiveAvatar 세션 토큰 발급 (realtime ephemeral key 와 같은 패턴).
 *
 * <p>서버가 {@code POST /v1/sessions/token} (FULL 모드)으로 단기 토큰을 받아 프런트에 내려주면,
 * 프런트는 LiveAvatar SDK 로 연결해 준비된 질문을 {@code repeat()} 으로 읽힌다.
 * 분석(MediaPipe·음성)은 전부 온디바이스, 점수만 저장한다 (ADR-002).
 */
@Service
public class InterviewAvatarService {

    /** 아바타가 읽을 본 질문 수 상한 (꼬리 질문 제외). */
    private static final int MAX_QUESTIONS = 6;

    private final InterviewAvatarProperties properties;
    private final InterviewMapper interviewMapper;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public InterviewAvatarService(InterviewAvatarProperties properties,
                                  InterviewMapper interviewMapper,
                                  ObjectMapper objectMapper) {
        this.properties = properties;
        this.interviewMapper = interviewMapper;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public AvatarSessionResponse createSession(Long userId, Long sessionId) {
        if (!properties.configured()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "LiveAvatar API 키가 설정되어 있지 않습니다. (HEYGEN_API_KEY)");
        }
        InterviewSession session = interviewMapper.findSessionByIdAndUserId(sessionId, userId);
        if (session == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "면접 세션을 찾을 수 없습니다.");
        }
        // 미생성 게이트: 준비된 질문이 있어야 아바타 면접을 시작할 수 있다.
        List<String> questions = interviewMapper.findQuestionsBySessionId(sessionId).stream()
                .filter(q -> q.getParentQuestionId() == null)
                .map(InterviewQuestion::getQuestion)
                .limit(MAX_QUESTIONS)
                .toList();
        if (questions.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "준비된 면접 질문이 없습니다. 예상 면접 질문을 먼저 생성해 주세요.");
        }

        JsonNode data = requestSessionToken();
        String sessionToken = data.path("session_token").asText("");
        if (sessionToken.isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "LiveAvatar 세션 토큰 발급에 실패했습니다.");
        }
        return new AvatarSessionResponse(
                data.path("session_id").asText(null),
                sessionToken,
                properties.isSandbox(),
                properties.getLanguage(),
                questions);
    }

    private JsonNode requestSessionToken() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("mode", "FULL");
        if (!properties.getAvatarId().isBlank()) {
            body.put("avatar_id", properties.getAvatarId());
        }
        Map<String, Object> persona = new LinkedHashMap<>();
        if (!properties.getVoiceId().isBlank()) {
            persona.put("voice_id", properties.getVoiceId());
        }
        persona.put("language", properties.getLanguage());
        body.put("avatar_persona", persona);
        body.put("is_sandbox", properties.isSandbox());

        try {
            String json = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder(URI.create(tokenUrl()))
                    .timeout(Duration.ofSeconds(20))
                    .header("X-API-KEY", properties.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                        "LiveAvatar 세션 요청 실패: " + truncate(response.body(), 300));
            }
            return objectMapper.readTree(response.body()).path("data");
        } catch (BusinessException ex) {
            throw ex;
        } catch (JacksonException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "LiveAvatar 응답을 처리하지 못했습니다.");
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "LiveAvatar 요청을 보내지 못했습니다.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "LiveAvatar 요청이 중단되었습니다.");
        }
    }

    private String tokenUrl() {
        return properties.getBaseUrl().replaceAll("/+$", "") + "/v1/sessions/token";
    }

    private String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max) + "...";
    }
}
