package com.careertuner.jobposting.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.jobposting.service.JobPostingFileStorage.StoredJobPostingFile;
import com.careertuner.jobposting.service.JobPostingTextExtractor.ExtractedPosting;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class JobPostingAiWorkerClient {

    private final JobPostingAiWorkerProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public JobPostingAiWorkerClient(JobPostingAiWorkerProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, HttpClient.newBuilder()
                .connectTimeout(properties.getTimeout())
                .build());
    }

    JobPostingAiWorkerClient(JobPostingAiWorkerProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    static JobPostingAiWorkerClient disabled() {
        return new JobPostingAiWorkerClient(new JobPostingAiWorkerProperties(), null);
    }

    /** capabilities 조회는 등록 화면 옵션용이라 추출(120s)과 달리 짧게 잡는다. */
    private static final Duration CAPABILITIES_TIMEOUT = Duration.ofSeconds(5);
    /** 옵션 요청마다 원격 왕복하지 않도록 결과(성공·실패 모두)를 짧게 캐시한다(워커 다운 시 반복 timeout 방지). */
    private static final Duration CAPABILITIES_CACHE_TTL = Duration.ofSeconds(30);
    /** 앱이 아는 OCR 엔진만 인정한다 — 알 수 없는 엔진명만 오면 Self OCR 을 켜지 않는다(방어). */
    private static final Set<String> KNOWN_ENGINES = Set.of("paddleocr", "ppstructure");

    private volatile CachedCapabilities cachedCapabilities;

    /**
     * 워커 {@code GET /capabilities} 로 준비된 OCR 엔진(PaddleOCR/PPStructureV3)을 조회한다.
     * 모델 옵션 조회는 워커 장애로 실패하면 안 되므로, 비활성·오프라인·타임아웃·파싱 실패·응답 이상은 예외 대신
     * {@link WorkerCapabilities#unavailable(String)} 로 안전하게 degrade 하고, 결과를 짧게 캐시한다.
     */
    public WorkerCapabilities capabilities() {
        CachedCapabilities cached = cachedCapabilities;
        if (cached != null && !cached.isExpired()) {
            return cached.value();
        }
        WorkerCapabilities fresh = probeCapabilities();
        cachedCapabilities = new CachedCapabilities(fresh, Instant.now().plus(CAPABILITIES_CACHE_TTL));
        return fresh;
    }

    private WorkerCapabilities probeCapabilities() {
        if (!properties.isEnabled()) {
            return WorkerCapabilities.unavailable("disabled");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.capabilitiesUrl()))
                    .timeout(CAPABILITIES_TIMEOUT)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return WorkerCapabilities.unavailable("status:" + response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            String status = root.path("status").asText("");
            if (!"ok".equals(status)) {
                return WorkerCapabilities.unavailable("workerStatus:" + (status.isBlank() ? "missing" : status));
            }
            List<String> readyEngines = new ArrayList<>();
            JsonNode engines = root.path("readyEngines");
            if (engines.isArray()) {
                for (JsonNode engine : engines) {
                    String name = engine.asText("");
                    if (KNOWN_ENGINES.contains(name)) {
                        readyEngines.add(name);
                    }
                }
            }
            return new WorkerCapabilities(true, readyEngines, null);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return WorkerCapabilities.unavailable("interrupted");
        } catch (RuntimeException | IOException ex) {
            return WorkerCapabilities.unavailable(ex.getClass().getSimpleName());
        }
    }

    private record CachedCapabilities(WorkerCapabilities value, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    public Optional<ExtractedPosting> extractFile(StoredJobPostingFile file) {
        if (!properties.isEnabled()) {
            return Optional.empty();
        }
        return Optional.of(post(buildFileRequest(file), file.sourceType(), file.fileReference(), null));
    }

    /**
     * 워커에 보낼 파일 추출 요청 바디를 만든다. sendBytes=on 이면 파일 바이트를 base64 로 동봉해
     * 워커가 파일경로 공유(co-location) 없이 OCR 할 수 있게 한다(off 면 기존 filePath 방식 그대로).
     * 워커는 fileBase64 가 있으면 filePath 보다 우선 사용한다.
     */
    Map<String, Object> buildFileRequest(StoredJobPostingFile file) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("sourceType", file.sourceType());
        request.put("uploadedFileUrl", file.fileReference());
        request.put("fileName", file.originalFilename());
        request.put("contentType", file.contentType());
        request.put("filePath", file.path() == null ? null : file.path().toAbsolutePath().toString());
        if (properties.isSendBytes() && file.bytes() != null) {
            request.put("fileBase64", Base64.getEncoder().encodeToString(file.bytes()));
        }
        return request;
    }

    public Optional<ExtractedPosting> extractText(String sourceType,
                                                  String uploadedFileUrl,
                                                  String originalText,
                                                  String extractedText) {
        if (!properties.isEnabled()) {
            return Optional.empty();
        }
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("sourceType", sourceType);
        request.put("uploadedFileUrl", uploadedFileUrl);
        request.put("originalText", originalText);
        request.put("extractedText", extractedText);
        request.put("text", extractedText == null || extractedText.isBlank() ? originalText : extractedText);
        return Optional.of(post(request, sourceType, uploadedFileUrl, originalText));
    }

    private ExtractedPosting post(Map<String, Object> requestBody,
                                  String sourceType,
                                  String uploadedFileUrl,
                                  String originalText) {
        try {
            String body = objectMapper.writeValueAsString(requestBody);
            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.extractJobPostingUrl()))
                    .timeout(properties.getTimeout())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                Optional<ExtractedPosting> failureResult = parseFailureResponse(
                        response.body(),
                        sourceType,
                        uploadedFileUrl,
                        originalText);
                if (failureResult.isPresent()) {
                    return failureResult.get();
                }
                throw new BusinessException(
                        ErrorCode.INTERNAL_ERROR,
                        "Python job posting worker request failed. status=%d".formatted(response.statusCode()));
            }
            return parseResponse(response.body(), sourceType, uploadedFileUrl, originalText);
        } catch (BusinessException ex) {
            throw ex;
        } catch (JacksonException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Python job posting worker JSON handling failed.");
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Python job posting worker is unavailable.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Python job posting worker request was interrupted.");
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Python job posting worker URL is invalid.");
        }
    }

    private Optional<ExtractedPosting> parseFailureResponse(String responseBody,
                                                            String sourceType,
                                                            String uploadedFileUrl,
                                                            String originalText) {
        if (responseBody == null || responseBody.isBlank()) {
            return Optional.empty();
        }
        try {
            ExtractedPosting extracted = parseResponse(responseBody, sourceType, uploadedFileUrl, originalText);
            return "FAILED".equals(extracted.qualityStatus()) ? Optional.of(extracted) : Optional.empty();
        } catch (BusinessException ex) {
            return Optional.empty();
        }
    }

    private ExtractedPosting parseResponse(String responseBody,
                                           String sourceType,
                                           String uploadedFileUrl,
                                           String originalText) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode meta = root.path("meta").isMissingNode() ? root : root.path("meta");
            JsonNode modelVersions = meta.path("modelVersions");
            String qualityReportJson = jsonValue(meta.path("qualityReportJson"), meta);
            String modelVersionsJson = mergeOcrProvider(jsonValue(meta.path("modelVersionsJson"), modelVersions), "worker");
            return new ExtractedPosting(
                    text(root.path("sourceType"), sourceType),
                    text(root.path("uploadedFileUrl"), uploadedFileUrl),
                    originalText,
                    root.path("text").asText(""),
                    null,
                    text(meta.path("strategy"), null),
                    integer(meta.path("qualityScore")),
                    text(meta.path("qualityStatus"), null),
                    qualityReportJson,
                    modelVersionsJson,
                    meta.path("fallbackEligible").asBoolean(false),
                    text(meta.path("fallbackReason"), warnings(meta)),
                    "worker",
                    null);
        } catch (JacksonException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Python job posting worker response is invalid.");
        }
    }

    private String jsonValue(JsonNode preferred, JsonNode fallback) throws JacksonException {
        if (preferred != null && !preferred.isMissingNode() && !preferred.isNull()) {
            if (preferred.isTextual()) {
                String value = preferred.asText("");
                if (!value.isBlank()) {
                    return value;
                }
            } else {
                return objectMapper.writeValueAsString(preferred);
            }
        }
        if (fallback == null || fallback.isMissingNode() || fallback.isNull()) {
            return null;
        }
        return objectMapper.writeValueAsString(fallback);
    }

    /**
     * worker modelVersionsJson 을 보존하면서 {@code ocr.provider} 만 앱 확정값으로 보강.
     * 기존 {@code ocr} 하위 정보(예: workerModel)는 유지하고 provider 만 덮어쓴다(설계: 앱 provider 가 최종 권위, worker 세부는 보존).
     */
    private String mergeOcrProvider(String modelVersionsJson, String provider) {
        try {
            ObjectNode node;
            if (modelVersionsJson == null || modelVersionsJson.isBlank()) {
                node = objectMapper.createObjectNode();
            } else {
                JsonNode parsed = objectMapper.readTree(modelVersionsJson);
                node = parsed.isObject() ? (ObjectNode) parsed : objectMapper.createObjectNode();
            }
            JsonNode existingOcr = node.path("ocr");
            ObjectNode ocr = existingOcr.isObject() ? (ObjectNode) existingOcr : objectMapper.createObjectNode();
            ocr.put("provider", provider);
            node.set("ocr", ocr);
            return objectMapper.writeValueAsString(node);
        } catch (JacksonException ex) {
            return modelVersionsJson;
        }
    }

    private static String text(JsonNode node, String fallback) {
        String value = node.asText("");
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private static Integer integer(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return node.asInt();
    }

    private static String warnings(JsonNode meta) {
        JsonNode warnings = meta.path("warnings");
        if (!warnings.isArray() || warnings.size() == 0) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (JsonNode warning : warnings) {
            if (!builder.isEmpty()) {
                builder.append(", ");
            }
            builder.append(warning.asText());
        }
        return builder.toString();
    }

    /**
     * 워커 capabilities 응답. {@code available} 은 워커가 정상 응답했는지, {@code readyEngines} 는 실제 초기화까지
     * 성공한 OCR 엔진 목록, {@code reason} 은 미가용 사유(가용하면 null)다.
     */
    public record WorkerCapabilities(boolean available, List<String> readyEngines, String reason) {

        public WorkerCapabilities {
            readyEngines = readyEngines == null ? List.of() : List.copyOf(readyEngines);
        }

        static WorkerCapabilities unavailable(String reason) {
            return new WorkerCapabilities(false, List.of(), reason);
        }

        /** Self OCR 선택 가능 조건: 워커가 응답했고 준비된 엔진이 하나 이상. */
        public boolean anyEngineReady() {
            return available && !readyEngines.isEmpty();
        }
    }
}
