package com.careertuner.correction.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.careertuner.ai.common.gpu.GpuPermitGate;
import com.careertuner.correction.dto.CorrectionWarmupResponse;
import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.ObjectMapper;

class CorrectionModelWarmupServiceTest {

    private HttpServer server;
    private CorrectionModelWarmupService service;

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.shutdown();
        }
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("loads only the configured 3B model and keeps repeated warmup requests idempotent")
    void warmAsync_loadsPrimaryModelOnce() throws IOException {
        AtomicReference<String> requestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/generate", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();

        CorrectionAiProperties properties = properties("http://localhost:" + server.getAddress().getPort() + "/v1");
        service = new CorrectionModelWarmupService(properties, new ObjectMapper(), GpuPermitGate.disabled());

        CorrectionWarmupResponse first = service.warmAsync("TEST");
        service.awaitIfInProgress(Duration.ofSeconds(2));
        CorrectionWarmupResponse second = service.warmAsync("TEST");

        assertThat(first.status()).isEqualTo("STARTED");
        assertThat(second.status()).isEqualTo("ALREADY_WARM");
        assertThat(requestBody.get()).contains(
                "careertuner-e-correction-3b:latest",
                "\"prompt\":\"\"",
                "\"keep_alive\":\"600s\"");
    }

    @Test
    @DisplayName("awaitIfInProgress returns only after warm state is visible (no duplicate warmup race)")
    void awaitIfInProgress_guaranteesWarmStateBeforeReturn() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/generate", exchange -> {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        CorrectionAiProperties properties = properties("http://localhost:" + server.getAddress().getPort() + "/v1");

        // 과거 버그: awaitIfInProgress 가 loadModel 단계만 기다려, completeWarmup(warmUntil 세팅) 전에
        // 다음 warmAsync 가 끼어들면 중복 warmup(STARTED)이 시작됐다(스위트 부하에서 간헐 재현).
        // 수정 후에는 await 복귀 즉시 ALREADY_WARM 이 보장되어야 한다 — 반복으로 race 창을 두드린다.
        for (int i = 0; i < 20; i++) {
            CorrectionModelWarmupService iteration = new CorrectionModelWarmupService(properties, new ObjectMapper(), GpuPermitGate.disabled());
            try {
                assertThat(iteration.warmAsync("TEST").status()).isEqualTo("STARTED");
                iteration.awaitIfInProgress(Duration.ofSeconds(2));
                assertThat(iteration.warmAsync("TEST").status())
                        .as("iteration %d: await 복귀 후에는 warm 상태가 보여야 한다", i)
                        .isEqualTo("ALREADY_WARM");
            } finally {
                iteration.shutdown();
            }
        }
    }

    @Test
    @DisplayName("skips warmup when the self provider is disabled")
    void warmAsync_skipsWhenSelfProviderDisabled() {
        CorrectionAiProperties properties = new CorrectionAiProperties();
        service = new CorrectionModelWarmupService(properties, new ObjectMapper(), GpuPermitGate.disabled());

        assertThat(service.warmAsync("TEST").status()).isEqualTo("SKIPPED");
    }

    private CorrectionAiProperties properties(String baseUrl) {
        CorrectionAiProperties properties = new CorrectionAiProperties();
        properties.setProvider("self");
        properties.getSelf().setBaseUrl(baseUrl);
        properties.getWarmup().setKeepAlive(Duration.ofMinutes(10));
        properties.getWarmup().setRequestTimeout(Duration.ofSeconds(2));
        return properties;
    }
}
