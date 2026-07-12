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

    /** mock | twilio | aligo | naver-sens | firebase */
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
    private final Firebase firebase = new Firebase();

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

    /**
     * Firebase Phone Auth 설정.
     *
     * <p>다른 제공자와 달리 발송·검증은 프런트가 Firebase SDK 로 직접 수행하고,
     * 백엔드는 넘어온 ID 토큰만 검증한다. 따라서 두 종류의 값이 있다:
     * <ul>
     *   <li>웹 config(apiKey/authDomain/projectId/appId/messagingSenderId) — 프런트에 노출되는 공개키.
     *       {@code GET /api/auth/phone/config} 로 내려보낸다.</li>
     *   <li>{@code serviceAccountPath} — 백엔드 토큰 검증용 서비스계정 JSON 경로. <b>절대 프런트로 내려보내지 않는다.</b></li>
     * </ul>
     */
    @Getter
    @Setter
    public static class Firebase {
        /** 웹 API 키(공개키). */
        private String apiKey = "";
        private String authDomain = "";
        private String projectId = "";
        private String appId = "";
        private String messagingSenderId = "";
        /** 백엔드 ID 토큰 검증용 서비스계정 JSON 경로(비밀). FCM 서비스계정과 같은 프로젝트면 재사용 가능. */
        private String serviceAccount = "";

        /** 프런트에 웹 인증 흐름을 제공할 수 있는가(공개 config 완비). */
        public boolean webConfigured() {
            return isNotBlank(apiKey) && isNotBlank(authDomain) && isNotBlank(projectId) && isNotBlank(appId);
        }

        /** 백엔드가 ID 토큰을 검증할 수 있는가(서비스계정 경로 설정). */
        public boolean verifierConfigured() {
            return isNotBlank(serviceAccount);
        }
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
