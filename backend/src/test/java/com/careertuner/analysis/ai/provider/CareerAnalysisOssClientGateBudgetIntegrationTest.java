package com.careertuner.analysis.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.careertuner.ai.common.gpu.GpuPermitGate;
import com.careertuner.ai.common.settings.AiRuntimeSettings;
import com.careertuner.common.exception.BusinessException;
import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * GPU permit gate(옵션4)·총 시간예산(#226)의 <b>ON 경로</b>를 실제 HTTP 경유로 검증하는 통합 테스트.
 *
 * <p>단위 테스트({@code GpuPermitGateTest}, {@code AiTotalTimeBudgetTest})는 장치 자체만 검증한다 —
 * 여기서는 로컬 스텁 서버(모의 Ollama)를 띄워 {@code CareerAnalysisOssClient} 를 관통시켜
 * "게이트 ON 이면 실제 HTTP 동시 수가 permits 로 묶이고, 예산 ON 이면 재시도가 벽시계로 유계"임을
 * 실측한다. 4090 없이 로컬에서 재현 가능.
 */
class CareerAnalysisOssClientGateBudgetIntegrationTest {

    private static final String OK_BODY =
            "{\"choices\":[{\"message\":{\"content\":\"{\\\"fitSummary\\\":\\\"ok\\\"}\"}}]}";

    private HttpServer server;
    private final AtomicInteger active = new AtomicInteger();
    private final AtomicInteger maxActive = new AtomicInteger();
    private final AtomicInteger requestCount = new AtomicInteger();

    @BeforeEach
    void resetCounters() {
        active.set(0);
        maxActive.set(0);
        requestCount.set(0);
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    /** delayMs 대기 후 status 로 응답하는 모의 Ollama. 동시 in-flight 최대값을 기록한다. */
    private String startStub(long delayMs, int status, CountDownLatch rendezvous) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/chat/completions", exchange -> {
            requestCount.incrementAndGet();
            int now = active.incrementAndGet();
            maxActive.accumulateAndGet(now, Math::max);
            try {
                if (rendezvous != null) {
                    rendezvous.countDown();
                    rendezvous.await(2, TimeUnit.SECONDS); // 두 요청이 겹칠 수 있으면 여기서 만난다
                }
                if (delayMs > 0) {
                    Thread.sleep(delayMs);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                active.decrementAndGet();
            }
            byte[] body = (status >= 500 ? "{\"error\":\"boom\"}" : OK_BODY).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private CareerAnalysisAiProviderProperties props(String baseUrl, int maxRetries,
                                                     Duration budget, Duration backoff) {
        CareerAnalysisAiProviderProperties properties = new CareerAnalysisAiProviderProperties();
        properties.getOss().setBaseUrl(baseUrl);
        properties.getOss().setTimeout(Duration.ofSeconds(5));
        properties.getOss().setMaxRetries(maxRetries);
        properties.getOss().setRetryBackoff(backoff);
        properties.getOss().setTotalTimeBudget(budget);
        return properties;
    }

    /** 게이트 ON — permits/acquire-timeout 을 돌려주는 mock 런타임 설정으로 게이트를 만든다(도메인 override 없음). */
    private static GpuPermitGate gateOn(int permits) {
        AiRuntimeSettings settings = mock(AiRuntimeSettings.class);
        when(settings.gpuGateEnabled()).thenReturn(true);
        when(settings.gpuGatePermits()).thenReturn(permits);
        when(settings.gpuGateAcquireTimeout()).thenReturn(Duration.ofSeconds(5));
        when(settings.gpuGateDomainOverride(anyString())).thenReturn(null);
        return new GpuPermitGate(settings);
    }

    @Test
    @DisplayName("게이트 ON(permits=1): 동시 호출 2건이 실제 HTTP 레벨에서 직렬화된다")
    void gateOnSerializesConcurrentHttpCalls() throws Exception {
        String baseUrl = startStub(250, 200, null);
        CareerAnalysisOssClient client = new CareerAnalysisOssClient(
                props(baseUrl, 0, Duration.ZERO, Duration.ZERO), new ObjectMapper(), gateOn(1));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<JsonNode> first = pool.submit(() -> client.requestFitExplain("s", "u"));
            Future<JsonNode> second = pool.submit(() -> client.requestFitExplain("s", "u"));
            assertThat(first.get(10, TimeUnit.SECONDS).path("fitSummary").asText()).isEqualTo("ok");
            assertThat(second.get(10, TimeUnit.SECONDS).path("fitSummary").asText()).isEqualTo("ok");
        } finally {
            pool.shutdownNow();
        }
        assertThat(maxActive.get()).as("permits=1 이면 서버측 동시 in-flight 는 1을 넘을 수 없다").isEqualTo(1);
        assertThat(requestCount.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("게이트 OFF(기본): 동시 호출 2건이 겹쳐 흐른다(기존 무제약 경로)")
    void gateOffAllowsConcurrentOverlap() throws Exception {
        CountDownLatch rendezvous = new CountDownLatch(2);
        String baseUrl = startStub(0, 200, rendezvous);
        CareerAnalysisOssClient client = new CareerAnalysisOssClient(
                props(baseUrl, 0, Duration.ZERO, Duration.ZERO), new ObjectMapper(), GpuPermitGate.disabled());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<JsonNode> first = pool.submit(() -> client.requestFitExplain("s", "u"));
            Future<JsonNode> second = pool.submit(() -> client.requestFitExplain("s", "u"));
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertThat(maxActive.get()).as("게이트 OFF 면 두 요청이 서버에 동시에 존재할 수 있다").isEqualTo(2);
    }

    @Test
    @DisplayName("예산 ON: 5xx 반복 시 재시도가 총 시간예산으로 유계된다(허용 시도 수보다 먼저 중단)")
    void budgetBoundsRetriesEndToEnd() throws Exception {
        String baseUrl = startStub(120, 500, null);
        // 예산 400ms, 시도당 서버 지연 120ms, 최대 6시도(maxRetries=5) — 예산이 먼저 소진돼야 한다
        CareerAnalysisOssClient client = new CareerAnalysisOssClient(
                props(baseUrl, 5, Duration.ofMillis(400), Duration.ofMillis(50)),
                new ObjectMapper(), GpuPermitGate.disabled());

        long startNanos = System.nanoTime();
        assertThatThrownBy(() -> client.requestFitExplain("s", "u"))
                .isInstanceOf(BusinessException.class);
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;

        assertThat(requestCount.get()).as("예산 소진으로 6시도를 다 쓰기 전에 중단").isLessThan(6);
        assertThat(elapsedMs).as("총 소요가 예산+1시도 여유 안(무예산이면 (120+백오프)×6 ≈ 1.5s+)")
                .isLessThan(1200);
    }
}
