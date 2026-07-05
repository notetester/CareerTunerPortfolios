package com.careertuner.sms;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 설정된 SMS 제공자를 선택한다.
 *
 * <p>{@code careertuner.sms.provider} 로 지정한 실 제공자의 키가 모두 채워져 있으면 그 제공자를,
 * 그렇지 않으면 {@link MockSmsProvider} 로 폴백한다. 이 폴백 덕분에 실 키 없이도
 * 발송→코드입력→검증 데모가 완결된다.</p>
 */
@Slf4j
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

    /** 현재 설정에 맞는 활성 제공자를 반환한다. 실 제공자 키가 없으면 Mock. */
    public SmsProvider resolve() {
        String provider = properties.normalizedProvider();
        switch (provider) {
            case "aligo" -> {
                if (properties.getAligo().configured()) {
                    return aligoProvider;
                }
                log.warn("SMS provider=aligo 지정됐지만 키 미설정 → Mock 폴백");
            }
            case "twilio" -> {
                if (properties.getTwilio().configured()) {
                    // Twilio 실 구현 추가 시 여기에서 반환. 현재는 미구현이라 Mock 폴백.
                    log.warn("SMS provider=twilio 는 아직 실 구현이 없어 Mock 으로 발송합니다.");
                } else {
                    log.warn("SMS provider=twilio 지정됐지만 키 미설정 → Mock 폴백");
                }
            }
            case "naver-sens" -> {
                if (properties.getNaverSens().configured()) {
                    log.warn("SMS provider=naver-sens 는 아직 실 구현이 없어 Mock 으로 발송합니다.");
                } else {
                    log.warn("SMS provider=naver-sens 지정됐지만 키 미설정 → Mock 폴백");
                }
            }
            case "mock" -> {
                // 명시적 Mock — 로그 없이 폴백.
            }
            default -> log.warn("알 수 없는 SMS provider='{}' → Mock 폴백", provider);
        }
        return mockProvider;
    }
}
