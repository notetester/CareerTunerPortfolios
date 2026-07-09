package com.careertuner.auth.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.auth.domain.MfaBackupCode;
import com.careertuner.auth.domain.MfaChallenge;
import com.careertuner.auth.domain.MfaPolicy;
import com.careertuner.auth.domain.UserMfaSetting;

@Mapper
public interface MfaMapper {
    UserMfaSetting findSettingByUserId(Long userId);

    void upsertSetting(UserMfaSetting setting);

    void enableSetting(@Param("userId") Long userId);

    void disableSetting(@Param("userId") Long userId);

    void touchSettingUsed(@Param("userId") Long userId);

    void insertChallenge(MfaChallenge challenge);

    MfaChallenge findChallengeByToken(String challengeToken);

    List<MfaChallenge> findPendingPushChallenges(Long userId);

    void markChallengeVerified(@Param("challengeToken") String challengeToken);

    void markChallengeApproved(@Param("challengeToken") String challengeToken,
                               @Param("deviceName") String deviceName);

    void markChallengeDenied(@Param("challengeToken") String challengeToken,
                             @Param("deviceName") String deviceName);

    void expireOldChallenges(@Param("now") LocalDateTime now);

    void deleteBackupCodes(Long userId);

    void insertBackupCode(MfaBackupCode backupCode);

    List<MfaBackupCode> findUnusedBackupCodes(Long userId);

    int countUnusedBackupCodes(Long userId);

    void markBackupCodeUsed(Long id);

    MfaPolicy findPolicy();

    void upsertPolicy(MfaPolicy policy);
}
