package com.careertuner.collaboration.service;

import java.util.List;

import com.careertuner.collaboration.dto.CollaborationUserResponse;
import com.careertuner.collaboration.dto.ChatProfileRequest;
import com.careertuner.collaboration.dto.ChatProfileResponse;
import com.careertuner.collaboration.dto.ConversationMemberActionRequest;
import com.careertuner.collaboration.dto.ConversationMemberResponse;
import com.careertuner.collaboration.dto.ConversationMemberUpdateRequest;
import com.careertuner.collaboration.dto.ConversationSummaryResponse;
import com.careertuner.collaboration.dto.ConversationSettingsRequest;
import com.careertuner.collaboration.dto.ConversationSettingsResponse;
import com.careertuner.collaboration.dto.CreateConversationRequest;
import com.careertuner.collaboration.dto.FriendRequestResponse;
import com.careertuner.collaboration.dto.FriendResponse;
import com.careertuner.collaboration.dto.InviteMembersRequest;
import com.careertuner.collaboration.dto.JoinConversationRequest;
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

    ConversationSummaryResponse createConversation(Long userId, CreateConversationRequest request);

    ConversationSummaryResponse joinConversation(Long userId, Long conversationId, JoinConversationRequest request);

    ConversationSummaryResponse inviteMembers(Long userId, Long conversationId, InviteMembersRequest request);

    List<ConversationSummaryResponse> listConversations(Long userId);

    List<ConversationSummaryResponse> discoverConversations(Long userId, String keyword, int limit);

    List<MessageResponse> listMessages(Long userId, Long conversationId, int limit);

    MessageResponse sendMessage(Long userId, Long conversationId, SendMessageRequest request);

    ConversationSummaryResponse setConversationMuted(Long userId, Long conversationId, boolean muted);

    ConversationSettingsResponse getConversationSettings(Long userId, Long conversationId);

    ConversationSettingsResponse updateConversationSettings(Long userId, Long conversationId,
                                                            ConversationSettingsRequest request);

    List<ConversationMemberResponse> listConversationMembers(Long userId, Long conversationId);

    ConversationMemberResponse updateConversationMember(Long userId, Long conversationId, Long targetUserId,
                                                        ConversationMemberUpdateRequest request);

    void kickConversationMember(Long userId, Long conversationId, Long targetUserId,
                                ConversationMemberActionRequest request);

    List<ChatProfileResponse> listChatProfiles(Long userId);

    ChatProfileResponse createChatProfile(Long userId, ChatProfileRequest request);

    ChatProfileResponse updateChatProfile(Long userId, Long profileId, ChatProfileRequest request);

    void deleteChatProfile(Long userId, Long profileId);

    FileService.Download downloadAttachment(Long userId, Long fileId);

    /** 데스크톱 앱 heartbeat — LOCAL 파일 공유 다운로드 게이트의 presence 를 갱신한다. */
    void touchDesktopPresence(Long userId);
}
