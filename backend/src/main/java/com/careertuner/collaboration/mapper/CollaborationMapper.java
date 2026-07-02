package com.careertuner.collaboration.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.collaboration.domain.CollaborationConversation;
import com.careertuner.collaboration.domain.CollaborationMessage;
import com.careertuner.collaboration.domain.CollaborationUserRow;
import com.careertuner.collaboration.domain.ConversationSummaryRow;
import com.careertuner.collaboration.domain.FriendRequest;
import com.careertuner.collaboration.domain.FriendRequestRow;
import com.careertuner.collaboration.domain.FriendRow;
import com.careertuner.collaboration.domain.MessageAttachmentRow;

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

    void insertConversation(CollaborationConversation conversation);

    void insertConversationMember(@Param("conversationId") Long conversationId,
                                  @Param("userId") Long userId);

    int countConversationMember(@Param("conversationId") Long conversationId,
                                @Param("userId") Long userId);

    List<Long> findConversationMemberIds(@Param("conversationId") Long conversationId);

    List<ConversationSummaryRow> findConversations(@Param("userId") Long userId);

    void insertMessage(CollaborationMessage message);

    CollaborationMessage findMessageById(@Param("id") Long id);

    List<CollaborationMessage> findMessages(@Param("conversationId") Long conversationId,
                                            @Param("limit") int limit);

    void touchConversation(@Param("conversationId") Long conversationId);

    void insertMessageAttachment(@Param("messageId") Long messageId,
                                 @Param("fileAssetId") Long fileAssetId);

    List<MessageAttachmentRow> findAttachmentsByMessageId(@Param("messageId") Long messageId);

    Long findLatestMessageId(@Param("conversationId") Long conversationId);

    void markRead(@Param("conversationId") Long conversationId,
                  @Param("userId") Long userId,
                  @Param("messageId") Long messageId);

    int countAttachmentAccess(@Param("userId") Long userId,
                              @Param("fileId") Long fileId);
}
