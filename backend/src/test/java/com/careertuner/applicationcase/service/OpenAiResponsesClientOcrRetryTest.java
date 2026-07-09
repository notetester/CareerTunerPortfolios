package com.careertuner.applicationcase.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.careertuner.applicationcase.service.OcrPayload;
import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.ObjectMapper;

/**
 * OCR 하드닝 검증 — gpt-4o 가 이미지/PDF 를 때때로 "추출이 지원되지 않는다"류로 짧게 거부하는 비결정성 대응.
 * base-url 을 로컬 HttpServer 로 돌려 실제 HTTP 왕복으로 재시도 동작을 고정한다.
 */
class OpenAiResponsesClientOcrRetryTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    // 100자(OCR_MIN_USEFUL_CHARS) 이상인 정상 추출 결과.
    private static final String LONG_TEXT =
            "채용정보 회사명: 주식회사이액션 모집부문: 백엔드 개발자 자격요건: Java, Spring 우대사항: MySQL, AWS "
            + "근무지: 서울 접수기간: 2026-07-31 까지 담당업무: REST API 설계 및 개발, 데이터 모델링, 운영 지원.";

    private static OpenAiResponsesClient clientFor(HttpServer server) {
        OpenAiProperties props = new OpenAiProperties();
        props.setApiKey("test-key");
        props.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        props.setModel("gpt-4o");
        props.setTimeout(Duration.ofSeconds(5));
        return new OpenAiResponsesClient(props, new ObjectMapper());
    }

    private static void respondOutputText(com.sun.net.httpserver.HttpExchange exchange, String text) throws Exception {
        byte[] body = MAPPER.writeValueAsString(Map.of("output_text", text)).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    @Test
    void extractPdfTextRetriesWhenFirstResponseIsShortRefusal() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger requests = new AtomicInteger();
        server.createContext("/responses", exchange -> {
            int n = requests.incrementAndGet();
            try {
                respondOutputText(exchange, n == 1 ? "PDF 텍스트 추출이 지원되지 않습니다." : LONG_TEXT);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
        server.start();
        try {
            OcrPayload result = clientFor(server).extractPdfText("posting.pdf", new byte[]{1, 2, 3});

            assertThat(requests).as("짧은 거부 응답 후 1회 재시도").hasValue(2);
            assertThat(result.text()).contains("주식회사이액션");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void extractPdfTextDoesNotRetryWhenFirstResponseIsLongEnough() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger requests = new AtomicInteger();
        server.createContext("/responses", exchange -> {
            requests.incrementAndGet();
            try {
                respondOutputText(exchange, LONG_TEXT);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
        server.start();
        try {
            OcrPayload result = clientFor(server).extractPdfText("posting.pdf", new byte[]{1, 2, 3});

            assertThat(requests).as("충분히 긴 결과는 재시도 없음").hasValue(1);
            assertThat(result.text()).contains("주식회사이액션");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void extractImageTextRetriesOnceThenReturnsBlankWhenStillTooShort() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger requests = new AtomicInteger();
        server.createContext("/responses", exchange -> {
            requests.incrementAndGet();
            try {
                respondOutputText(exchange, "추출 불가"); // 항상 짧음(거부)
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
        server.start();
        try {
            OcrPayload result = clientFor(server).extractImageText("image/png", new byte[]{1, 2, 3});

            // 최대 시도까지도 짧으면 빈 문자열 반환 → 상위 ocrFallback 이 다음 단계(워커)로 내려갈 수 있게 한다.
            // 짧은 거부 응답을 그대로 반환하면 워커 폴백을 가로막으므로.
            assertThat(requests).as("짧은 결과는 OCR_MAX_ATTEMPTS 회까지 재시도").hasValue(2);
            assertThat(result.text()).as("최종까지 짧으면 빈 결과 반환").isEmpty();
        } finally {
            server.stop(0);
        }
    }
}
