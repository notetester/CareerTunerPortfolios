package com.careertuner.auth.dto;

/**
 * 현재 환경에서 실제 로그인 흐름을 시작할 수 있는 소셜 OAuth 제공자.
 *
 * <p>운영 키가 설정됐거나, 개발 환경에서 서명된 mock OAuth가 활성화된 경우에만 true다.</p>
 */
public record OAuthProviderAvailabilityResponse(
        boolean google,
        boolean kakao,
        boolean naver
) {
}
