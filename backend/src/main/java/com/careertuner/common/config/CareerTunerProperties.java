package com.careertuner.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * {@code careertuner.*} 설정 바인딩. 모든 값은 application.yaml 에서 ${ENV:기본값} 으로 주입되며
 * 배포 환경에서는 환경변수로 덮어쓴다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "careertuner")
public class CareerTunerProperties {

    private App app = new App();
    private Jwt jwt = new Jwt();
    private Mail mail = new Mail();
    private Oauth oauth = new Oauth();

    @Getter
    @Setter
    public static class App {
        /** 소셜 로그인 완료/이메일 링크가 향하는 프런트엔드 주소 */
        private String frontendUrl = "http://localhost:5173";
        /** Codex Sites 백업 프런트엔드 주소. 비어 있으면 sites 클라이언트를 허용하지 않는다. */
        private String sitesFrontendUrl = "";
        /** OAuth redirect-uri·이메일 링크 조립용 백엔드 주소 */
        private String apiBaseUrl = "http://localhost:8080";
    }

    @Getter
    @Setter
    public static class Jwt {
        private String secret;
        private long accessTokenValiditySeconds = 1800;
        private long refreshTokenValiditySeconds = 1209600;
    }

    @Getter
    @Setter
    public static class Mail {
        private String from = "no-reply@careertuner.local";
        private String senderName = "CareerTuner";
        /** true 이거나 SMTP username 이 비어 있으면 실제 발송 대신 링크를 로그로 출력 */
        private boolean devMode = true;
    }

    @Getter
    @Setter
    public static class Oauth {
        /**
         * 실 OAuth 키가 없는 개발/데모 환경에서 signed state 기반 mock OAuth 콜백을 허용한다.
         * 운영 프로파일은 false 로 덮어써서 실제 제공자 설정 누락을 숨기지 않는다.
         */
        private boolean mockEnabled = true;
        private Provider kakao = new Provider();
        private Provider naver = new Provider();
        private Provider google = new Provider();

        @Getter
        @Setter
        public static class Provider {
            private String clientId;
            private String clientSecret;
            private String redirectUri;

            public boolean isConfigured() {
                return clientId != null && !clientId.isBlank() && !"CHANGEME".equals(clientId);
            }
        }
    }
}
