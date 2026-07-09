package com.careertuner.payment.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
public class TossPaymentClient {

    private final TossPaymentProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    /** Toss 설정과 JSON 매퍼를 받아 승인 API 전용 HTTP 클라이언트를 준비한다. */
    public TossPaymentClient(TossPaymentProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getTimeout())
                .build();
    }

    /** Toss 결제 승인 API를 호출하고 성공 응답의 핵심 필드만 반환한다. */
    public ConfirmedPayment confirm(String paymentKey, String orderId, int amount) {
        if (!properties.configured()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Toss Payments 시크릿 키가 설정되어 있지 않습니다.");
        }
        try {
            String body = objectMapper.writeValueAsString(confirmBody(paymentKey, orderId, amount));
            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getConfirmUrl()))
                    .timeout(properties.getTimeout())
                    .header("Authorization", authorizationHeader())
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", orderId)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return parseConfirmedPayment(response.body());
            }
            throw new BusinessException(ErrorCode.PAYMENT_CONFIRM_FAILED,
                    "Toss 결제 승인에 실패했습니다. " + truncate(errorMessage(response.body()), 300));
        } catch (BusinessException ex) {
            throw ex;
        } catch (HttpTimeoutException ex) {
            throw new BusinessException(ErrorCode.PAYMENT_CONFIRM_FAILED, "Toss 결제 승인 요청 시간이 초과되었습니다.");
        } catch (IOException ex) {
            log.warn("Toss payment confirm I/O failure", ex);
            throw new BusinessException(ErrorCode.PAYMENT_CONFIRM_FAILED, "Toss 결제 승인 API와 통신하지 못했습니다.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.PAYMENT_CONFIRM_FAILED, "Toss 결제 승인 요청이 중단되었습니다.");
        } catch (JacksonException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Toss 결제 승인 요청을 구성하지 못했습니다.");
        }
    }

    /** Toss 승인 API 요청 본문에 필요한 paymentKey, orderId, amount를 구성한다. */
    private Map<String, Object> confirmBody(String paymentKey, String orderId, int amount) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("paymentKey", paymentKey);
        body.put("orderId", orderId);
        body.put("amount", amount);
        return body;
    }

    /** Toss 시크릿 키 뒤에 콜론을 붙인 값을 Base64 인코딩해 Basic 인증 헤더를 만든다. */
    private String authorizationHeader() {
        String token = Base64.getEncoder()
                .encodeToString((properties.getSecretKey() + ":").getBytes(StandardCharsets.UTF_8));
        return "Basic " + token;
    }

    /** Toss 승인 성공 응답 JSON에서 내부 검증에 필요한 필드만 추출한다. */
    private ConfirmedPayment parseConfirmedPayment(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            return new ConfirmedPayment(
                    root.path("paymentKey").asText(""),
                    root.path("orderId").asText(""),
                    root.path("totalAmount").asInt(0),
                    root.path("status").asText(""));
        } catch (JacksonException ex) {
            throw new BusinessException(ErrorCode.PAYMENT_CONFIRM_FAILED, "Toss 결제 승인 응답을 해석하지 못했습니다.");
        }
    }

    /** Toss 오류 응답에서 사용자에게 전달할 수 있는 메시지를 우선순위대로 추출한다. */
    private String errorMessage(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            String message = root.path("message").asText("");
            if (!message.isBlank()) {
                return message;
            }
            String errorMessage = root.path("error").path("message").asText("");
            return errorMessage.isBlank() ? body : errorMessage;
        } catch (JacksonException ex) {
            return body;
        }
    }

    /** 외부 API 오류 메시지가 지나치게 길 때 응답 크기를 제한한다. */
    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    public record ConfirmedPayment(String paymentKey, String orderId, int totalAmount, String status) {
    }
}
