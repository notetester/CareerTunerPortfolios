package com.careertuner.jobposting.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.jobposting.service.JobPostingFileStorage.StoredJobPostingFile;
import com.careertuner.jobposting.service.JobPostingTextExtractor.ExtractedPosting;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

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

    public Optional<ExtractedPosting> extractFile(StoredJobPostingFile file) {
        if (!properties.isEnabled()) {
            return Optional.empty();
        }
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("sourceType", file.sourceType());
        request.put("uploadedFileUrl", file.fileReference());
        request.put("fileName", file.originalFilename());
        request.put("contentType", file.contentType());
        request.put("filePath", file.path() == null ? null : file.path().toAbsolutePath().toString());
        return Optional.of(post(request, file.sourceType(), file.fileReference(), null));
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
            String modelVersionsJson = jsonValue(meta.path("modelVersionsJson"), modelVersions);
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
                    text(meta.path("fallbackReason"), warnings(meta)));
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
}
