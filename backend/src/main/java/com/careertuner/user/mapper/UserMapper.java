package com.careertuner.user.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.user.domain.User;

@Mapper
public interface UserMapper {

    User findById(Long id);

    User findByEmail(String email);

    User findByLoginIdentifier(String identifier);

    int countByEmail(String email);

    int countByEmailExcludingId(@Param("email") String email, @Param("id") Long id);

    int countByLoginId(String loginId);

    /** id 는 useGeneratedKeys 로 user 객체에 채워진다. */
    void insert(User user);

    void touchLastLogin(Long id);

    void touchLastLoginAndResetFailures(Long id);

    void increaseFailedLogin(Long id);

    void activateExpiredBlock(Long id);

    void lockForFailedLogin(@Param("id") Long id,
                            @Param("blockedUntil") java.time.LocalDateTime blockedUntil,
                            @Param("reason") String reason);

    void releaseDormant(Long id);

    void markEmailVerified(Long id);

    void updateEmailAndMarkVerified(@Param("id") Long id, @Param("email") String email);

    /** 전화번호를 저장하고 인증 완료로 표시한다(SMS OTP 검증 성공 시). */
    void markPhoneVerified(@Param("id") Long id, @Param("phone") String phone);

    void updatePassword(@Param("id") Long id, @Param("password") String password);
}
