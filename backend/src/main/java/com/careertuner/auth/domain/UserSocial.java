package com.careertuner.auth.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** user_social 테이블 VO. 한 유저가 provider별 1개 소셜 계정 연동. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSocial {

    private Long id;
    private Long userId;
    private String provider;          // KAKAO/NAVER/GOOGLE
    private String providerUserId;    // 제공자 고유 ID
    private LocalDateTime linkedAt;
}
