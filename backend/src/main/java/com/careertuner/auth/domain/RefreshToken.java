package com.careertuner.auth.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** refresh_token 테이블 VO. JWT 리프레시 토큰(불투명 UUID)을 DB로 회전/폐기 관리. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken {

    private Long id;
    private Long userId;
    private String token;
    private LocalDateTime expiredAt;
    private boolean revoked;
    private LocalDateTime revokedAt;
    private String ipAddress;
    private String userAgent;
    private LocalDateTime createdAt;
}
