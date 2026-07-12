package com.careertuner.user.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.user.domain.User;
import com.careertuner.user.domain.UserResumeDetail;

/**
 * 계정 확충(로그인 아이디·전화번호) + 이력서 상세 스펙 매퍼.
 *
 * <p>기존 UserMapper 를 건드리지 않고 W6 신규 컬럼/테이블만 담당한다.</p>
 */
@Mapper
public interface UserAccountMapper {

    // ── 계정 정보 ──

    User findById(Long id);

    /** 소셜 연결 변경을 직렬화하기 위해 회원 행을 잠근다. */
    User findByIdForUpdate(Long id);

    /** 연결된 소셜 provider 목록(user_social). */
    List<String> findLinkedProviders(Long userId);

    int countByLoginId(String loginId);

    int countByEmailExcludingUser(@Param("email") String email, @Param("excludeUserId") Long excludeUserId);

    int countByPhone(@Param("phone") String phone, @Param("excludeUserId") Long excludeUserId);

    /** 로그인 아이디는 아직 미설정(NULL)일 때만 최초 설정한다. */
    int setLoginIdIfAbsent(@Param("userId") Long userId, @Param("loginId") String loginId);

    int updatePhone(@Param("userId") Long userId, @Param("phone") String phone);

    void deleteSocial(@Param("userId") Long userId, @Param("provider") String provider);

    /**
     * 회원 행은 FK 보존을 위해 유지하되 로그인 식별자와 공개 식별 정보를 즉시 제거한다.
     * 상태 조건을 함께 검사해 중복 탈퇴와 관리자 상태 변경 경합을 감지한다.
     */
    int anonymizeAndSoftDeleteOwnAccount(Long userId);

    int deleteAllSocialLinks(Long userId);

    int deleteAllPushSubscriptions(Long userId);

    int expireAllEmailVerifications(Long userId);

    int deleteAllSmsOtpCodes(Long userId);

    /** 프로필 현재값·과거 스냅샷·AI 산출물의 개인정보를 행 보존 상태로 제거한다. */
    int scrubUserProfile(Long userId);

    int scrubUserProfileVersions(Long userId);

    int scrubProfileAiAnalyses(Long userId);

    int scrubResumeDetail(Long userId);

    /** 첨삭 행/FK는 보존하되 사용자가 작성한 원문·개선문·AI 결과·운영 메모를 제거한다. */
    int scrubCorrectionRequests(Long userId);

    int hideAndAnonymizeNicknameProfiles(Long userId);

    int anonymizeChatProfiles(Long userId);

    int clearConversationNicknameProfiles(Long userId);

    int deactivateConversationMemberships(Long userId);

    int cancelPendingFriendRequests(Long userId);

    int cancelPendingConversationInvites(Long userId);

    int softDeleteFriendships(Long userId);
    int softDeleteConversationGrants(Long userId);
    int softDeleteConversationInviteAllows(Long userId);
    int softDeleteNotifications(Long userId);
    int softDeleteCommunitySubscriptions(Long userId);
    int softDeleteCommentSubscriptions(Long userId);
    int softDeletePostScraps(Long userId);
    int reconcilePostScrapCountsForUser(Long userId);
    int softDeletePostReactions(Long userId);
    int reconcilePostReactionCountsForUser(Long userId);
    int softDeleteCommentReactions(Long userId);
    int reconcileCommentReactionCountsForUser(Long userId);

    int deleteDesktopPresence(Long userId);

    // ── 이력서 상세 스펙 ──

    UserResumeDetail findResumeDetail(Long userId);

    void upsertResumeDetail(UserResumeDetail detail);
}
