package com.careertuner.interview.dto;

/**
 * 프런트 WebRTC 연결용 단기 세션 정보.
 * clientSecret(ephemeral key)으로 브라우저가 OpenAI Realtime 에 직접 연결한다.
 * (정식 API 키는 절대 프런트로 내려가지 않는다.)
 */
public record RealtimeSessionResponse(
        String clientSecret,
        Long expiresAt,
        String model,
        String voice,
        String realtimeUrl) {
}
