package com.careertuner.sms;

import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * 설정된 SMS 제공자를 선택한다.
 *
 * <p>{@code mock}은 개발/데모에서 명시적으로 선택하고, 운영 실발송은 구현된 {@code aligo}만 사용한다.
 * 실 제공자 오타·미구현 provider·키 누락을 Mock 성공으로 위장하지 않고 설정 오류로 중단한다.</p>
 */
@Component
public class SmsProviderRouter {

    private final SmsProperties properties;
    private final MockSmsProvider mockProvider;
    private final AligoSmsProvider aligoProvider;

    public SmsProviderRouter(SmsProperties properties,
                             MockSmsProvider mockProvider,
                             AligoSmsProvider aligoProvider) {
        this.properties = properties;
        this.mockProvider = mockProvider;
        this.aligoProvider = aligoProvider;
    }

    /** 잘못된 운영 설정을 첫 OTP 요청이 아니라 애플리케이션 시작 단계에서 차단한다. */
    @PostConstruct
    void validateConfiguration() {
        configuredProvider();
    }

    /** 현재 설정에 맞는 활성 제공자를 반환한다. 지원하지 않거나 불완전한 실 설정은 fail-fast 한다. */
    public SmsProvider resolve() {
        return configuredProvider();
    }

    private SmsProvider configuredProvider() {
        String provider = properties.normalizedProvider();
        return switch (provider) {
            case "aligo" -> {
                if (properties.getAligo().configured()) {
                    yield aligoProvider;
                }
                throw new IllegalStateException(
                        "SMS_PROVIDER=aligo 이지만 SMS_ALIGO_API_KEY/USER_ID/SENDER 설정이 완전하지 않습니다.");
            }
            case "mock" -> mockProvider;
            case "firebase" -> {
                // Firebase 는 발송·코드검증을 프런트 SDK 가 수행하고 백엔드는 ID 토큰만 검증한다.
                // 백엔드 발송자(SmsProvider)는 쓰지 않으므로 이 경로는 정상 흐름에서 호출되지 않는다.
                // 다만 @PostConstruct 검증은 통과해야 하므로 웹 config 완비만 확인한다.
                if (!properties.getFirebase().webConfigured()) {
                    throw new IllegalStateException(
                            "SMS_PROVIDER=firebase 이지만 SMS_FIREBASE_API_KEY/AUTH_DOMAIN/PROJECT_ID/APP_ID 설정이 완전하지 않습니다.");
                }
                yield mockProvider;
            }
            case "twilio", "naver-sens" -> throw new IllegalStateException(
                    "SMS provider=" + provider + " 는 현재 지원하지 않습니다. mock, aligo 또는 firebase 를 사용하세요.");
            default -> throw new IllegalStateException(
                    "알 수 없는 SMS provider='" + provider + "' 입니다. mock, aligo 또는 firebase 를 사용하세요.");
        };
    }
}
