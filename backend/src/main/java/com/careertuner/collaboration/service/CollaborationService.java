package com.careertuner.collaboration.service;

import java.util.List;

import com.careertuner.collaboration.dto.CollaborationUserResponse;
import com.careertuner.collaboration.dto.ConversationSummaryResponse;
import com.careertuner.collaboration.dto.FriendRequestResponse;
import com.careertuner.collaboration.dto.FriendResponse;
import com.careertuner.collaboration.dto.MessageResponse;
import com.careertuner.collaboration.dto.SendMessageRequest;
import com.careertuner.file.service.FileService;

public interface CollaborationService {

    List<CollaborationUserResponse> searchUsers(Long userId, String keyword, int limit);

    List<FriendResponse> listFriends(Long userId);

    List<FriendRequestResponse> listIncomingRequests(Long userId);

    List<FriendRequestResponse> listOutgoingRequests(Long userId);

    FriendRequestResponse sendFriendRequest(Long userId, Long targetUserId);

    FriendRequestResponse acceptFriendRequest(Long userId, Long requestId);

    FriendRequestResponse declineFriendRequest(Long userId, Long requestId);

    void removeFriend(Long userId, Long friendUserId);

    ConversationSummaryResponse openDirectConversation(Long userId, Long targetUserId);

    List<ConversationSummaryResponse> listConversations(Long userId);

    List<MessageResponse> listMessages(Long userId, Long conversationId, int limit);

    MessageResponse sendMessage(Long userId, Long conversationId, SendMessageRequest request);

    FileService.Download downloadAttachment(Long userId, Long fileId);
}
