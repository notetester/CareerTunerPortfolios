package com.careertuner.sms;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.stereotype.Component;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Aligo(알리고) 문자 API 실 제공자 스텁.
 *
 * <p>키가 채워져 있을 때만 실제 HTTP 를 호출한다({@link SmsProviderRouter} 가 configured 판정 후 선택).
 * TossPaymentClient 와 동일하게 {@link HttpClient} + {@code tools.jackson} 조합을 쓴다.
 * 다른 제공자(Twilio/NAVER SENS)로 교체하려면 이 클래스를 참고해 같은 계약으로 추가하면 된다.</p>
 */
@Slf4j
@Component
public class AligoSmsProvider implements SmsProvider {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final SmsProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AligoSmsProvider(SmsProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    @Override
    public String name() {
        return "aligo";
    }

    @Override
    public boolean supportsRealSending() {
        return true;
    }

    @Override
    public SmsSendResult send(String phone, String code) {
        SmsProperties.Aligo cfg = properties.getAligo();
        if (!cfg.configured()) {
            // Router 가 configured 를 확인하고 넘기지만, 직접 호출 대비 방어한다.
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Aligo SMS 키가 설정되어 있지 않습니다.");
        }
        String text = "[CareerTuner] 인증번호 " + code + " 를 입력해 주세요.";
        String form = "key=" + enc(cfg.getApiKey())
                + "&user_id=" + enc(cfg.getUserId())
                + "&sender=" + enc(cfg.getSender())
                + "&receiver=" + enc(phone)
                + "&msg=" + enc(text);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(cfg.getBaseUrl() + "/send/"))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                        "SMS 발송에 실패했습니다. (HTTP " + response.statusCode() + ")");
            }
            return parseResult(response.body());
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException ex) {
            log.warn("Aligo SMS I/O 실패", ex);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "SMS 제공자와 통신하지 못했습니다.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "SMS 발송 요청이 중단되었습니다.");
        }
    }

    /** Aligo 는 result_code 가 1(양수)일 때 성공이다. */
    private SmsSendResult parseResult(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            int resultCode = root.path("result_code").asInt(Integer.MIN_VALUE);
            if (resultCode <= 0) {
                String message = root.path("message").asText("SMS 발송에 실패했습니다.");
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, message);
            }
            String msgId = root.path("msg_id").asText(null);
            return SmsSendResult.realSuccess(name(), msgId);
        } catch (JacksonException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "SMS 제공자 응답을 해석하지 못했습니다.");
        }
    }

    private static String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
