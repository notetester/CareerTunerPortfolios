package com.careertuner.ai.common.gpu;

/**
 * GPU permit 을 {@code acquire-timeout} 안에 얻지 못했을 때 던진다.
 *
 * <p>런타임 예외로 두어 각 도메인 클라이언트의 기존 실패 처리(재시도·폴백·저장된 오류 메시지)가
 * 그대로 이 경우를 흡수하게 한다 — 게이트 도입이 새 실패 유형을 만들지 않는다.
 */
public class GpuPermitTimeoutException extends RuntimeException {

    public GpuPermitTimeoutException(String message) {
        super(message);
    }

    public GpuPermitTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
