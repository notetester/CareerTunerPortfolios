package com.careertuner.correction.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.stereotype.Service;

import com.careertuner.ai.common.gpu.GpuPermitGate;
import com.careertuner.correction.dto.CorrectionWarmupResponse;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@Slf4j
public class CorrectionModelWarmupService {

    private static final String STARTED = "STARTED";
    private static final String IN_PROGRESS = "IN_PROGRESS";
    private static final String ALREADY_WARM = "ALREADY_WARM";
    private static final String COOLDOWN = "COOLDOWN";
    private static final String SKIPPED = "SKIPPED";

    private final CorrectionAiProperties properties;
    private final ObjectMapper objectMapper;
    private final GpuPermitGate gpuPermitGate;
    private final HttpClient httpClient;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "correction-model-warmup");
        thread.setDaemon(true);
        return thread;
    });

    private CompletableFuture<Void> inFlight;
    private Instant warmUntil = Instant.EPOCH;
    private Instant retryAfter = Instant.EPOCH;

    public CorrectionModelWarmupService(
            CorrectionAiProperties properties, ObjectMapper objectMapper, GpuPermitGate gpuPermitGate) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.gpuPermitGate = gpuPermitGate;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getSelf().getConnectTimeout())
                .build();
    }

    public CorrectionWarmupResponse warmAsync(String reason) {
        String model = properties.getSelf().getModel();
        if (!properties.getWarmup().isEnabled() || !properties.selfProviderEnabled()) {
            return new CorrectionWarmupResponse(SKIPPED, model);
        }

        synchronized (this) {
            Instant now = Instant.now();
            if (now.isBefore(warmUntil)) {
                return new CorrectionWarmupResponse(ALREADY_WARM, model);
            }
            if (inFlight != null && !inFlight.isDone()) {
                return new CorrectionWarmupResponse(IN_PROGRESS, model);
            }
            if (now.isBefore(retryAfter)) {
                return new CorrectionWarmupResponse(COOLDOWN, model);
            }

            // whenComplete 를 inFlight 체인에 포함해야 awaitIfInProgress 가 completeWarmup(warmUntil 세팅)까지
            // 대기한다. 분리하면 loadModel 완료~콜백 실행 사이에 warmAsync 가 끼어들어 중복 warmup 이 시작되는
            // race 가 생긴다(전체 테스트 스위트에서 간헐 재현되던 실동작 버그).
            inFlight = CompletableFuture.runAsync(() -> loadModel(model), executor)
                    .whenComplete((ignored, failure) -> completeWarmup(model, reason, failure));
            log.info("Correction model warmup started model={} reason={}", model, safeReason(reason));
            return new CorrectionWarmupResponse(STARTED, model);
        }
    }

    public void awaitIfInProgress(Duration timeout) {
        CompletableFuture<Void> active;
        synchronized (this) {
            active = inFlight;
        }
        if (active == null || active.isDone()) {
            return;
        }
        long waitMillis = Math.max(1, positive(timeout).toMillis());
        try {
            active.get(waitMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            log.warn("Correction model warmup is still running after {} ms.", waitMillis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            log.warn("Correction model warmup did not complete successfully: {}", ex.getMessage());
        }
    }

    private void loadModel(String model) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("prompt", "");
        body.put("stream", false);
        body.put("keep_alive", keepAliveValue(properties.getWarmup().getKeepAlive()));
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(generateUrl()))
                    .timeout(positive(properties.getWarmup().getRequestTimeout()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(body), StandardCharsets.UTF_8));
            String apiKey = properties.getSelf().getApiKey();
            if (apiKey != null && !apiKey.isBlank()) {
                request.header("Authorization", "Bearer " + apiKey);
            }
            HttpResponse<Void> response;
            try (GpuPermitGate.GpuPermit permit = gpuPermitGate.acquire("correction-warmup")) {
                response = httpClient.send(
                        request.build(), HttpResponse.BodyHandlers.discarding());
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new WarmupException("Ollama warmup failed (" + response.statusCode() + ").");
            }
        } catch (JacksonException ex) {
            throw new WarmupException("Ollama warmup request could not be serialized.", ex);
        } catch (IOException ex) {
            throw new WarmupException("Ollama warmup communication failed.", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new WarmupException("Ollama warmup was interrupted.", ex);
        }
    }

    private synchronized void completeWarmup(String model, String reason, Throwable failure) {
        if (failure == null) {
            warmUntil = Instant.now().plus(positive(properties.getWarmup().getKeepAlive()));
            retryAfter = Instant.EPOCH;
            log.info("Correction model warmup completed model={} reason={}", model, safeReason(reason));
        } else {
            warmUntil = Instant.EPOCH;
            retryAfter = Instant.now().plus(positive(properties.getWarmup().getRetryCooldown()));
            log.warn("Correction model warmup failed model={} reason={}: {}",
                    model, safeReason(reason), rootMessage(failure));
        }
        inFlight = null;
    }

    private String generateUrl() {
        String base = properties.getSelf().getBaseUrl().replaceAll("/+$", "");
        if (base.endsWith("/v1")) {
            base = base.substring(0, base.length() - 3);
        }
        return base + "/api/generate";
    }

    private String keepAliveValue(Duration value) {
        return Math.max(1, positive(value).toSeconds()) + "s";
    }

    private Duration positive(Duration value) {
        return value == null || value.isZero() || value.isNegative() ? Duration.ofMillis(1) : value;
    }

    private String safeReason(String reason) {
        return reason == null || reason.isBlank() ? "UNSPECIFIED" : reason.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    private String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage();
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    static class WarmupException extends RuntimeException {
        WarmupException(String message) {
            super(message);
        }

        WarmupException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
