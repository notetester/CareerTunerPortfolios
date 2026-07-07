package com.careertuner.collaboration.service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import com.careertuner.collaboration.dto.CollaborationUserResponse;
import com.careertuner.collaboration.dto.ConversationAuditResponse;
import com.careertuner.collaboration.dto.ConversationBanRequest;
import com.careertuner.collaboration.dto.ConversationBanResponse;
import com.careertuner.collaboration.dto.ConversationInviteAllowRequest;
import com.careertuner.collaboration.dto.ConversationMemberDetailResponse;
import com.careertuner.collaboration.dto.ConversationPermissionResponse;
import com.careertuner.collaboration.dto.ConversationPermissionUpdateRequest;
import com.careertuner.collaboration.dto.ConversationSettingsResponse;
import com.careertuner.collaboration.dto.ConversationSettingsUpdateRequest;
import com.careertuner.collaboration.dto.ConversationSummaryResponse;
import com.careertuner.collaboration.dto.CreateConversationRequest;
import com.careertuner.collaboration.dto.FriendRequestResponse;
import com.careertuner.collaboration.dto.FriendResponse;
import com.careertuner.collaboration.dto.InviteMembersRequest;
import com.careertuner.collaboration.dto.JoinConversationRequest;
import com.careertuner.collaboration.dto.MessageAttachmentResponse;
import com.careertuner.collaboration.dto.MessagePreviewResponse;
import com.careertuner.collaboration.dto.MessageResponse;
import com.careertuner.collaboration.dto.SendMessageRequest;
import com.careertuner.collaboration.dto.SharedPostingResponse;
import com.careertuner.collaboration.dto.UserBriefResponse;
import com.careertuner.collaboration.mapper.CollaborationMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.file.domain.FileAsset;
import com.careertuner.file.mapper.FileAssetMapper;
import com.careertuner.file.service.FileService;
import com.careertuner.nickname.dto.DisplayNameQuery;
import com.careertuner.nickname.dto.DisplayNameResponse;
import com.careertuner.nickname.service.NicknameProfileService;
import com.careertuner.notification.domain.Notification;
import com.careertuner.notification.service.NotificationPreferenceService;
import com.careertuner.notification.service.NotificationService;
import com.careertuner.privacy.dto.ConversationBlockResponse;
import com.careertuner.privacy.service.PrivacyPolicyService;
import com.careertuner.privacy.service.PrivacySurfaces;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CollaborationServiceImpl implements CollaborationService {

    private static final int MAX_SEARCH_LIMIT = 30;
    private static final int MAX_ROOM_SEARCH_LIMIT = 50;
    private static final int MAX_MESSAGE_LIMIT = 120;
    private static final int MAX_INVITE_USERS = 50;
    private static final int DEFAULT_TEMPORARY_HOURS = 72;
    private static final int MAX_TEMPORARY_HOURS = 24 * 30;
    private static final Set<String> MESSAGE_KINDS = Set.of("CHAT", "NOTE");
    private static final Set<String> ROOM_TYPES = Set.of("GROUP", "PUBLIC", "PRIVATE");
    private static final Set<String> ATTACHMENT_SHARE_MODES = Set.of("TEMPORARY", "CLOUD", "LOCAL");
    /** 초대 권한 정책 값. */
    private static final Set<String> INVITE_POLICIES =
            Set.of("OWNER_ONLY", "MANAGERS", "SPECIFIC_MEMBERS", "ALL_MEMBERS");
    /** 공개/비공개 전환이 가능한 유형(GROUP·DIRECT 는 전환 불가). */
    private static final Set<String> TOGGLEABLE_TYPES = Set.of("PUBLIC", "PRIVATE");
    private static final int MAX_AUDIT_LIMIT = 50;
    /** 익명 참가자에게 노출하는 방 전용 표시명 기본값(방 전용 닉네임이 없을 때). */
    private static final String ANONYMOUS_LABEL = "익명";
    /** 데스크톱 heartbeat(30초 폴링) 유예 포함 온라인 판정 창 — 이 시간 안에 heartbeat 가 있으면 온라인. */
    private static final int DESKTOP_ONLINE_WINDOW_SECONDS = 90;

    /** 차단 사실을 특정할 수 없는 일반 거부 문구 (silent deny — docs/PERSONAL_BLOCK_POLICY.md §4). */
    private static final String GENERIC_DENY_DM = "지금은 이 사용자와 대화를 시작할 수 없습니다.";
    private static final String BLOCKED_MESSAGE_TOMBSTONE = "차단한 사용자의 메시지입니다.";

    private final CollaborationMapper mapper;
    private final NotificationService notificationService;
    private final NotificationPreferenceService notificationPreferenceService;
    private final FileAssetMapper fileAssetMapper;
    private final FileService fileService;
    private final PasswordEncoder passwordEncoder;
    private final PrivacyPolicyService privacyPolicyService;
    private final NicknameProfileService nicknameProfileService;

    @Override
    public List<CollaborationUserResponse> searchUsers(Long userId, String keyword, int limit) {
        String q = keyword == null ? "" : keyword.trim();
        if (q.length() < 2) {
            return List.of();
        }
        int cappedLimit = Math.max(1, Math.min(limit, MAX_SEARCH_LIMIT));
        return mapper.searchUsers(userId, q, cappedLimit).stream()
                .map(row -> toSearchResult(userId, row))
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 검색 결과의 개인 정책 반영 (docs/PERSONAL_BLOCK_POLICY.md §5 profile.*).
     *  - 내가 차단한 계정: 제외하지 않고 relationStatus=BLOCKED 로 표기(차단 관리에서 검색 필요).
     *  - 상대 정책이 나를 검색에서 숨김(profile.searchMe): 결과에서 조용히 제외.
     *  - 상대 정책이 내 프로필 조회를 차단(profile.viewMe): 이메일 마스킹(공개 프로필 페이지 부재로 현재 유일한 노출 지점).
     */
    private CollaborationUserResponse toSearchResult(Long viewerId, CollaborationUserRow row) {
        String relationStatus = row.getRelationStatus();
        if (PrivacySurfaces.BLOCKED_ACCOUNT.equals(privacyPolicyService.relationOf(viewerId, row.getId()))) {
            relationStatus = "BLOCKED";
        } else if (!privacyPolicyService.allows(row.getId(), viewerId, PrivacySurfaces.PROFILE_SEARCH_ME)) {
            return null; // 상대가 검색 노출을 차단 — 조용히 제외(silent deny)
        }
        String email = privacyPolicyService.allows(row.getId(), viewerId, PrivacySurfaces.PROFILE_VIEW_ME)
                ? row.getEmail()
                : maskEmail(row.getEmail());
        return new CollaborationUserResponse(row.getId(), row.getName(), email, relationStatus);
    }

    /** 프로필 조회 차단 시 이메일 마스킹 — 첫 글자와 도메인만 남긴다. */
    private String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return email;
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return at < 0 ? "***" : "***" + email.substring(at);
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    @Override
    public List<FriendResponse> listFriends(Long userId) {
        return mapper.findFriends(userId).stream()
                .map(this::toFriendResponse)
                .toList();
    }

    @Override
    public List<FriendRequestResponse> listIncomingRequests(Long userId) {
        return mapper.findIncomingRequests(userId).stream()
                .map(this::toRequestResponse)
                .toList();
    }

    @Override
    public List<FriendRequestResponse> listOutgoingRequests(Long userId) {
        return mapper.findOutgoingRequests(userId).stream()
                .map(this::toRequestResponse)
                .toList();
    }

    @Override
    @Transactional
    public FriendRequestResponse sendFriendRequest(Long userId, Long targetUserId) {
        if (userId.equals(targetUserId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "본인에게 친구 요청을 보낼 수 없습니다.");
        }
        CollaborationUserRow target = requireActiveUser(targetUserId);
        // 상대 정책이 내 친구 요청을 차단(friendRequest) — 차단 사실 비특정 일반 문구(silent deny)
        if (!privacyPolicyService.allows(targetUserId, userId, PrivacySurfaces.FRIEND_REQUEST)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "지금은 이 사용자에게 요청을 보낼 수 없습니다.");
        }
        if (mapper.countFriendship(userId, targetUserId) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 친구로 등록된 사용자입니다.");
        }

        FriendRequest incoming = mapper.findPendingRequest(targetUserId, userId);
        if (incoming != null) {
            return acceptFriendRequest(userId, incoming.getId());
        }

        FriendRequest existing = mapper.findPendingRequest(userId, targetUserId);
        if (existing != null) {
            return toRequestResponse(requireRequestRow(existing.getId()));
        }

        FriendRequest request = FriendRequest.builder()
                .requesterId(userId)
                .receiverId(targetUserId)
                .status("PENDING")
                .build();
        mapper.insertFriendRequest(request);
        notifyUser(targetUserId, userId, "FRIEND_REQUEST", "FRIEND_REQUEST", request.getId(),
                "친구 요청이 도착했습니다.", "새 친구 요청을 확인해 주세요.");
        return toRequestResponse(requireRequestRow(request.getId()));
    }

    @Override
    @Transactional
    public FriendRequestResponse acceptFriendRequest(Long userId, Long requestId) {
        FriendRequest request = requireRequest(requestId);
        if (!userId.equals(request.getReceiverId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "받은 친구 요청만 수락할 수 있습니다.");
        }
        if (!"PENDING".equals(request.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 처리된 친구 요청입니다.");
        }
        mapper.updateFriendRequestStatus(requestId, "ACCEPTED");
        mapper.insertFriendship(request.getRequesterId(), request.getReceiverId(), userId);
        mapper.insertFriendship(request.getReceiverId(), request.getRequesterId(), userId);
        notifyUser(request.getRequesterId(), userId, "FRIEND_ACCEPTED", "FRIEND_REQUEST", requestId,
                "친구 요청이 수락되었습니다.", "이제 채팅방과 쪽지를 주고받을 수 있습니다.");
        return toRequestResponse(requireRequestRow(requestId));
    }

    @Override
    @Transactional
    public FriendRequestResponse declineFriendRequest(Long userId, Long requestId) {
        FriendRequest request = requireRequest(requestId);
        if (!userId.equals(request.getReceiverId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "받은 친구 요청만 거절할 수 있습니다.");
        }
        if (!"PENDING".equals(request.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 처리된 친구 요청입니다.");
        }
        mapper.updateFriendRequestStatus(requestId, "DECLINED");
        return toRequestResponse(requireRequestRow(requestId));
    }

    @Override
    @Transactional
    public void removeFriend(Long userId, Long friendUserId) {
        if (mapper.countFriendship(userId, friendUserId) == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "친구 관계를 찾을 수 없습니다.");
        }
        mapper.deleteFriendshipBoth(userId, friendUserId);
    }

    @Override
    @Transactional
    public ConversationSummaryResponse openDirectConversation(Long userId, Long targetUserId) {
        if (userId.equals(targetUserId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "본인과의 대화방은 만들 수 없습니다.");
        }
        CollaborationUserRow target = requireActiveUser(targetUserId);
        if (mapper.countFriendship(userId, targetUserId) == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "친구로 등록된 사용자와만 대화할 수 있습니다.");
        }
        // ① 뷰어가 상대의 dm 을 차단한 경우 — 본인 설정이므로 차단 사실을 명시해도 된다
        if (!privacyPolicyService.allows(userId, targetUserId, PrivacySurfaces.DM)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "차단한 사용자와는 대화를 시작할 수 없습니다.");
        }
        // ② 상대 정책이 뷰어의 dm 을 차단한 경우 — 차단 사실 비특정 일반 문구(silent deny)
        if (!privacyPolicyService.allows(targetUserId, userId, PrivacySurfaces.DM)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, GENERIC_DENY_DM);
        }

        Long low = Math.min(userId, targetUserId);
        Long high = Math.max(userId, targetUserId);
        Long conversationId = mapper.findDirectConversation(low, high);
        if (conversationId == null) {
            CollaborationConversation conversation = CollaborationConversation.builder()
                    .type("DIRECT")
                    .userLowId(low)
                    .userHighId(high)
                    .createdBy(userId)
                    .maxMembers(2)
                    .build();
            mapper.insertConversation(conversation);
            conversationId = conversation.getId();
            mapper.insertConversationMemberWithRole(conversationId, userId, "MEMBER", userId);
            mapper.insertConversationMemberWithRole(conversationId, targetUserId, "MEMBER", userId);
        }
        return requireConversationSummary(userId, conversationId);
    }

    @Override
    @Transactional
    public ConversationSummaryResponse createConversation(Long userId, CreateConversationRequest request) {
        String type = normalizeRoomType(request.type());
        String title = normalizeRequiredText(request.title(), "대화방 이름을 입력해 주세요.");
        String description = trimToNull(request.description());
        String passwordHash = null;
        if ("PRIVATE".equals(type) && hasText(request.password())) {
            passwordHash = passwordEncoder.encode(request.password().trim());
        }

        CollaborationConversation conversation = CollaborationConversation.builder()
                .type(type)
                .title(title)
                .description(description)
                .passwordHash(passwordHash)
                .maxMembers("PUBLIC".equals(type) ? 500 : 100)
                .createdBy(userId)
                .build();
        mapper.insertConversation(conversation);
        mapper.insertConversationMemberWithRole(conversation.getId(), userId, "OWNER", userId);

        for (Long memberId : normalizeUserIds(request.memberUserIds())) {
            if (memberId.equals(userId)) {
                continue;
            }
            requireActiveUser(memberId);
            requireFriendship(userId, memberId);
            // 초대 대상의 개인 정책(invite.*)이 차단이면 그 대상만 조용히 스킵(silent deny — 예외 금지)
            if (!privacyPolicyService.allowsInvite(memberId, userId, conversation.getId(),
                    type, true, false)) {
                continue;
            }
            mapper.insertConversationMemberWithRole(conversation.getId(), memberId, "MEMBER", userId);
            notifyRoomInvite(memberId, userId, conversation.getId(), title);
        }

        return requireConversationSummary(userId, conversation.getId());
    }

    @Override
    @Transactional
    public ConversationSummaryResponse joinConversation(Long userId, Long conversationId, JoinConversationRequest request) {
        CollaborationConversation conversation = requireConversation(conversationId);
        if ("DIRECT".equals(conversation.getType())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "1:1 대화방에는 직접 참가할 수 없습니다.");
        }
        if (mapper.countConversationMember(conversationId, userId) > 0) {
            return requireConversationSummary(userId, conversationId);
        }
        // 재입장불가 강퇴(ban) 명단이면 참가 차단
        if (mapper.countBan(conversationId, userId) > 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "이 채팅방에서 참가가 제한된 사용자입니다.");
        }
        // 익명만 참가 가능한 방인데 실명 참가(직접 join)로 들어오면 거부 — 익명 참가는 초대 수락 경로로만
        if (Boolean.TRUE.equals(conversation.getAnonymousOnly())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "익명 초대를 통해서만 참가할 수 있는 채팅방입니다.");
        }

        boolean invited = mapper.countPendingInvite(conversationId, userId) > 0;
        boolean allowed = switch (conversation.getType()) {
            case "PUBLIC" -> true;
            case "PRIVATE" -> invited || passwordMatches(request == null ? null : request.password(), conversation);
            case "GROUP" -> invited;
            default -> false;
        };
        if (!allowed) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "참가 권한이 없거나 비밀번호가 올바르지 않습니다.");
        }

        mapper.insertConversationMemberWithRole(conversationId, userId, "MEMBER", null);
        if (invited) {
            mapper.acceptInvite(conversationId, userId);
        }
        writeAudit(conversationId, userId, userId, "MEMBER_JOINED", null);
        return requireConversationSummary(userId, conversationId);
    }

    @Override
    @Transactional
    public ConversationSummaryResponse inviteMembers(Long userId, Long conversationId, InviteMembersRequest request) {
        CollaborationConversation conversation = requireConversation(conversationId);
        if ("DIRECT".equals(conversation.getType())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "1:1 대화방에는 멤버를 초대할 수 없습니다.");
        }
        requireConversationMember(userId, conversationId);
        // 초대 권한 정책 검사 — 정책과 호출자 role/권한/허용목록에 따라 초대 가능 여부 판정
        requireInvitePermission(userId, conversation);
        // 익명만 참가 가능한 방은 초대도 익명으로 강제(방 설정과 정합)
        boolean anonymous = Boolean.TRUE.equals(conversation.getAnonymousOnly())
                || Boolean.TRUE.equals(request.anonymous());
        boolean inviterIsCreator = userId.equals(conversation.getCreatedBy());
        for (Long inviteeId : normalizeUserIds(request.userIds())) {
            if (inviteeId.equals(userId)) {
                continue;
            }
            requireActiveUser(inviteeId);
            requireFriendship(userId, inviteeId);
            // 재입장불가 강퇴(ban) 명단은 재초대 불가 — 그 대상만 조용히 스킵
            if (mapper.countBan(conversationId, inviteeId) > 0) {
                continue;
            }
            // 초대 대상의 개인 정책(invite.{TYPE}.{creator|member}.anonymous + 채팅방 차단 파생 규칙)이
            // 차단이면 그 대상만 조용히 스킵(silent deny — 예외 금지)
            if (!privacyPolicyService.allowsInvite(inviteeId, userId, conversationId,
                    conversation.getType(), inviterIsCreator, anonymous)) {
                continue;
            }
            mapper.insertConversationInvite(conversationId, userId, inviteeId, anonymous);
            mapper.insertConversationMemberWithAnonymous(conversationId, inviteeId, "MEMBER", userId, anonymous);
            mapper.acceptInvite(conversationId, inviteeId);
            writeAudit(conversationId, userId, inviteeId,
                    anonymous ? "MEMBER_INVITED_ANONYMOUS" : "MEMBER_INVITED", null);
            notifyRoomInvite(inviteeId, userId, conversationId, conversation.getTitle());
        }
        return requireConversationSummary(userId, conversationId);
    }

    @Override
    public List<ConversationSummaryResponse> listConversations(Long userId) {
        Set<Long> blockedRooms = blockedConversationIds(userId);
        return mapper.findConversations(userId).stream()
                .filter(row -> !blockedRooms.contains(row.getId()))
                .map(this::toConversationResponse)
                .toList();
    }

    @Override
    public List<ConversationSummaryResponse> discoverConversations(Long userId, String keyword, int limit) {
        String q = keyword == null ? "" : keyword.trim();
        int cappedLimit = Math.max(1, Math.min(limit, MAX_ROOM_SEARCH_LIMIT));
        Set<Long> blockedRooms = blockedConversationIds(userId);
        return mapper.findDiscoverableConversations(userId, q, cappedLimit).stream()
                .filter(row -> !blockedRooms.contains(row.getId()))
                .map(this::toConversationResponse)
                .toList();
    }

    /** 뷰어가 차단한 채팅방 id 집합 — 목록/발견에서 숨김(정책 조회 실패는 필터 없음으로 폴백). */
    private Set<Long> blockedConversationIds(Long userId) {
        try {
            return privacyPolicyService.listConversationBlocks(userId).stream()
                    .map(ConversationBlockResponse::conversationId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        } catch (RuntimeException ex) {
            // 차단 목록 조회 실패가 방 목록 자체를 죽이면 안 된다
            return Set.of();
        }
    }

    @Override
    @Transactional
    public List<MessageResponse> listMessages(Long userId, Long conversationId, int limit) {
        requireConversationMember(userId, conversationId);
        int cappedLimit = Math.max(1, Math.min(limit, MAX_MESSAGE_LIMIT));
        List<CollaborationMessage> messages = mapper.findMessages(conversationId, cappedLimit);
        // 뷰어 기준 content.roomMessage 차단 발신자 벌크 판정 → 해당 메시지는 톰스톤 처리
        Set<Long> senderIds = messages.stream()
                .map(CollaborationMessage::getSenderId)
                .collect(Collectors.toSet());
        Set<Long> blockedSenders =
                privacyPolicyService.blockedAuthorsAmong(userId, senderIds, PrivacySurfaces.CONTENT_ROOM_MESSAGE);
        // 노출될(비차단) 메시지의 첨부를 먼저 모아 LOCAL 소유자 presence 를 1쿼리로 벌크 조회한다.
        Map<Long, List<MessageAttachmentRow>> attachmentRowsByMessage = new LinkedHashMap<>();
        for (CollaborationMessage message : messages) {
            if (!blockedSenders.contains(message.getSenderId())) {
                attachmentRowsByMessage.put(message.getId(), mapper.findAttachmentsByMessageId(message.getId()));
            }
        }
        Map<Long, LocalDateTime> presenceByOwner = desktopPresenceOf(attachmentRowsByMessage.values().stream()
                .flatMap(List::stream)
                .toList());
        // 비익명 발신자 표시명을 방 전용 닉네임 프로필/기본 프로필로 벌크 해석(N+1 방지).
        Map<DisplayNameQuery, DisplayNameResponse> resolvedSenders = resolveSenderNames(messages);
        List<MessageResponse> responses = messages.stream()
                .map(message -> blockedSenders.contains(message.getSenderId())
                        ? toBlockedMessageResponse(message, userId, resolvedSenders)
                        : toMessageResponse(message, userId,
                                attachmentRowsByMessage.getOrDefault(message.getId(), List.of()),
                                presenceByOwner, resolvedSenders))
                .toList();
        Long latestId = mapper.findLatestMessageId(conversationId);
        if (latestId != null) {
            mapper.markRead(conversationId, userId, latestId);
        }
        return responses;
    }

    @Override
    @Transactional
    public MessageResponse sendMessage(Long userId, Long conversationId, SendMessageRequest request) {
        requireConversationMember(userId, conversationId);
        String kind = normalizeKind(request.kind());
        String content = request.content() == null ? "" : request.content().trim();
        List<Long> attachmentIds = normalizeIds(request.attachmentFileIds(), 100);
        List<Long> postingIds = normalizeIds(request.sharedApplicationCaseIds(), 20);
        if (content.isBlank() && attachmentIds.isEmpty() && postingIds.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "메시지 내용, 첨부 파일, 공유 공고 중 하나가 필요합니다.");
        }

        // DIRECT 방은 상대(peer) 정책 기준으로 수신 표면 검사 — 그룹 방은 전송 허용(노출 필터가 처리)
        CollaborationConversation conversation = mapper.findConversationById(conversationId);
        if (conversation != null && "DIRECT".equals(conversation.getType())) {
            Long peerId = userId.equals(conversation.getUserLowId())
                    ? conversation.getUserHighId()
                    : conversation.getUserLowId();
            requirePeerAllows(peerId, userId,
                    "NOTE".equals(kind) ? PrivacySurfaces.NOTE : PrivacySurfaces.DM);
            if (!attachmentIds.isEmpty()) {
                requirePeerAllows(peerId, userId, PrivacySurfaces.FILE_SHARE);
            }
            if (!postingIds.isEmpty()) {
                requirePeerAllows(peerId, userId, PrivacySurfaces.POSTING_SHARE);
            }
        }

        String shareMode = normalizeShareMode(request.attachmentShareMode());
        if ("CLOUD".equals(shareMode) && !attachmentIds.isEmpty()) {
            requirePaidPlan(userId);
        }
        LocalDateTime expiresAt = "TEMPORARY".equals(shareMode)
                ? LocalDateTime.now().plusHours(normalizeTemporaryHours(request.temporaryHours()))
                : null;

        CollaborationMessage message = CollaborationMessage.builder()
                .conversationId(conversationId)
                .senderId(userId)
                .kind(kind)
                .content(content)
                .build();
        mapper.insertMessage(message);

        for (Long fileId : attachmentIds) {
            FileAsset asset = fileAssetMapper.findById(fileId);
            if (asset == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "첨부 파일을 찾을 수 없습니다.");
            }
            if (!userId.equals(asset.getOwnerUserId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "본인이 업로드한 파일만 첨부할 수 있습니다.");
            }
            mapper.insertMessageAttachment(message.getId(), fileId, shareMode, expiresAt);
            fileAssetMapper.updateRef(fileId, "COLLAB_MESSAGE", message.getId());
        }

        for (Long applicationCaseId : postingIds) {
            SharedPostingRow posting = mapper.findOwnedApplicationCase(userId, applicationCaseId);
            if (posting == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "공유할 공고를 찾을 수 없습니다.");
            }
            mapper.insertMessagePosting(message.getId(), applicationCaseId);
        }

        mapper.touchConversation(conversationId);
        notifyConversationMembers(userId, conversationId, message.getId(), kind, content,
                attachmentIds.size(), postingIds.size());
        return toMessageResponse(mapper.findMessageById(message.getId()), userId);
    }

    @Override
    @Transactional
    public ConversationSummaryResponse setConversationMuted(Long userId, Long conversationId, boolean muted) {
        requireConversationMember(userId, conversationId);
        mapper.updateConversationMemberMuted(conversationId, userId, muted);
        return requireConversationSummary(userId, conversationId);
    }

    @Override
    public FileService.Download downloadAttachment(Long userId, Long fileId) {
        MessageAttachmentRow attachment = mapper.findAttachmentForDownload(userId, fileId);
        if (attachment == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "첨부 파일에 접근할 권한이 없습니다.");
        }
        String shareMode = attachment.getShareMode() == null ? "TEMPORARY" : attachment.getShareMode();
        if ("TEMPORARY".equals(shareMode)
                && attachment.getExpiresAt() != null
                && attachment.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "공유 기간이 만료된 파일입니다.");
        }
        if ("CLOUD".equals(shareMode) && !isPaidPlan(attachment.getOwnerPlan())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "파일 소유자의 유료 플랜이 비활성화되어 다운로드할 수 없습니다.");
        }
        if ("LOCAL".equals(shareMode)
                && !isDesktopOnline(mapper.findDesktopLastSeenAt(attachment.getOwnerUserId()))) {
            // LOCAL 공유는 소유자 데스크톱이 온라인(heartbeat 90초 이내)일 때만 전송한다
            throw new BusinessException(ErrorCode.CONFLICT,
                    "파일 소유자의 데스크톱이 온라인이 아닙니다 — 온라인이 되면 다운로드할 수 있습니다.");
        }
        return fileService.downloadAfterAccessCheck(fileId);
    }

    @Override
    @Transactional
    public void touchDesktopPresence(Long userId) {
        mapper.upsertDesktopPresence(userId);
    }

    // ══════════════ 방 설정 / 관리자 위임 (W5) ══════════════

    @Override
    public ConversationSettingsResponse getConversationSettings(Long userId, Long conversationId) {
        CollaborationConversation conversation = requireManageableRoom(conversationId);
        RoomAuthority authority = requireSettingsAuthority(userId, conversation);
        return buildSettingsResponse(conversation, authority);
    }

    @Override
    @Transactional
    public ConversationSettingsResponse updateConversationSettings(Long userId, Long conversationId,
                                                                   ConversationSettingsUpdateRequest request) {
        CollaborationConversation conversation = requireManageableRoom(conversationId);
        RoomAuthority authority = requireSettingsAuthority(userId, conversation);
        if (!authority.canEditRoom()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "방 정보를 수정할 권한이 없습니다.");
        }

        CollaborationConversation patch = CollaborationConversation.builder()
                .id(conversationId)
                .title(trimToNull(request.title()))
                .description(request.description() == null ? null : trimToNull(request.description()))
                .notice(request.notice() == null ? null : trimToNull(request.notice()))
                .maxMembers(request.maxMembers())
                .build();

        // 공개/비공개 전환 (PUBLIC↔PRIVATE 만 허용)
        String newType = null;
        if (request.type() != null) {
            newType = request.type().trim().toUpperCase(Locale.ROOT);
            if (!TOGGLEABLE_TYPES.contains(newType) || !TOGGLEABLE_TYPES.contains(conversation.getType())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "공개/비공개 전환만 가능한 채팅방입니다.");
            }
            patch.setType(newType);
        }

        // 초대 정책
        if (request.invitePolicy() != null) {
            String policy = request.invitePolicy().trim().toUpperCase(Locale.ROOT);
            if (!INVITE_POLICIES.contains(policy)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 초대 정책입니다.");
            }
            patch.setInvitePolicy(policy);
        }
        patch.setAllowAnonymous(request.allowAnonymous());
        patch.setAnonymousOnly(request.anonymousOnly());
        // anonymousOnly 를 켜면 allowAnonymous 도 함께 켜 정합을 맞춘다
        if (Boolean.TRUE.equals(request.anonymousOnly())) {
            patch.setAllowAnonymous(Boolean.TRUE);
        }

        mapper.updateConversationSettings(patch);

        // 비밀번호 처리 — 정원/이미지와 달리 별도 권한(canSetPassword) 필요
        applyPasswordAction(userId, conversationId, conversation, request, authority, newType);

        // 방 프로필 사진 (0/음수면 제거)
        if (request.imageFileId() != null) {
            Long imageFileId = request.imageFileId() > 0 ? request.imageFileId() : null;
            if (imageFileId != null) {
                requireOwnedFile(userId, imageFileId);
                fileAssetMapper.updateRef(imageFileId, "COLLAB_ROOM_IMAGE", conversationId);
            }
            mapper.updateConversationImage(conversationId, imageFileId);
        }

        // 공개→비공개 전환인데 비밀번호가 없으면 초대 전용 비공개방으로 남는다(허용).
        writeAudit(conversationId, userId, null, "ROOM_UPDATED", settingsAuditDetail(request, newType));

        CollaborationConversation updated = requireConversation(conversationId);
        return buildSettingsResponse(updated, requireSettingsAuthority(userId, updated));
    }

    @Override
    @Transactional
    public ConversationSettingsResponse updateMemberPermission(Long userId, Long conversationId, Long targetUserId,
                                                               ConversationPermissionUpdateRequest request) {
        CollaborationConversation conversation = requireManageableRoom(conversationId);
        RoomAuthority authority = requireSettingsAuthority(userId, conversation);
        if (!authority.canManageMembers()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "멤버 권한을 관리할 권한이 없습니다.");
        }
        // 위임은 OWNER 만 — MANAGER 는 다른 MANAGER 를 지정/해제할 수 없다(권한 상승 방지)
        if (!authority.owner()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "방 관리자 지정은 개설자만 할 수 있습니다.");
        }
        ConversationMemberDetailRow target = requireActiveMember(conversationId, targetUserId);
        if ("OWNER".equals(target.getRole())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "개설자의 권한은 변경할 수 없습니다.");
        }

        boolean manager = Boolean.TRUE.equals(request.manager());
        if (manager) {
            mapper.updateMemberRole(conversationId, targetUserId, "MANAGER");
            ConversationPermissionRow permission = ConversationPermissionRow.builder()
                    .conversationId(conversationId)
                    .userId(targetUserId)
                    .canKick(Boolean.TRUE.equals(request.canKick()))
                    .canBan(Boolean.TRUE.equals(request.canBan()))
                    .canSetPassword(Boolean.TRUE.equals(request.canSetPassword()))
                    .canInvite(Boolean.TRUE.equals(request.canInvite()))
                    .canEditRoom(Boolean.TRUE.equals(request.canEditRoom()))
                    .canManageMembers(Boolean.TRUE.equals(request.canManageMembers()))
                    .grantedBy(userId)
                    .build();
            mapper.upsertPermission(permission);
            writeAudit(conversationId, userId, targetUserId, "MANAGER_GRANTED", permissionAuditDetail(permission));
        } else {
            mapper.updateMemberRole(conversationId, targetUserId, "MEMBER");
            mapper.deletePermission(conversationId, targetUserId);
            writeAudit(conversationId, userId, targetUserId, "MANAGER_REVOKED", null);
        }
        notifyUser(targetUserId, userId, "ROOM_MENTION", "COLLAB_CONVERSATION", conversationId,
                manager ? "방 관리자로 지정되었습니다." : "방 관리자 권한이 해제되었습니다.",
                conversation.getTitle() == null ? "채팅방" : conversation.getTitle());

        CollaborationConversation refreshed = requireConversation(conversationId);
        return buildSettingsResponse(refreshed, requireSettingsAuthority(userId, refreshed));
    }

    @Override
    @Transactional
    public ConversationSettingsResponse kickMember(Long userId, Long conversationId, Long targetUserId) {
        CollaborationConversation conversation = requireManageableRoom(conversationId);
        RoomAuthority authority = requireSettingsAuthority(userId, conversation);
        if (!authority.canKick()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "멤버를 강퇴할 권한이 없습니다.");
        }
        ConversationMemberDetailRow target = requireModerableTarget(conversationId, userId, targetUserId, authority);
        mapper.updateMemberStatus(conversationId, targetUserId, "REMOVED");
        writeAudit(conversationId, userId, targetUserId, "MEMBER_KICKED", null);
        CollaborationConversation refreshed = requireConversation(conversationId);
        return buildSettingsResponse(refreshed, requireSettingsAuthority(userId, refreshed));
    }

    @Override
    @Transactional
    public ConversationSettingsResponse banMember(Long userId, Long conversationId, Long targetUserId,
                                                  ConversationBanRequest request) {
        CollaborationConversation conversation = requireManageableRoom(conversationId);
        RoomAuthority authority = requireSettingsAuthority(userId, conversation);
        if (!authority.canBan()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "멤버를 차단(밴)할 권한이 없습니다.");
        }
        requireModerableTarget(conversationId, userId, targetUserId, authority);
        String reason = request == null ? null : trimToNull(request.reason());
        mapper.insertBan(conversationId, targetUserId, userId, reason);
        mapper.updateMemberStatus(conversationId, targetUserId, "REMOVED");
        writeAudit(conversationId, userId, targetUserId, "MEMBER_BANNED", reason);
        CollaborationConversation refreshed = requireConversation(conversationId);
        return buildSettingsResponse(refreshed, requireSettingsAuthority(userId, refreshed));
    }

    @Override
    @Transactional
    public ConversationSettingsResponse unbanMember(Long userId, Long conversationId, Long targetUserId) {
        CollaborationConversation conversation = requireManageableRoom(conversationId);
        RoomAuthority authority = requireSettingsAuthority(userId, conversation);
        if (!authority.canBan()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "차단(밴)을 해제할 권한이 없습니다.");
        }
        if (mapper.deleteBan(conversationId, targetUserId) > 0) {
            writeAudit(conversationId, userId, targetUserId, "MEMBER_UNBANNED", null);
        }
        return buildSettingsResponse(conversation, authority);
    }

    @Override
    @Transactional
    public ConversationSettingsResponse setInviteAllowList(Long userId, Long conversationId,
                                                           ConversationInviteAllowRequest request) {
        CollaborationConversation conversation = requireManageableRoom(conversationId);
        RoomAuthority authority = requireSettingsAuthority(userId, conversation);
        if (!authority.canEditRoom()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "초대 허용 목록을 수정할 권한이 없습니다.");
        }
        mapper.deleteInviteAllowAll(conversationId);
        for (Long memberId : normalizeUserIds(request == null ? null : request.userIds())) {
            // 실제 방 멤버만 허용 목록에 넣는다(존재하지 않는 멤버는 조용히 스킵)
            if (mapper.countConversationMember(conversationId, memberId) > 0) {
                mapper.insertInviteAllow(conversationId, memberId, userId);
            }
        }
        writeAudit(conversationId, userId, null, "INVITE_ALLOW_UPDATED", null);
        return buildSettingsResponse(conversation, authority);
    }

    private CollaborationUserRow requireActiveUser(Long userId) {
        CollaborationUserRow user = mapper.findActiveUserById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다.");
        }
        return user;
    }

    private CollaborationConversation requireConversation(Long conversationId) {
        CollaborationConversation conversation = mapper.findConversationById(conversationId);
        if (conversation == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "대화방을 찾을 수 없습니다.");
        }
        return conversation;
    }

    private FriendRequest requireRequest(Long requestId) {
        FriendRequest request = mapper.findRequestById(requestId);
        if (request == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "친구 요청을 찾을 수 없습니다.");
        }
        return request;
    }

    private FriendRequestRow requireRequestRow(Long requestId) {
        FriendRequestRow row = mapper.findRequestRowById(requestId);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "친구 요청을 찾을 수 없습니다.");
        }
        return row;
    }

    private void requireConversationMember(Long userId, Long conversationId) {
        if (mapper.countConversationMember(conversationId, userId) == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "대화방에 접근할 권한이 없습니다.");
        }
    }

    private void requireFriendship(Long userId, Long friendUserId) {
        if (mapper.countFriendship(userId, friendUserId) == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "친구로 등록된 사용자만 초대할 수 있습니다.");
        }
    }

    /** 상대(peer) 정책의 수신 표면 검사 — 차단 사실 비특정 일반 문구로 거부(silent deny). */
    private void requirePeerAllows(Long peerId, Long senderId, String surface) {
        if (!privacyPolicyService.allows(peerId, senderId, surface)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, GENERIC_DENY_DM);
        }
    }

    private void requirePaidPlan(Long userId) {
        CollaborationUserRow user = requireActiveUser(userId);
        if (!isPaidPlan(user.getPlan())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "클라우드 파일 공유는 유료 플랜에서 사용할 수 있습니다.");
        }
    }

    private ConversationSummaryResponse requireConversationSummary(Long userId, Long conversationId) {
        ConversationSummaryRow row = mapper.findConversationSummary(userId, conversationId);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "대화방을 찾을 수 없습니다.");
        }
        return toConversationResponse(row);
    }

    private String normalizeKind(String rawKind) {
        String kind = rawKind == null || rawKind.isBlank()
                ? "CHAT"
                : rawKind.trim().toUpperCase(Locale.ROOT);
        if (!MESSAGE_KINDS.contains(kind)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 메시지 유형입니다.");
        }
        return kind;
    }

    private String normalizeRoomType(String rawType) {
        String type = rawType == null || rawType.isBlank()
                ? "GROUP"
                : rawType.trim().toUpperCase(Locale.ROOT);
        if (!ROOM_TYPES.contains(type)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 대화방 유형입니다.");
        }
        return type;
    }

    private String normalizeShareMode(String rawMode) {
        String mode = rawMode == null || rawMode.isBlank()
                ? "TEMPORARY"
                : rawMode.trim().toUpperCase(Locale.ROOT);
        if (!ATTACHMENT_SHARE_MODES.contains(mode)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 파일 공유 방식입니다.");
        }
        return mode;
    }

    private int normalizeTemporaryHours(Integer rawHours) {
        if (rawHours == null) {
            return DEFAULT_TEMPORARY_HOURS;
        }
        return Math.max(1, Math.min(rawHours, MAX_TEMPORARY_HOURS));
    }

    private List<Long> normalizeUserIds(List<Long> ids) {
        return normalizeIds(ids, MAX_INVITE_USERS);
    }

    private List<Long> normalizeIds(List<Long> ids, int maxCount) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream()
                .filter(id -> id != null && id > 0)
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                        set -> set.stream().limit(maxCount).toList()));
    }

    private String normalizeRequiredText(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, message);
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean passwordMatches(String rawPassword, CollaborationConversation conversation) {
        if (!hasText(rawPassword) || !hasText(conversation.getPasswordHash())) {
            return false;
        }
        return passwordEncoder.matches(rawPassword.trim(), conversation.getPasswordHash());
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private boolean isPaidPlan(String plan) {
        return plan != null && !"FREE".equalsIgnoreCase(plan);
    }

    private void notifyConversationMembers(Long senderId, Long conversationId, Long messageId,
                                           String kind, String content, int attachmentCount, int postingCount) {
        boolean note = "NOTE".equals(kind);
        String type = note ? "NOTE_MESSAGE" : "ROOM_MESSAGE";
        String title = note ? "새 쪽지가 도착했습니다." : "새 채팅 메시지가 도착했습니다.";
        String message = content.isBlank()
                ? attachmentOrPostingPreview(attachmentCount, postingCount)
                : content;
        String haystack = content.toLowerCase(Locale.ROOT);
        for (ConversationMemberRow member : mapper.findConversationMembersForNotify(conversationId)) {
            if (member.getUserId().equals(senderId)) {
                continue;
            }
            if (Boolean.TRUE.equals(member.getMuted())) {
                // 알림 해제한 방 — 본인 이름이나 설정 키워드가 언급된 경우에만 ROOM_MENTION 으로 알린다.
                if (!haystack.isBlank() && isMentioned(haystack, member)) {
                    notifyUser(member.getUserId(), senderId, "ROOM_MENTION", "COLLAB_CONVERSATION", conversationId,
                            "회원님이 언급되었습니다.", message);
                }
                continue;
            }
            notifyUser(member.getUserId(), senderId, type, "COLLAB_CONVERSATION", conversationId,
                    title, message);
        }
    }

    private boolean isMentioned(String haystack, ConversationMemberRow member) {
        String name = member.getName();
        if (name != null && !name.isBlank() && haystack.contains(name.trim().toLowerCase(Locale.ROOT))) {
            return true;
        }
        try {
            for (String keyword : notificationPreferenceService.get(member.getUserId()).keywords()) {
                if (haystack.contains(keyword.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        } catch (RuntimeException ex) {
            // 키워드 조회 실패는 언급 아님으로 처리 — 메시지 전송 흐름을 막지 않는다.
        }
        return false;
    }

    private String attachmentOrPostingPreview(int attachmentCount, int postingCount) {
        if (attachmentCount > 0 && postingCount > 0) {
            return "첨부 파일 " + attachmentCount + "개 · 공고 " + postingCount + "건";
        }
        if (attachmentCount > 0) {
            return "첨부 파일 " + attachmentCount + "개";
        }
        if (postingCount > 0) {
            return "공고 " + postingCount + "건";
        }
        return "";
    }

    private void notifyRoomInvite(Long userId, Long actorId, Long conversationId, String title) {
        notifyUser(userId, actorId, "ROOM_INVITE", "COLLAB_CONVERSATION", conversationId,
                "채팅방 초대가 도착했습니다.", title == null ? "새 채팅방에 초대되었습니다." : title);
    }

    private void notifyUser(Long userId, Long actorId, String type, String targetType,
                            Long targetId, String title, String message) {
        notificationService.notify(Notification.builder()
                .userId(userId)
                .actorId(actorId)
                .type(type)
                .targetType(targetType)
                .targetId(targetId)
                .title(title)
                .message(message)
                .link("/collaboration")
                .build());
    }

    private FriendResponse toFriendResponse(FriendRow row) {
        return new FriendResponse(
                new UserBriefResponse(row.getUserId(), row.getName(), row.getEmail()),
                row.getCreatedAt());
    }

    private FriendRequestResponse toRequestResponse(FriendRequestRow row) {
        return new FriendRequestResponse(
                row.getId(),
                new UserBriefResponse(row.getRequesterId(), row.getRequesterName(), row.getRequesterEmail()),
                new UserBriefResponse(row.getReceiverId(), row.getReceiverName(), row.getReceiverEmail()),
                row.getStatus(),
                row.getCreatedAt(),
                row.getRespondedAt());
    }

    private ConversationSummaryResponse toConversationResponse(ConversationSummaryRow row) {
        MessagePreviewResponse latest = row.getLatestMessageId() == null ? null
                : new MessagePreviewResponse(
                        row.getLatestMessageId(),
                        row.getLatestKind(),
                        row.getLatestContent(),
                        row.getLatestCreatedAt());
        UserBriefResponse peer = row.getPeerUserId() == null ? null
                : new UserBriefResponse(row.getPeerUserId(), row.getPeerName(), row.getPeerEmail());
        String displayName = "DIRECT".equals(row.getType())
                ? (row.getPeerName() == null ? "1:1 대화" : row.getPeerName())
                : row.getTitle();
        // 방 설정 진입 버튼 노출 힌트 — OWNER/MANAGER 면 노출하고, 실제 권한은 시트 조회에서 재검사한다.
        String myRole = row.getMyRole();
        boolean canManageRoom = !"DIRECT".equals(row.getType())
                && ("OWNER".equals(myRole) || "MANAGER".equals(myRole));
        return new ConversationSummaryResponse(
                row.getId(),
                row.getType(),
                row.getTitle(),
                row.getDescription(),
                displayName,
                row.getImageFileId(),
                row.getNotice(),
                Boolean.TRUE.equals(row.getLocked()),
                row.getMemberCount() == null ? 0 : row.getMemberCount(),
                Boolean.TRUE.equals(row.getJoined()),
                Boolean.TRUE.equals(row.getMuted()),
                myRole,
                canManageRoom,
                peer,
                latest,
                row.getUnreadCount(),
                row.getUpdatedAt());
    }

    private MessageResponse toMessageResponse(CollaborationMessage message, Long viewerId) {
        List<MessageAttachmentRow> attachmentRows = mapper.findAttachmentsByMessageId(message.getId());
        return toMessageResponse(message, viewerId, attachmentRows, desktopPresenceOf(attachmentRows),
                resolveSenderNames(List.of(message)));
    }

    private MessageResponse toMessageResponse(CollaborationMessage message, Long viewerId,
                                              List<MessageAttachmentRow> attachmentRows,
                                              Map<Long, LocalDateTime> presenceByOwner,
                                              Map<DisplayNameQuery, DisplayNameResponse> resolvedSenders) {
        List<MessageAttachmentResponse> attachments = attachmentRows.stream()
                .map(row -> toAttachmentResponse(row, presenceByOwner))
                .toList();
        List<SharedPostingResponse> postings = mapper.findPostingsByMessageId(message.getId()).stream()
                .map(this::toPostingResponse)
                .toList();
        return new MessageResponse(
                message.getId(),
                message.getConversationId(),
                senderBrief(message, resolvedSenders),
                viewerId.equals(message.getSenderId()),
                message.getKind(),
                message.getContent(),
                attachments,
                postings,
                message.getCreatedAt(),
                false);
    }

    /**
     * 차단 발신자 메시지 톰스톤 — 본문은 안내 문구로 교체, 첨부/공고는 비운다.
     * (첨부·공고 조회 자체를 생략해 차단 콘텐츠가 응답에 실리지 않게 한다.)
     */
    private MessageResponse toBlockedMessageResponse(CollaborationMessage message, Long viewerId,
                                                     Map<DisplayNameQuery, DisplayNameResponse> resolvedSenders) {
        return new MessageResponse(
                message.getId(),
                message.getConversationId(),
                senderBrief(message, resolvedSenders),
                viewerId.equals(message.getSenderId()),
                message.getKind(),
                BLOCKED_MESSAGE_TOMBSTONE,
                List.of(),
                List.of(),
                message.getCreatedAt(),
                true);
    }

    /**
     * 발신자 표시 정보(UserBriefResponse) 해석.
     *  - 방 익명 참가(cm.anonymous=1): 방 전용 닉네임(room_nickname) → 없으면 "익명".
     *    익명성 보호를 위해 계정 id·이메일은 노출하지 않는다(id=null, email=null).
     *  - 그 외: 방 전용 닉네임 프로필(conversation_member_profile) 우선 → 기본 프로필 → users.name.
     */
    private UserBriefResponse senderBrief(CollaborationMessage message,
                                          Map<DisplayNameQuery, DisplayNameResponse> resolvedSenders) {
        if (Boolean.TRUE.equals(message.getSenderAnonymous())) {
            String label = hasText(message.getSenderRoomNickname())
                    ? message.getSenderRoomNickname() : ANONYMOUS_LABEL;
            return new UserBriefResponse(null, label, null);
        }
        DisplayNameResponse dn = resolvedSenders.get(
                new DisplayNameQuery(message.getSenderId(), message.getSenderNicknameProfileId()));
        String name = dn != null ? dn.displayName() : message.getSenderName();
        return new UserBriefResponse(message.getSenderId(), name, message.getSenderEmail());
    }

    /** 목록의 비익명 발신자 (senderId, roomNicknameProfileId) 를 모아 표시명을 한 번에 해석한다. */
    private Map<DisplayNameQuery, DisplayNameResponse> resolveSenderNames(List<CollaborationMessage> messages) {
        Set<DisplayNameQuery> queries = new HashSet<>();
        for (CollaborationMessage m : messages) {
            if (!Boolean.TRUE.equals(m.getSenderAnonymous())) {
                queries.add(new DisplayNameQuery(m.getSenderId(), m.getSenderNicknameProfileId()));
            }
        }
        return nicknameProfileService.bulkResolveDisplayNames(queries);
    }

    private MessageAttachmentResponse toAttachmentResponse(MessageAttachmentRow row,
                                                           Map<Long, LocalDateTime> presenceByOwner) {
        String shareMode = row.getShareMode() == null ? "TEMPORARY" : row.getShareMode();
        String availability = attachmentAvailability(row);
        // LOCAL 공유일 때만 소유자 데스크톱 온라인 여부를 세팅 — UI 가 상태 기반으로 다운로드를 안내한다
        Boolean ownerDesktopOnline = "LOCAL".equals(shareMode)
                ? isDesktopOnline(presenceByOwner.get(row.getOwnerUserId()))
                : null;
        boolean downloadable = "AVAILABLE".equals(availability)
                || ("LOCAL_ONLY".equals(availability) && Boolean.TRUE.equals(ownerDesktopOnline));
        String downloadUrl = downloadable
                ? "/api/collaboration/files/" + row.getFileAssetId() + "/content"
                : null;
        return new MessageAttachmentResponse(
                row.getFileAssetId(),
                row.getOriginalName(),
                row.getContentType(),
                row.getSizeBytes(),
                shareMode,
                availability,
                row.getExpiresAt(),
                downloadUrl,
                ownerDesktopOnline);
    }

    /** 첨부 목록의 LOCAL 소유자 presence 벌크 조회 — 소유자가 없으면 쿼리 없이 빈 맵. */
    private Map<Long, LocalDateTime> desktopPresenceOf(List<MessageAttachmentRow> rows) {
        Set<Long> ownerIds = rows.stream()
                .filter(row -> "LOCAL".equals(row.getShareMode()))
                .map(MessageAttachmentRow::getOwnerUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ownerIds.isEmpty()) {
            return Map.of();
        }
        return mapper.findDesktopPresenceByUserIds(ownerIds).stream()
                .filter(presence -> presence.getLastSeenAt() != null)
                .collect(Collectors.toMap(DesktopPresenceRow::getUserId, DesktopPresenceRow::getLastSeenAt,
                        (first, second) -> second));
    }

    /** heartbeat 가 판정 창(90초) 이내면 데스크톱 온라인으로 본다. */
    private boolean isDesktopOnline(LocalDateTime lastSeenAt) {
        return lastSeenAt != null
                && lastSeenAt.isAfter(LocalDateTime.now().minusSeconds(DESKTOP_ONLINE_WINDOW_SECONDS));
    }

    private String attachmentAvailability(MessageAttachmentRow row) {
        String shareMode = row.getShareMode() == null ? "TEMPORARY" : row.getShareMode();
        if ("LOCAL".equals(shareMode)) {
            return "LOCAL_ONLY";
        }
        if ("TEMPORARY".equals(shareMode)
                && row.getExpiresAt() != null
                && row.getExpiresAt().isBefore(LocalDateTime.now())) {
            return "EXPIRED";
        }
        if ("CLOUD".equals(shareMode) && !isPaidPlan(row.getOwnerPlan())) {
            return "PLAN_INACTIVE";
        }
        return "AVAILABLE";
    }

    private SharedPostingResponse toPostingResponse(SharedPostingRow row) {
        return new SharedPostingResponse(
                row.getApplicationCaseId(),
                row.getCompanyName(),
                row.getJobTitle(),
                row.getDeadlineDate(),
                row.getSourceType());
    }

    // ══════════════ 방 설정 / 관리자 위임 — 권한·감사·응답 조립 헬퍼 ══════════════

    /** OWNER 는 전권, MANAGER 는 위임 플래그, 그 외는 시트 진입 불가. */
    private record RoomAuthority(boolean owner, boolean canKick, boolean canBan, boolean canSetPassword,
                                 boolean canInvite, boolean canEditRoom, boolean canManageMembers) {
        static RoomAuthority ownerAll() {
            return new RoomAuthority(true, true, true, true, true, true, true);
        }

        boolean canEnterSettings() {
            return owner || canKick || canBan || canSetPassword || canInvite || canEditRoom || canManageMembers;
        }
    }

    private CollaborationConversation requireManageableRoom(Long conversationId) {
        CollaborationConversation conversation = requireConversation(conversationId);
        if ("DIRECT".equals(conversation.getType())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "1:1 대화방은 설정을 관리할 수 없습니다.");
        }
        return conversation;
    }

    /**
     * 방 설정 시트 진입 권한 — OWNER 는 전권, MANAGER 는 위임 플래그 중 하나라도 있어야 진입 가능.
     * OWNER 자동 판정은 role 기준으로만(개설자 createdBy 와 별개로 role='OWNER' 을 신뢰).
     */
    private RoomAuthority requireSettingsAuthority(Long userId, CollaborationConversation conversation) {
        String role = mapper.findMemberRole(conversation.getId(), userId);
        if (role == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "대화방에 접근할 권한이 없습니다.");
        }
        if ("OWNER".equals(role)) {
            return RoomAuthority.ownerAll();
        }
        if ("MANAGER".equals(role)) {
            ConversationPermissionRow permission = mapper.findPermission(conversation.getId(), userId);
            RoomAuthority authority = permission == null
                    ? new RoomAuthority(false, false, false, false, false, false, false)
                    : new RoomAuthority(false,
                            Boolean.TRUE.equals(permission.getCanKick()),
                            Boolean.TRUE.equals(permission.getCanBan()),
                            Boolean.TRUE.equals(permission.getCanSetPassword()),
                            Boolean.TRUE.equals(permission.getCanInvite()),
                            Boolean.TRUE.equals(permission.getCanEditRoom()),
                            Boolean.TRUE.equals(permission.getCanManageMembers()));
            if (!authority.canEnterSettings()) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "방 설정을 열 권한이 없습니다.");
            }
            return authority;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN, "방 설정은 개설자 또는 방 관리자만 열 수 있습니다.");
    }

    /** 초대 권한 정책 집행 — 정책별로 호출자의 role/권한/허용목록을 검사한다. */
    private void requireInvitePermission(Long userId, CollaborationConversation conversation) {
        String policy = conversation.getInvitePolicy() == null ? "ALL_MEMBERS" : conversation.getInvitePolicy();
        String role = mapper.findMemberRole(conversation.getId(), userId);
        boolean owner = "OWNER".equals(role);
        boolean manager = "MANAGER".equals(role);
        boolean allowed = switch (policy) {
            case "OWNER_ONLY" -> owner;
            case "MANAGERS" -> owner || (manager && managerHasInvite(conversation.getId(), userId));
            case "SPECIFIC_MEMBERS" ->
                    owner || mapper.countInviteAllow(conversation.getId(), userId) > 0;
            default -> true; // ALL_MEMBERS
        };
        if (!allowed) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "이 채팅방에 멤버를 초대할 권한이 없습니다.");
        }
    }

    private boolean managerHasInvite(Long conversationId, Long userId) {
        ConversationPermissionRow permission = mapper.findPermission(conversationId, userId);
        return permission != null && Boolean.TRUE.equals(permission.getCanInvite());
    }

    /** 강퇴/밴 대상 검사 — 존재하는 활성 멤버여야 하고, OWNER 는 대상이 될 수 없다. */
    private ConversationMemberDetailRow requireModerableTarget(Long conversationId, Long actorId,
                                                               Long targetUserId, RoomAuthority authority) {
        if (targetUserId.equals(actorId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "자기 자신은 처리할 수 없습니다.");
        }
        ConversationMemberDetailRow target = requireActiveMember(conversationId, targetUserId);
        if ("OWNER".equals(target.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "개설자는 강퇴하거나 차단할 수 없습니다.");
        }
        // MANAGER 대상은 OWNER 만 처리 가능(관리자끼리 상호 강퇴 방지)
        if ("MANAGER".equals(target.getRole()) && !authority.owner()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "다른 방 관리자는 개설자만 처리할 수 있습니다.");
        }
        return target;
    }

    private ConversationMemberDetailRow requireActiveMember(Long conversationId, Long targetUserId) {
        ConversationMemberDetailRow target = mapper.findMemberDetail(conversationId, targetUserId);
        if (target == null || !"ACTIVE".equals(target.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "대화방 멤버를 찾을 수 없습니다.");
        }
        return target;
    }

    private void requireOwnedFile(Long userId, Long fileId) {
        FileAsset asset = fileAssetMapper.findById(fileId);
        if (asset == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "방 프로필 이미지를 찾을 수 없습니다.");
        }
        if (!userId.equals(asset.getOwnerUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "본인이 업로드한 파일만 방 프로필로 지정할 수 있습니다.");
        }
    }

    /** 비밀번호 설정/변경/해제 처리 — canSetPassword 권한 필요, PRIVATE 방에만 유효. */
    private void applyPasswordAction(Long userId, Long conversationId, CollaborationConversation conversation,
                                     ConversationSettingsUpdateRequest request, RoomAuthority authority,
                                     String newType) {
        String action = request.passwordAction() == null
                ? null : request.passwordAction().trim().toUpperCase(Locale.ROOT);
        // 공개→비공개 전환 시 비밀번호를 넘기면 자동 SET 으로 취급한다.
        boolean privateAfter = "PRIVATE".equals(newType)
                || (newType == null && "PRIVATE".equals(conversation.getType()));
        if (action == null && hasText(request.password()) && privateAfter) {
            action = "SET";
        }
        if (action == null) {
            return;
        }
        if (!authority.canSetPassword()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "비밀번호를 변경할 권한이 없습니다.");
        }
        switch (action) {
            case "SET" -> {
                if (!hasText(request.password())) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT, "설정할 비밀번호를 입력해 주세요.");
                }
                mapper.updateConversationPassword(conversationId, passwordEncoder.encode(request.password().trim()));
                writeAudit(conversationId, userId, null, "PASSWORD_SET", null);
            }
            case "CLEAR" -> {
                mapper.updateConversationPassword(conversationId, null);
                writeAudit(conversationId, userId, null, "PASSWORD_CLEARED", null);
            }
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 비밀번호 처리입니다.");
        }
    }

    private ConversationSettingsResponse buildSettingsResponse(CollaborationConversation conversation,
                                                               RoomAuthority authority) {
        Long conversationId = conversation.getId();
        List<ConversationMemberDetailResponse> members = mapper.findMemberDetails(conversationId).stream()
                .map(this::toMemberDetailResponse)
                .toList();
        List<ConversationBanResponse> bans = mapper.findBans(conversationId).stream()
                .map(this::toBanResponse)
                .toList();
        List<Long> inviteAllow = mapper.findInviteAllowUserIds(conversationId);
        List<ConversationAuditResponse> audits = mapper.findAudits(conversationId, MAX_AUDIT_LIMIT).stream()
                .map(this::toAuditResponse)
                .toList();
        return new ConversationSettingsResponse(
                conversationId,
                conversation.getType(),
                conversation.getTitle(),
                conversation.getDescription(),
                conversation.getImageFileId(),
                conversation.getNotice(),
                "PRIVATE".equals(conversation.getType()) || conversation.getPasswordHash() != null,
                conversation.getPasswordHash() != null,
                conversation.getMaxMembers() == null ? 0 : conversation.getMaxMembers(),
                conversation.getInvitePolicy() == null ? "ALL_MEMBERS" : conversation.getInvitePolicy(),
                Boolean.TRUE.equals(conversation.getAllowAnonymous()),
                Boolean.TRUE.equals(conversation.getAnonymousOnly()),
                toPermissionResponse(authority),
                members,
                bans,
                inviteAllow,
                audits);
    }

    private ConversationPermissionResponse toPermissionResponse(RoomAuthority authority) {
        return new ConversationPermissionResponse(
                authority.owner(),
                authority.canKick(),
                authority.canBan(),
                authority.canSetPassword(),
                authority.canInvite(),
                authority.canEditRoom(),
                authority.canManageMembers());
    }

    private ConversationMemberDetailResponse toMemberDetailResponse(ConversationMemberDetailRow row) {
        boolean owner = "OWNER".equals(row.getRole());
        boolean anonymous = Boolean.TRUE.equals(row.getAnonymous());
        // 익명 멤버는 실명·이메일 대신 방 전용 닉네임(없으면 익명 라벨)만 노출한다.
        String displayName = anonymous
                ? (hasText(row.getRoomNickname()) ? row.getRoomNickname() : ANONYMOUS_LABEL)
                : (hasText(row.getName()) ? row.getName() : row.getEmail());
        ConversationPermissionResponse permission = new ConversationPermissionResponse(
                owner,
                owner || Boolean.TRUE.equals(row.getCanKick()),
                owner || Boolean.TRUE.equals(row.getCanBan()),
                owner || Boolean.TRUE.equals(row.getCanSetPassword()),
                owner || Boolean.TRUE.equals(row.getCanInvite()),
                owner || Boolean.TRUE.equals(row.getCanEditRoom()),
                owner || Boolean.TRUE.equals(row.getCanManageMembers()));
        return new ConversationMemberDetailResponse(
                row.getUserId(),
                displayName,
                anonymous ? null : row.getEmail(),
                row.getRole(),
                anonymous,
                row.getRoomProfileFileId(),
                row.getJoinedAt(),
                permission,
                Boolean.TRUE.equals(row.getBanned()));
    }

    private ConversationBanResponse toBanResponse(ConversationBanRow row) {
        return new ConversationBanResponse(
                row.getUserId(),
                hasText(row.getName()) ? row.getName() : row.getEmail(),
                row.getReason(),
                row.getBannedBy(),
                row.getCreatedAt());
    }

    private ConversationAuditResponse toAuditResponse(ConversationAuditRow row) {
        return new ConversationAuditResponse(
                row.getId(),
                row.getActorId(),
                row.getActorName(),
                row.getTargetUserId(),
                row.getTargetName(),
                row.getAction(),
                row.getDetail(),
                row.getCreatedAt());
    }

    /** 방 활동 로그 기록 — best-effort. 로그 실패가 본 작업을 막지 않는다. */
    private void writeAudit(Long conversationId, Long actorId, Long targetUserId, String action, String detail) {
        try {
            mapper.insertAudit(conversationId, actorId, targetUserId, action, detail);
        } catch (RuntimeException ex) {
            // 감사 로그 기록 실패는 무시 — 본 흐름에 영향 주지 않는다.
        }
    }

    private String permissionAuditDetail(ConversationPermissionRow permission) {
        StringBuilder builder = new StringBuilder();
        appendFlag(builder, "강퇴", permission.getCanKick());
        appendFlag(builder, "차단", permission.getCanBan());
        appendFlag(builder, "비밀번호", permission.getCanSetPassword());
        appendFlag(builder, "초대", permission.getCanInvite());
        appendFlag(builder, "방편집", permission.getCanEditRoom());
        appendFlag(builder, "멤버관리", permission.getCanManageMembers());
        return builder.length() == 0 ? "권한 없음" : builder.toString();
    }

    private void appendFlag(StringBuilder builder, String label, Boolean value) {
        if (Boolean.TRUE.equals(value)) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(label);
        }
    }

    private String settingsAuditDetail(ConversationSettingsUpdateRequest request, String newType) {
        StringBuilder builder = new StringBuilder();
        if (request.title() != null) {
            appendChange(builder, "제목");
        }
        if (request.description() != null) {
            appendChange(builder, "설명");
        }
        if (request.notice() != null) {
            appendChange(builder, "공지");
        }
        if (request.imageFileId() != null) {
            appendChange(builder, "프로필 사진");
        }
        if (newType != null) {
            appendChange(builder, "공개 설정(" + newType + ")");
        }
        if (request.maxMembers() != null) {
            appendChange(builder, "정원");
        }
        if (request.invitePolicy() != null) {
            appendChange(builder, "초대 정책");
        }
        if (request.allowAnonymous() != null || request.anonymousOnly() != null) {
            appendChange(builder, "익명 정책");
        }
        return builder.length() == 0 ? null : builder.toString();
    }

    private void appendChange(StringBuilder builder, String label) {
        if (builder.length() > 0) {
            builder.append(", ");
        }
        builder.append(label);
    }
}
