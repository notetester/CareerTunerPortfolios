package com.careertuner.common.security;

/**
 * 인증된 사용자 주체. JWT 액세스 토큰에서 복원되어 SecurityContext 에 담긴다.
 * 컨트롤러에서 {@code @AuthenticationPrincipal AuthUser} 로 주입받는다.
 */
public record AuthUser(Long id, String email, String role) {
}
