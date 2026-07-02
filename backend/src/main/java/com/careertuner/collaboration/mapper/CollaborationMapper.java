package com.careertuner.collaboration.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.collaboration.domain.CollaborationConversation;
import com.careertuner.collaboration.domain.CollaborationMessage;
import com.careertuner.collaboration.domain.CollaborationUserRow;
import com.careertuner.collaboration.domain.ConversationMemberRow;
import com.careertuner.collaboration.domain.ConversationSummaryRow;
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

    void insertConversationInvite(@Param("conversationId") Long conversationId,
                                  @Param("inviterId") Long inviterId,
                                  @Param("inviteeId") Long inviteeId);

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
}
