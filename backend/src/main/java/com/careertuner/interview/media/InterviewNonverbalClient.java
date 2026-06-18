package com.careertuner.interview.media;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.interview.media.dto.TranscribeResponse;
import com.careertuner.interview.media.dto.VoiceScoreResponse;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 비언어 자체 추론 서버(Python FastAPI, ADR-006) 호출 — 음성 → 피처 추출 → 점수.
 *
 * <p>원본 음성을 base64 로 보내면 서버가 ffmpeg 16kHz 변환·피처 추출 후 점수를 낸다
 * (LightGBM 모델이 있으면 모델, 없으면 규칙 폴백). 외부 API(Inworld)를 자체 서버로 대체하기 위한 클라이언트.
 */
@Service
public class InterviewNonverbalClient {

    private final InterviewNonverbalProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public InterviewNonverbalClient(InterviewNonverbalProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public boolean enabled() {
        return properties.configured();
    }

    /**
     * @param audioBase64     녹음 원본(webm/wav 등) base64
     * @param audioFormat     컨테이너 확장자(webm/wav). null 이면 webm
     * @param transcriptChars 지원자 발화 글자수(말속도 계산용)
     * @param fillerCount     군말 개수
     * @param latencySec      질문→발화 평균 지연(초). null 이면 미측정으로 처리
     */
    public VoiceScoreResponse scoreVoice(String audioBase64, String audioFormat,
                                         Integer transcriptChars, Integer fillerCount, Double latencySec) {
        if (!properties.configured()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "비언어 추론 서버가 비활성화되어 있습니다. (careertuner.interview.nonverbal.enabled)");
        }
        if (audioBase64 == null || audioBase64.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "오디오 데이터가 비어 있습니다.");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("audio_base64", audioBase64);
        body.put("audio_format", audioFormat == null || audioFormat.isBlank() ? "webm" : audioFormat);
        body.put("transcript_chars", transcriptChars != null ? transcriptChars : 0);
        body.put("filler_count", fillerCount != null ? fillerCount : 0);
        body.put("latency_sec", latencySec != null ? latencySec : -1.0);

        JsonNode root = post(scoreUrl(), body);
        return new VoiceScoreResponse(
                root.path("score").asInt(0),
                root.path("detail"),
                root.path("metrics"),
                root.path("source").asText("rule"));
    }

    /** 음성 → 텍스트 (자체 STT, serve /transcribe). B 베이직 답변 전사 — OpenAI Whisper API 대체. */
    public TranscribeResponse transcribe(String audioBase64, String audioFormat, String language) {
        if (!properties.configured()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "비언어 추론 서버가 비활성화되어 있습니다. (careertuner.interview.nonverbal.enabled)");
        }
        if (audioBase64 == null || audioBase64.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "오디오 데이터가 비어 있습니다.");
        }
        String lang = language == null || language.isBlank() ? "ko" : language;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("audio_base64", audioBase64);
        body.put("audio_format", audioFormat == null || audioFormat.isBlank() ? "webm" : audioFormat);
        body.put("language", lang);

        JsonNode root = post(transcribeUrl(), body);
        return new TranscribeResponse(
                root.path("text").asText(""),
                root.path("language").asText(lang),
                root.path("duration").asDouble(0));
    }

    private JsonNode post(String url, Map<String, Object> body) {
        try {
            String json = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                        "음성 점수 요청 실패: " + truncate(response.body(), 300));
            }
            return objectMapper.readTree(response.body());
        } catch (BusinessException ex) {
            throw ex;
        } catch (JacksonException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "음성 점수 응답을 처리하지 못했습니다.");
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "음성 점수 서버에 연결하지 못했습니다. (serve 미기동?)");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "음성 점수 요청이 중단되었습니다.");
        }
    }

    private String scoreUrl() {
        return properties.getServeUrl().replaceAll("/+$", "") + "/score/voice-base64";
    }

    private String transcribeUrl() {
        return properties.getServeUrl().replaceAll("/+$", "") + "/transcribe";
    }

    private String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max) + "...";
    }
}
