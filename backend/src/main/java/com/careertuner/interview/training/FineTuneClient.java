package com.careertuner.interview.training;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.careertuner.applicationcase.service.OpenAiProperties;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** OpenAI 파인튜닝 클라이언트: JSONL 업로드(/v1/files) → FT 잡 생성(/v1/fine_tuning/jobs). */
@Service
public class FineTuneClient {

    private final OpenAiProperties openAiProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public FineTuneClient(OpenAiProperties openAiProperties, ObjectMapper objectMapper) {
        this.openAiProperties = openAiProperties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    }

    /** JSONL 학습 파일을 업로드하고 FT 잡을 생성한다. */
    public Result startFineTune(String jsonl, String baseModel) {
        if (!openAiProperties.configured()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "OpenAI API 키가 설정되어 있지 않습니다.");
        }
        String fileId = uploadFile(jsonl);
        JsonNode job = createJob(fileId, baseModel);
        return new Result(fileId, job.path("id").asText(""), job.path("status").asText("queued"), baseModel);
    }

    private String uploadFile(String jsonl) {
        String boundary = "----CareerTuner" + UUID.randomUUID().toString().replace("-", "");
        byte[] body = multipartBody(boundary, jsonl);
        HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(filesUrl()))
                .header("Authorization", "Bearer " + openAiProperties.getApiKey())
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)));
        try {
            String id = objectMapper.readTree(response.body()).path("id").asText("");
            if (id.isBlank()) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "학습 파일 업로드 응답에 파일 id 가 없습니다.");
            }
            return id;
        } catch (JacksonException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "학습 파일 업로드 응답을 처리하지 못했습니다.");
        }
    }

    private JsonNode createJob(String fileId, String baseModel) {
        Map<String, Object> body = Map.of("training_file", fileId, "model", baseModel);
        try {
            HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(jobsUrl()))
                    .header("Authorization", "Bearer " + openAiProperties.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8)));
            return objectMapper.readTree(response.body());
        } catch (JacksonException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "파인튜닝 잡 생성 응답을 처리하지 못했습니다.");
        }
    }

    private byte[] multipartBody(String boundary, String jsonl) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            out.write("Content-Disposition: form-data; name=\"purpose\"\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            out.write("fine-tune\r\n".getBytes(StandardCharsets.UTF_8));
            out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            out.write("Content-Disposition: form-data; name=\"file\"; filename=\"training.jsonl\"\r\n"
                    .getBytes(StandardCharsets.UTF_8));
            out.write("Content-Type: application/jsonl\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            out.write(jsonl.getBytes(StandardCharsets.UTF_8));
            out.write("\r\n".getBytes(StandardCharsets.UTF_8));
            out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "학습 파일 본문을 구성하지 못했습니다.");
        }
        return out.toByteArray();
    }

    private HttpResponse<String> send(HttpRequest.Builder builder) {
        try {
            HttpResponse<String> response = httpClient.send(builder.timeout(Duration.ofSeconds(60)).build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                        "OpenAI 파인튜닝 요청 실패 (" + response.statusCode() + ")");
            }
            return response;
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "OpenAI 파인튜닝 요청을 보내지 못했습니다.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "OpenAI 파인튜닝 요청이 중단되었습니다.");
        }
    }

    private String baseUrl() {
        return openAiProperties.getBaseUrl().replaceAll("/+$", "");
    }

    private String filesUrl() {
        return baseUrl() + "/files";
    }

    private String jobsUrl() {
        return baseUrl() + "/fine_tuning/jobs";
    }

    public record Result(String fileId, String jobId, String status, String baseModel) {
    }
}
