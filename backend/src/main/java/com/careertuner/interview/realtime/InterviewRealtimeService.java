package com.careertuner.interview.realtime;

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

import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.applicationcase.service.ApplicationCaseAccessService;
import com.careertuner.applicationcase.service.OpenAiProperties;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.interview.domain.InterviewQuestion;
import com.careertuner.interview.domain.InterviewSession;
import com.careertuner.interview.dto.RealtimeSessionResponse;
import com.careertuner.interview.mapper.InterviewMapper;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 실시간 음성 AI 면접관 세션 발급.
 * 세션 컨텍스트(회사/직무/모드/질문)로 면접관 instructions 를 구성하고,
 * OpenAI Realtime sessions API 로 단기 ephemeral key 를 발급해 프런트에 내려준다.
 */
@Service
public class InterviewRealtimeService {

    private static final Map<String, String> MODE_LABELS = Map.of(
            "BASIC", "기본 면접", "JOB", "직무 면접", "PERSONALITY", "인성 면접",
            "PRESSURE", "압박 면접", "REAL", "실전 면접", "RESUME", "자소서 기반 면접",
            "PORTFOLIO", "포트폴리오 기반 면접", "COMPANY", "기업 맞춤 면접");

    private final OpenAiProperties openAiProperties;
    private final InterviewRealtimeProperties realtimeProperties;
    private final InterviewMapper interviewMapper;
    private final ApplicationCaseAccessService accessService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public InterviewRealtimeService(OpenAiProperties openAiProperties,
                                    InterviewRealtimeProperties realtimeProperties,
                                    InterviewMapper interviewMapper,
                                    ApplicationCaseAccessService accessService,
                                    ObjectMapper objectMapper) {
        this.openAiProperties = openAiProperties;
        this.realtimeProperties = realtimeProperties;
        this.interviewMapper = interviewMapper;
        this.accessService = accessService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public RealtimeSessionResponse createSession(Long userId, Long sessionId) {
        if (!openAiProperties.configured()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "OpenAI API 키가 설정되어 있지 않습니다.");
        }
        InterviewSession session = interviewMapper.findSessionByIdAndUserId(sessionId, userId);
        if (session == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "면접 세션을 찾을 수 없습니다.");
        }
        ApplicationCase applicationCase = accessService.requireOwned(userId, session.getApplicationCaseId());
        List<InterviewQuestion> questions = interviewMapper.findQuestionsBySessionId(sessionId);

        String instructions = buildInstructions(applicationCase, session.getMode(), questions);
        JsonNode root = requestEphemeralSession(instructions);

        String value = root.path("value").asText("");
        if (value.isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Realtime 세션 발급에 실패했습니다.");
        }
        Long expiresAt = root.path("expires_at").isMissingNode() ? null : root.path("expires_at").asLong();
        return new RealtimeSessionResponse(value, expiresAt, realtimeProperties.getModel(),
                realtimeProperties.getVoice(), realtimeUrl());
    }

    private String buildInstructions(ApplicationCase applicationCase, String mode, List<InterviewQuestion> questions) {
        String modeLabel = MODE_LABELS.getOrDefault(mode, mode);
        StringBuilder sb = new StringBuilder();
        sb.append("너는 한국어로 진행하는 전문 면접관이다.\n");
        sb.append("회사: ").append(applicationCase.getCompanyName()).append("\n");
        sb.append("직무: ").append(applicationCase.getJobTitle()).append("\n");
        sb.append("면접 유형: ").append(modeLabel).append("\n\n");
        sb.append("진행 규칙:\n");
        sb.append("- 한 번에 질문 하나만 한다. 지원자가 답하면 짧게 반응하고 다음 질문으로 넘어간다.\n");
        sb.append("- 답변이 부실하면 한 번 정도 꼬리 질문으로 파고든다.\n");
        sb.append("- 면접관답게 간결하고 또박또박 말한다. 정답을 대신 말해주지 않는다.\n");
        sb.append("- 인사 → 자기소개 요청 → 아래 질문들 → 마무리 순으로 자연스럽게 진행한다.\n");
        // 준비된 본 질문(꼬리 질문 제외) 최대 6개로 진행한다 (ADR-002).
        List<InterviewQuestion> mainQuestions = questions.stream()
                .filter(q -> q.getParentQuestionId() == null)
                .limit(6)
                .toList();
        if (!mainQuestions.isEmpty()) {
            sb.append("\n준비된 질문 목록(이 순서대로 모두 질문하고, 표현은 자연스럽게 변형 가능):\n");
            int idx = 1;
            for (InterviewQuestion q : mainQuestions) {
                sb.append(idx++).append(". ").append(q.getQuestion()).append("\n");
            }
            sb.append("\n모든 질문이 끝나면 면접을 정중히 마무리하고 수고했다고 인사한다.\n");
        } else {
            sb.append("\n공고와 직무를 바탕으로 적절한 질문을 즉석에서 만들어 진행한다.\n");
        }
        return sb.toString();
    }

    private JsonNode requestEphemeralSession(String instructions) {
        Map<String, Object> session = new LinkedHashMap<>();
        session.put("type", "realtime");
        session.put("model", realtimeProperties.getModel());
        session.put("instructions", instructions);
        session.put("audio", Map.of("output", Map.of("voice", realtimeProperties.getVoice())));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("session", session);
        try {
            String json = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder(URI.create(sessionsUrl()))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + openAiProperties.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                        "Realtime 세션 요청 실패: " + truncate(response.body(), 300));
            }
            return objectMapper.readTree(response.body());
        } catch (BusinessException ex) {
            throw ex;
        } catch (JacksonException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Realtime 응답을 처리하지 못했습니다.");
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Realtime 요청을 보내지 못했습니다.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Realtime 요청이 중단되었습니다.");
        }
    }

    private String baseUrl() {
        return openAiProperties.getBaseUrl().replaceAll("/+$", "");
    }

    private String sessionsUrl() {
        return baseUrl() + "/realtime/client_secrets";
    }

    private String realtimeUrl() {
        return baseUrl() + "/realtime/calls";
    }

    private String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max) + "...";
    }
}
