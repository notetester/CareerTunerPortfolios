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
    @DisplayName("loads only the 8B model and keeps repeated warmup requests idempotent")
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
        service = new CorrectionModelWarmupService(properties, new ObjectMapper());

        CorrectionWarmupResponse first = service.warmAsync("TEST");
        service.awaitIfInProgress(Duration.ofSeconds(2));
        CorrectionWarmupResponse second = service.warmAsync("TEST");

        assertThat(first.status()).isEqualTo("STARTED");
        assertThat(second.status()).isEqualTo("ALREADY_WARM");
        assertThat(requestBody.get()).contains(
                "careertuner-e-correction:8b",
                "\"prompt\":\"\"",
                "\"keep_alive\":\"600s\"");
        assertThat(requestBody.get()).doesNotContain("careertuner-e-correction-3b");
    }

    @Test
    @DisplayName("skips warmup when the self provider is disabled")
    void warmAsync_skipsWhenSelfProviderDisabled() {
        CorrectionAiProperties properties = new CorrectionAiProperties();
        service = new CorrectionModelWarmupService(properties, new ObjectMapper());

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
