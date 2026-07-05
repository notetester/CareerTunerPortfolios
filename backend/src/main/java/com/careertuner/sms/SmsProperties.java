package com.careertuner.sms;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * SMS OTP 인증 설정. {@code careertuner.sms} 접두사로 바인딩된다.
 *
 * <p>{@code provider} 기본값은 {@code mock} 이며 env {@code SMS_PROVIDER} 로 교체한다.
 * 선택한 실 제공자의 키가 채워져 있으면 실 발송, 비어 있으면 Mock 제공자로 폴백한다
 * ({@link com.careertuner.sms.SmsProviderRouter} 가 판정). Toss 결제 설정의
 * {@code configured()} 토글 패턴을 그대로 따른다.</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "careertuner.sms")
public class SmsProperties {

    /** mock | twilio | aligo | naver-sens */
    private String provider = "mock";

    /** OTP 코드 유효 시간(초). */
    private int otpValiditySeconds = 300;

    /** 코드당 허용 최대 검증 시도 횟수. */
    private int maxAttempts = 5;

    /** 재발송 쿨다운(초). 직전 발송 후 이 시간 동안 재요청을 막는다. */
    private int cooldownSeconds = 60;

    /** 발신번호(제공자에 따라 사전 등록된 번호여야 함). */
    private String senderPhone = "";

    private final Twilio twilio = new Twilio();
    private final Aligo aligo = new Aligo();
    private final NaverSens naverSens = new NaverSens();

    /** 요청된 provider 값을 정규화한다(대소문자·언더스코어 허용). */
    public String normalizedProvider() {
        if (provider == null) {
            return "mock";
        }
        return provider.trim().toLowerCase().replace('_', '-');
    }

    @Getter
    @Setter
    public static class Twilio {
        private String accountSid = "";
        private String authToken = "";
        /** Messaging Service SID 또는 발신번호. */
        private String from = "";
        private String baseUrl = "https://api.twilio.com/2010-04-01";

        public boolean configured() {
            return isNotBlank(accountSid) && isNotBlank(authToken) && isNotBlank(from);
        }
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

    @Getter
    @Setter
    public static class NaverSens {
        private String accessKey = "";
        private String secretKey = "";
        private String serviceId = "";
        private String from = "";
        private String baseUrl = "https://sens.apigw.ntruss.com";

        public boolean configured() {
            return isNotBlank(accessKey) && isNotBlank(secretKey)
                    && isNotBlank(serviceId) && isNotBlank(from);
        }
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
