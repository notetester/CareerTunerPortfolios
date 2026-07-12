package com.careertuner.auth.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 네이티브 OAuth 제공자 응답을 앱의 PKCE verifier에 잠시 묶어 두는 일회성 handoff. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NativeAuthHandoff {

    private Long id;
    private String provider;
    private String providerUserId;
    private String email;
    private boolean emailVerified;
    private String displayName;
    private String codeHash;
    private String handoffChallenge;
    private LocalDateTime expiredAt;
    private LocalDateTime createdAt;
}
