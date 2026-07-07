package com.careertuner.collaboration.mapper;

import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.collaboration.domain.CollaborationConversation;
import com.careertuner.collaboration.domain.CollaborationMessage;
import com.careertuner.collaboration.domain.CollaborationUserRow;
import com.careertuner.collaboration.domain.ConversationAuditRow;
import com.careertuner.collaboration.domain.ConversationBanRow;
import com.careertuner.collaboration.domain.ConversationMemberDetailRow;
import com.careertuner.collaboration.domain.ConversationMemberRow;
import com.careertuner.collaboration.domain.ConversationPermissionRow;
import com.careertuner.collaboration.domain.ConversationSummaryRow;
import com.careertuner.collaboration.domain.DesktopPresenceRow;
import com.careertuner.collaboration.domain.FriendRequest;
import com.careertuner.collaboration.domain.FriendRequestRow;
import com.careertuner.collaboration.domain.FriendRow;
import com.careertuner.collaboration.domain.MessageAttachmentRow;
import com.careertuner.collaboration.domain.SharedPostingRow;

@Mapper
public interface CollaborationMapper {

    CollaborationUserRow findActiveUserById(@Param("id") Long id);

    List<CollaborationUserRow> searchUsers(@Param("viewerId") Long viewerId,
                                           @Param("keyword") String keyword,
                                           @Param("limit") int limit);

    int countFriendship(@Param("userId") Long userId, @Param("friendUserId") Long friendUserId);

    List<FriendRow> findFriends(@Param("userId") Long userId);

    void deleteFriendshipBoth(@Param("userId") Long userId, @Param("friendUserId") Long friendUserId);

    FriendRequest findPendingRequest(@Param("requesterId") Long requesterId,
                                     @Param("receiverId") Long receiverId);

    FriendRequest findRequestById(@Param("id") Long id);

    FriendRequestRow findRequestRowById(@Param("id") Long id);

    List<FriendRequestRow> findIncomingRequests(@Param("userId") Long userId);

    List<FriendRequestRow> findOutgoingRequests(@Param("userId") Long userId);

    void insertFriendRequest(FriendRequest request);

    void updateFriendRequestStatus(@Param("id") Long id, @Param("status") String status);

    void insertFriendship(@Param("userId") Long userId,
                          @Param("friendUserId") Long friendUserId,
                          @Param("createdBy") Long createdBy);

    Long findDirectConversation(@Param("userLowId") Long userLowId,
                                @Param("userHighId") Long userHighId);

    CollaborationConversation findConversationById(@Param("id") Long id);

    void insertConversation(CollaborationConversation conversation);

    void insertConversationMember(@Param("conversationId") Long conversationId,
                                  @Param("userId") Long userId);

    void insertConversationMemberWithRole(@Param("conversationId") Long conversationId,
                                          @Param("userId") Long userId,
                                          @Param("role") String role,
                                          @Param("invitedBy") Long invitedBy);

    /** 익명/방 전용 프로필까지 지정해 멤버를 추가한다(익명 초대 수락 흐름). */
    void insertConversationMemberWithAnonymous(@Param("conversationId") Long conversationId,
                                               @Param("userId") Long userId,
                                               @Param("role") String role,
                                               @Param("invitedBy") Long invitedBy,
                                               @Param("anonymous") boolean anonymous);

    void insertConversationInvite(@Param("conversationId") Long conversationId,
                                  @Param("inviterId") Long inviterId,
                                  @Param("inviteeId") Long inviteeId,
                                  @Param("anonymous") boolean anonymous);

    int countPendingInvite(@Param("conversationId") Long conversationId,
                           @Param("inviteeId") Long inviteeId);

    void acceptInvite(@Param("conversationId") Long conversationId,
                      @Param("inviteeId") Long inviteeId);

    int countConversationMember(@Param("conversationId") Long conversationId,
                                @Param("userId") Long userId);

    List<Long> findConversationMemberIds(@Param("conversationId") Long conversationId);

    List<ConversationMemberRow> findConversationMembersForNotify(@Param("conversationId") Long conversationId);

    int updateConversationMemberMuted(@Param("conversationId") Long conversationId,
                                      @Param("userId") Long userId,
                                      @Param("muted") boolean muted);

    List<ConversationSummaryRow> findConversations(@Param("userId") Long userId);

    ConversationSummaryRow findConversationSummary(@Param("userId") Long userId,
                                                  @Param("conversationId") Long conversationId);

    List<ConversationSummaryRow> findDiscoverableConversations(@Param("userId") Long userId,
                                                              @Param("keyword") String keyword,
                                                              @Param("limit") int limit);

    void insertMessage(CollaborationMessage message);

    CollaborationMessage findMessageById(@Param("id") Long id);

    List<CollaborationMessage> findMessages(@Param("conversationId") Long conversationId,
                                            @Param("limit") int limit);

    void touchConversation(@Param("conversationId") Long conversationId);

    void insertMessageAttachment(@Param("messageId") Long messageId,
                                 @Param("fileAssetId") Long fileAssetId,
                                 @Param("shareMode") String shareMode,
                                 @Param("expiresAt") java.time.LocalDateTime expiresAt);

