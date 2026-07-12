package com.careertuner.sms;

/**
 * SMS 발송 결과.
 *
 * @param success       발송 성공 여부
 * @param provider      실제 발송을 처리한 제공자 식별자(mock/aligo)
 * @param realSending   실 제공자를 통한 실제 발송이었는지 여부. false 면 Mock(데모)이다.
 * @param providerMessageId 제공자가 반환한 메시지 식별자(없으면 null)
 */
public record SmsSendResult(
        boolean success,
        String provider,
        boolean realSending,
        String providerMessageId) {

    public static SmsSendResult mockSuccess(String provider) {
        return new SmsSendResult(true, provider, false, null);
    }

    public static SmsSendResult realSuccess(String provider, String providerMessageId) {
        return new SmsSendResult(true, provider, true, providerMessageId);
    }
}
