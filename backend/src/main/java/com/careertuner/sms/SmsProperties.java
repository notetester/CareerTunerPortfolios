package com.careertuner.sms;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * SMS OTP 인증 설정. {@code careertuner.sms} 접두사로 바인딩된다.
 *
 * <p>{@code provider} 기본값은 {@code mock} 이며 env {@code SMS_PROVIDER} 로 교체한다.
 * 개발/데모는 {@code mock}, 운영 실발송은 구현된 {@code aligo}를 선택한다.
 * 잘못된 실 제공자 설정은 {@link com.careertuner.sms.SmsProviderRouter}가 거부한다.</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "careertuner.sms")
public class SmsProperties {

    /** 지원 목록: mock | aligo */
    private String provider = "mock";

    /** OTP 코드 유효 시간(초). */
    private int otpValiditySeconds = 300;

    /** 코드당 허용 최대 검증 시도 횟수. */
    private int maxAttempts = 5;

    /** 재발송 쿨다운(초). 직전 발송 후 이 시간 동안 재요청을 막는다. */
    private int cooldownSeconds = 60;

    /** 발신번호(제공자에 따라 사전 등록된 번호여야 함). */
    private String senderPhone = "";

    private final Aligo aligo = new Aligo();

    /** 요청된 provider 값을 정규화한다(대소문자·언더스코어 허용). */
    public String normalizedProvider() {
        if (provider == null) {
            return "mock";
        }
        return provider.trim().toLowerCase().replace('_', '-');
    }

    @Getter
    @Setter
    public static class Aligo {
        private String apiKey = "";
        private String userId = "";
        private String sender = "";
        private String baseUrl = "https://apis.aligo.in";

        public boolean configured() {
            return isNotBlank(apiKey) && isNotBlank(userId) && isNotBlank(sender);
        }
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
