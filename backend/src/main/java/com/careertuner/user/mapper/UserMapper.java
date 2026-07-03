package com.careertuner.user.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.user.domain.User;

@Mapper
public interface UserMapper {

    User findById(Long id);

    User findByEmail(String email);

    int countByEmail(String email);

    int countByLoginId(@Param("loginId") String loginId, @Param("excludeUserId") Long excludeUserId);

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

    void updatePassword(@Param("id") Long id, @Param("password") String password);

    void updateAccountBasics(@Param("id") Long id,
                             @Param("loginId") String loginId,
                             @Param("phoneNumber") String phoneNumber);

    void updateEnterpriseAccount(@Param("id") Long id,
                                 @Param("accountType") String accountType,
                                 @Param("enterpriseTrusted") boolean enterpriseTrusted);
}
