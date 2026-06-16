package com.careertuner.interview.media.dto;

import java.util.List;

/**
 * 아바타 화상 면접용 LiveAvatar 단기 세션 토큰 + 면접 질문 목록.
 * 프런트는 sessionToken 으로 @heygen/liveavatar-web-sdk 의 LiveAvatarSession 을 연다.
 * (정식 API 키는 절대 프런트로 내려가지 않는다.)
 */
public record AvatarSessionResponse(
        String sessionId,
        String sessionToken,
        boolean sandbox,
        String language,
        List<String> questions) {
}
