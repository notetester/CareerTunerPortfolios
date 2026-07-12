package com.careertuner.privacy.mapper;

import java.util.List;
import java.util.Set;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.privacy.domain.ContentAuthorRow;
import com.careertuner.privacy.domain.ConversationBlock;
import com.careertuner.privacy.domain.UserBlock;
import com.careertuner.privacy.domain.UserIpBlock;
import com.careertuner.privacy.domain.UserPrivacyPolicy;
import com.careertuner.privacy.domain.UserRoleRow;

@Mapper
public interface PrivacyMapper {

    /* ── 관계 정책 문서 ── */
    UserPrivacyPolicy findPolicy(@Param("userId") Long userId);

    void upsertPolicy(UserPrivacyPolicy policy);

    /* ── 계정 차단 ── */
    List<UserBlock> findBlocksByUser(@Param("userId") Long userId);

    UserBlock findBlock(@Param("userId") Long userId, @Param("blockedUserId") Long blockedUserId);

    UserBlock findBlockById(@Param("id") Long id);

    void insertBlock(UserBlock block);

    void updateBlock(UserBlock block);

    void softDeleteBlock(@Param("id") Long id);

    /** 뷰어가 차단한 계정 중 authorIds 에 포함된 것 — 콘텐츠 목록 필터용 벌크 조회. */
    List<UserBlock> findBlocksAmong(@Param("userId") Long userId, @Param("targetIds") Set<Long> targetIds);

    /* ── 콘텐츠 id → 작성자 조회 (익명 글/댓글 차단용 — 삭제 콘텐츠는 제외) ── */
    ContentAuthorRow findPostAuthor(@Param("postId") Long postId);

    ContentAuthorRow findCommentAuthor(@Param("commentId") Long commentId);

    /* ── IP 차단 ── */
    List<UserIpBlock> findIpBlocksByUser(@Param("userId") Long userId);

    void insertIpBlock(UserIpBlock block);

    void softDeleteIpBlock(@Param("id") Long id, @Param("userId") Long userId);

    void softDeleteIpBlocksBySource(@Param("userId") Long userId, @Param("sourceUserId") Long sourceUserId);

    /** 대상 계정의 최근 성공 로그인 IP 를 해시해 반환(없으면 null). 원본 IP 는 매퍼 밖으로 나가지 않는다. */
    String findLatestLoginIpHash(@Param("userId") Long userId, @Param("salt") String salt);

    /** 뷰어의 IP 차단 목록과 대상 계정의 최근 로그인 IP 해시가 일치하는지. */
    int countIpBlockMatch(@Param("viewerId") Long viewerId, @Param("actorId") Long actorId, @Param("salt") String salt);

    /** authorIds 중 뷰어의 IP 차단에 걸리는 계정 — 벌크. */
    List<Long> findIpBlockedAmong(@Param("viewerId") Long viewerId, @Param("targetIds") Set<Long> targetIds, @Param("salt") String salt);

    /** 이 IP 해시(들)로 최근 로그인한 계정 수 — 목록 표기용("계정 k개 일치"). */
    int countAccountsMatchingIpHash(@Param("ipHash") String ipHash, @Param("salt") String salt);

    /* ── 채팅방 차단 ── */
    List<ConversationBlock> findConversationBlocksByUser(@Param("userId") Long userId);

    ConversationBlock findConversationBlock(@Param("userId") Long userId, @Param("conversationId") Long conversationId);

    ConversationBlock findConversationBlockById(@Param("id") Long id);

    void insertConversationBlock(ConversationBlock block);

    void updateConversationBlock(ConversationBlock block);

    void softDeleteConversationBlock(@Param("id") Long id);

    /** 뷰어가 차단한 방 중 특정 사용자가 활성 멤버인 방의 차단 행들. */
    List<ConversationBlock> findConversationBlocksWhereMember(@Param("userId") Long userId, @Param("memberId") Long memberId);

    /** 뷰어가 차단한 방 중 특정 사용자가 개설자인 방의 차단 행들. */
    List<ConversationBlock> findConversationBlocksWhereCreator(@Param("userId") Long userId, @Param("creatorId") Long creatorId);

    /* ── 관계 판정 보조 ── */
    String findUserRole(@Param("userId") Long userId);

    int countFriendship(@Param("userId") Long userId, @Param("otherUserId") Long otherUserId);

    /** userIds 중 뷰어의 친구인 계정 — 벌크. */
    List<Long> findFriendsAmong(@Param("userId") Long userId, @Param("targetIds") Set<Long> targetIds);

    /** userIds 의 역할(id→role) — 벌크. */
    List<UserRoleRow> findRolesAmong(@Param("targetIds") Set<Long> targetIds);
}
