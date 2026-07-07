package com.careertuner.sms;

/**
 * SMS OTP 발송 제공자. Mock/실 제공자가 이 계약을 구현한다.
 *
 * <p>{@link SmsProviderRouter} 가 설정된 제공자를 골라 {@link #send} 를 호출한다.
 * 실 제공자는 키가 없거나 발송에 실패하면 예외를 던진다.</p>
 */
public interface SmsProvider {

    /** 이 제공자 식별자(mock/twilio/aligo/naver-sens). */
    String name();

    /** 실제 문자 발송이 일어나는 제공자인지 여부. Mock 은 false. */
    boolean supportsRealSending();

    /** OTP 코드를 대상 번호로 발송한다. 실패 시 예외를 던진다. */
    SmsSendResult send(String phone, String code);
}
