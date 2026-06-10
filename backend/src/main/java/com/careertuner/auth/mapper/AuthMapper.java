package com.careertuner.auth.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.auth.domain.EmailVerification;
import com.careertuner.auth.domain.RefreshToken;
import com.careertuner.auth.domain.UserLoginHistory;
import com.careertuner.auth.domain.UserSocial;

@Mapper
public interface AuthMapper {

    // ── 소셜 연동 ──
    UserSocial findSocial(@Param("provider") String provider, @Param("providerUserId") String providerUserId);

    void insertSocial(UserSocial social);

    // ── 이메일 인증 토큰 ──
    void insertEmailVerification(EmailVerification verification);

    EmailVerification findEmailVerificationByToken(String token);

    void markEmailVerificationUsed(Long id);

    // ── 리프레시 토큰 ──
    void insertRefreshToken(RefreshToken refreshToken);

    RefreshToken findRefreshToken(String token);

    void revokeRefreshToken(String token);

    void revokeAllForUser(Long userId);

    void insertLoginHistory(UserLoginHistory history);
}