    List<MessageAttachmentRow> findAttachmentsByMessageId(@Param("messageId") Long messageId);

    MessageAttachmentRow findAttachmentForDownload(@Param("userId") Long userId,
                                                   @Param("fileId") Long fileId);

    SharedPostingRow findOwnedApplicationCase(@Param("userId") Long userId,
                                             @Param("applicationCaseId") Long applicationCaseId);

    void insertMessagePosting(@Param("messageId") Long messageId,
                              @Param("applicationCaseId") Long applicationCaseId);

    List<SharedPostingRow> findPostingsByMessageId(@Param("messageId") Long messageId);

    Long findLatestMessageId(@Param("conversationId") Long conversationId);

    void markRead(@Param("conversationId") Long conversationId,
                  @Param("userId") Long userId,
                  @Param("messageId") Long messageId);

    int countAttachmentAccess(@Param("userId") Long userId,
                              @Param("fileId") Long fileId);

    // ── 데스크톱 presence (LOCAL 파일 공유 게이트) ──

    /** 데스크톱 앱 heartbeat upsert — 폴링 틱마다 last_seen_at 을 갱신한다. */
    void upsertDesktopPresence(@Param("userId") Long userId);

    /** 단일 사용자의 마지막 heartbeat 시각 (없으면 null). */
    java.time.LocalDateTime findDesktopLastSeenAt(@Param("userId") Long userId);

    /** 첨부 목록 표시용 — 소유자 여러 명의 presence 를 1쿼리로 벌크 조회한다. */
    List<DesktopPresenceRow> findDesktopPresenceByUserIds(@Param("userIds") Collection<Long> userIds);

    // ── 방 설정 / 관리자 위임 (W5) ──

    /** 특정 멤버의 role (없으면 null). */
    String findMemberRole(@Param("conversationId") Long conversationId, @Param("userId") Long userId);

    /** 방 메타/공개 정책 수정. null 이 아닌 필드만 반영하는 동적 업데이트. */
    int updateConversationSettings(CollaborationConversation conversation);

    /** 방 프로필 사진 file id 갱신(제거 시 null). */
    void updateConversationImage(@Param("conversationId") Long conversationId,
                                 @Param("imageFileId") Long imageFileId);

    /** 방 비밀번호 해시 설정/해제. */
    void updateConversationPassword(@Param("conversationId") Long conversationId,
                                    @Param("passwordHash") String passwordHash);

    /** 멤버 role 승격/강등. */
    int updateMemberRole(@Param("conversationId") Long conversationId,
                         @Param("userId") Long userId,
                         @Param("role") String role);

    /** 멤버 상태 변경(강퇴=REMOVED, 재입장 가능). */
    int updateMemberStatus(@Param("conversationId") Long conversationId,
                           @Param("userId") Long userId,
                           @Param("status") String status);

    // 세부 권한
    ConversationPermissionRow findPermission(@Param("conversationId") Long conversationId,
                                             @Param("userId") Long userId);

    void upsertPermission(ConversationPermissionRow permission);

    void deletePermission(@Param("conversationId") Long conversationId,
                          @Param("userId") Long userId);

    // 멤버 상세 목록 (role·권한·ban 노출)
    List<ConversationMemberDetailRow> findMemberDetails(@Param("conversationId") Long conversationId);

    ConversationMemberDetailRow findMemberDetail(@Param("conversationId") Long conversationId,
                                                 @Param("userId") Long userId);

    // ban
    int countBan(@Param("conversationId") Long conversationId, @Param("userId") Long userId);

    void insertBan(@Param("conversationId") Long conversationId,
                   @Param("userId") Long userId,
                   @Param("bannedBy") Long bannedBy,
                   @Param("reason") String reason);

    int deleteBan(@Param("conversationId") Long conversationId, @Param("userId") Long userId);

    List<ConversationBanRow> findBans(@Param("conversationId") Long conversationId);

    // SPECIFIC_MEMBERS 초대 허용 목록
    int countInviteAllow(@Param("conversationId") Long conversationId, @Param("userId") Long userId);

    List<Long> findInviteAllowUserIds(@Param("conversationId") Long conversationId);

    void deleteInviteAllowAll(@Param("conversationId") Long conversationId);

    void insertInviteAllow(@Param("conversationId") Long conversationId,
                           @Param("userId") Long userId,
                           @Param("grantedBy") Long grantedBy);

    // 방 활동 로그
    void insertAudit(@Param("conversationId") Long conversationId,
                     @Param("actorId") Long actorId,
                     @Param("targetUserId") Long targetUserId,
                     @Param("action") String action,
                     @Param("detail") String detail);

    List<ConversationAuditRow> findAudits(@Param("conversationId") Long conversationId,
                                          @Param("limit") int limit);

    // ── 관리자 오버사이트 ──

    /** 관리자 방 목록 — 그룹/공개/비공개 방을 최근 활동순으로(멤버수·밴수 포함). */
    List<ConversationSummaryRow> findRoomsForAdmin(@Param("keyword") String keyword,
                                                   @Param("limit") int limit);
}
