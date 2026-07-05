package com.careertuner.collaboration.service;

import java.util.List;

import com.careertuner.collaboration.dto.CollaborationUserResponse;
import com.careertuner.collaboration.dto.ConversationBanRequest;
import com.careertuner.collaboration.dto.ConversationInviteAllowRequest;
import com.careertuner.collaboration.dto.ConversationPermissionUpdateRequest;
import com.careertuner.collaboration.dto.ConversationSettingsResponse;
import com.careertuner.collaboration.dto.ConversationSettingsUpdateRequest;
import com.careertuner.collaboration.dto.ConversationSummaryResponse;
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

    FileService.Download downloadAttachment(Long userId, Long fileId);

    /** 데스크톱 앱 heartbeat — LOCAL 파일 공유 다운로드 게이트의 presence 를 갱신한다. */
    void touchDesktopPresence(Long userId);

    // ── 방 설정 / 관리자 위임 (W5) ──

    /** 방 설정 시트 조회 — OWNER 및 위임받은 MANAGER 만. */
    ConversationSettingsResponse getConversationSettings(Long userId, Long conversationId);

    /** 방 메타/공개 정책 수정(제목/설명/공지/사진/공개전환/비밀번호/정원/초대정책/익명정책). */
    ConversationSettingsResponse updateConversationSettings(Long userId, Long conversationId,
                                                            ConversationSettingsUpdateRequest request);

    /** 방 관리자 지정/해제 + 세부 권한 위임. */
    ConversationSettingsResponse updateMemberPermission(Long userId, Long conversationId, Long targetUserId,
                                                        ConversationPermissionUpdateRequest request);

    /** 일반 강퇴(재입장 가능) — 멤버 status 만 REMOVED 로. */
    ConversationSettingsResponse kickMember(Long userId, Long conversationId, Long targetUserId);

    /** 재입장불가 강퇴(ban) — 명단 등록 + 멤버 제거. */
    ConversationSettingsResponse banMember(Long userId, Long conversationId, Long targetUserId,
                                           ConversationBanRequest request);

    /** ban 해제. */
    ConversationSettingsResponse unbanMember(Long userId, Long conversationId, Long targetUserId);

    /** SPECIFIC_MEMBERS 초대 허용 멤버 목록 설정(전체 교체). */
    ConversationSettingsResponse setInviteAllowList(Long userId, Long conversationId,
                                                    ConversationInviteAllowRequest request);
}
