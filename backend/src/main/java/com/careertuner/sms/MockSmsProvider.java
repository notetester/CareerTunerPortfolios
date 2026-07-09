package com.careertuner.sms;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 실 제공자 키가 없을 때 동작하는 Mock 제공자.
 *
 * <p>실제 문자를 보내지 않고 코드를 로그로 남긴 뒤 성공을 반환한다.
 * {@link PhoneVerificationService} 는 Mock 발송일 때만 응답에 {@code devCode} 를 담아
 * 키 없이도 발송→입력→검증 전 과정을 브라우저에서 시연할 수 있게 한다.</p>
 */
@Slf4j
@Component
public class MockSmsProvider implements SmsProvider {

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public boolean supportsRealSending() {
        return false;
    }

    @Override
    public SmsSendResult send(String phone, String code) {
        log.info("[MOCK-SMS] 실 제공자 미설정 → 실제 발송 생략. 대상={}, 코드={} (데모용 devCode 로 프런트에 노출됨)",
                maskPhone(phone), code);
        return SmsSendResult.mockSuccess(name());
    }

    /** 로그에 전체 번호를 남기지 않도록 중간을 가린다. */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        int keepTail = 4;
        int keepHead = 3;
        String head = phone.substring(0, keepHead);
        String tail = phone.substring(phone.length() - keepTail);
        return head + "****" + tail;
    }
}
