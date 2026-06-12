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
import com.careertuner.interview.media.dto.VoiceAnalysisResponse;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Inworld STT(voice profiling) 호출 — 음성 답변의 감정·보컬스타일·피치 라벨 분석.
 *
 * <p>프런트가 녹음을 16kHz mono PCM16(LINEAR16) base64 로 변환해 보내면,
 * 서버가 Inworld {@code POST /stt/v1/transcribe} 를 호출해 voiceProfile 을 받아 내려준다.
 * API 키는 서버에만 두고 프런트로 내려가지 않는다.
 *
 * <p>키가 없으면 {@link #enabled()} 가 false — 프런트는 감정 분석 없이 브라우저 지표만으로 점수를 낸다.
 */
@Service
public class InterviewVoiceService {

    private final InterviewVoiceProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public InterviewVoiceService(InterviewVoiceProperties properties, ObjectMapper objectMapper) {
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
     * @param audioBase64     LINEAR16(PCM16) mono 오디오 base64
     * @param sampleRateHertz 샘플레이트 (프런트 변환 기준 16000)
     * @param language        BCP-47 (기본 ko)
     */
    public VoiceAnalysisResponse analyze(String audioBase64, int sampleRateHertz, String language) {
        if (!properties.configured()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "Inworld API 키가 설정되어 있지 않습니다. (INWORLD_API_KEY)");
        }
        if (audioBase64 == null || audioBase64.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "오디오 데이터가 비어 있습니다.");
        }

        Map<String, Object> transcribeConfig = new LinkedHashMap<>();
        transcribeConfig.put("modelId", properties.getModelId());
        transcribeConfig.put("audioEncoding", "LINEAR16");
        transcribeConfig.put("language", language == null || language.isBlank() ? "ko" : language);
        transcribeConfig.put("sampleRateHertz", sampleRateHertz > 0 ? sampleRateHertz : 16000);
        transcribeConfig.put("numberOfChannels", 1);
        transcribeConfig.put("voiceProfileConfig",
                Map.of("enableVoiceProfile", true, "topN", properties.getProfileTopN()));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("transcribeConfig", transcribeConfig);
        body.put("audioData", Map.of("content", audioBase64));

        JsonNode root = post(body);
        String transcript = root.path("transcription").path("transcript").asText("");
        JsonNode voiceProfile = root.path("voiceProfile");
        return new VoiceAnalysisResponse(transcript, voiceProfile.isMissingNode() ? null : voiceProfile);
    }

    private JsonNode post(Map<String, Object> body) {
        try {
            String json = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder(URI.create(transcribeUrl()))
                    .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                    .header("Authorization", "Basic " + properties.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                        "음성 분석 요청 실패: " + truncate(response.body(), 300));
            }
            return objectMapper.readTree(response.body());
        } catch (BusinessException ex) {
            throw ex;
        } catch (JacksonException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "음성 분석 응답을 처리하지 못했습니다.");
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "음성 분석 요청을 보내지 못했습니다.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "음성 분석 요청이 중단되었습니다.");
        }
    }

    private String transcribeUrl() {
        return properties.getBaseUrl().replaceAll("/+$", "") + "/stt/v1/transcribe";
    }

    private String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max) + "...";
    }
}
