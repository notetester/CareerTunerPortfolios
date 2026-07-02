package com.careertuner.collaboration.service;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.collaboration.domain.CollaborationConversation;
import com.careertuner.collaboration.domain.CollaborationMessage;
import com.careertuner.collaboration.domain.CollaborationUserRow;
import com.careertuner.collaboration.domain.ConversationSummaryRow;
import com.careertuner.collaboration.domain.FriendRequest;
import com.careertuner.collaboration.domain.FriendRequestRow;
import com.careertuner.collaboration.domain.FriendRow;
import com.careertuner.collaboration.domain.MessageAttachmentRow;
import com.careertuner.collaboration.domain.SharedPostingRow;
import com.careertuner.collaboration.dto.CollaborationUserResponse;
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
import com.careertuner.notification.domain.Notification;
import com.careertuner.notification.service.NotificationService;

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

    private final CollaborationMapper mapper;
    private final NotificationService notificationService;
    private final FileAssetMapper fileAssetMapper;
    private final FileService fileService;
    private final PasswordEncoder passwordEncoder;

    @Override
    public List<CollaborationUserResponse> searchUsers(Long userId, String keyword, int limit) {
        String q = keyword == null ? "" : keyword.trim();
        if (q.length() < 2) {
            return List.of();
        }
        int cappedLimit = Math.max(1, Math.min(limit, MAX_SEARCH_LIMIT));
        return mapper.searchUsers(userId, q, cappedLimit).stream()
                .map(row -> new CollaborationUserResponse(
                        row.getId(), row.getName(), row.getEmail(), row.getRelationStatus()))
                .toList();
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
        for (Long inviteeId : normalizeUserIds(request.userIds())) {
            if (inviteeId.equals(userId)) {
                continue;
            }
            requireActiveUser(inviteeId);
            requireFriendship(userId, inviteeId);
            mapper.insertConversationInvite(conversationId, userId, inviteeId);
            mapper.insertConversationMemberWithRole(conversationId, inviteeId, "MEMBER", userId);
            mapper.acceptInvite(conversationId, inviteeId);
            notifyRoomInvite(inviteeId, userId, conversationId, conversation.getTitle());
        }
        return requireConversationSummary(userId, conversationId);
    }

    @Override
    public List<ConversationSummaryResponse> listConversations(Long userId) {
        return mapper.findConversations(userId).stream()
                .map(this::toConversationResponse)
                .toList();
    }

    @Override
    public List<ConversationSummaryResponse> discoverConversations(Long userId, String keyword, int limit) {
        String q = keyword == null ? "" : keyword.trim();
        int cappedLimit = Math.max(1, Math.min(limit, MAX_ROOM_SEARCH_LIMIT));
        return mapper.findDiscoverableConversations(userId, q, cappedLimit).stream()
                .map(this::toConversationResponse)
                .toList();
    }

    @Override
    @Transactional
    public List<MessageResponse> listMessages(Long userId, Long conversationId, int limit) {
        requireConversationMember(userId, conversationId);
        int cappedLimit = Math.max(1, Math.min(limit, MAX_MESSAGE_LIMIT));
        List<MessageResponse> responses = mapper.findMessages(conversationId, cappedLimit).stream()
                .map(message -> toMessageResponse(message, userId))
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
        if ("LOCAL".equals(shareMode)) {
            throw new BusinessException(ErrorCode.CONFLICT, "로컬 파일 공유는 파일 소유자의 데스크톱 세션 연결이 필요합니다.");
        }
        return fileService.downloadAfterAccessCheck(fileId);
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
        String title = "NOTE".equals(kind) ? "새 쪽지가 도착했습니다." : "새 채팅 메시지가 도착했습니다.";
        String message = content.isBlank()
                ? attachmentOrPostingPreview(attachmentCount, postingCount)
                : content;
        for (Long memberId : mapper.findConversationMemberIds(conversationId)) {
            if (!memberId.equals(senderId)) {
                notifyUser(memberId, senderId, "ROOM_MESSAGE", "COLLAB_CONVERSATION", conversationId,
                        title, message);
            }
        }
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
        return new ConversationSummaryResponse(
                row.getId(),
                row.getType(),
                row.getTitle(),
                row.getDescription(),
                displayName,
                Boolean.TRUE.equals(row.getLocked()),
                row.getMemberCount() == null ? 0 : row.getMemberCount(),
                Boolean.TRUE.equals(row.getJoined()),
                peer,
                latest,
                row.getUnreadCount(),
                row.getUpdatedAt());
    }

    private MessageResponse toMessageResponse(CollaborationMessage message, Long viewerId) {
        List<MessageAttachmentResponse> attachments = mapper.findAttachmentsByMessageId(message.getId()).stream()
                .map(this::toAttachmentResponse)
                .toList();
        List<SharedPostingResponse> postings = mapper.findPostingsByMessageId(message.getId()).stream()
                .map(this::toPostingResponse)
                .toList();
        return new MessageResponse(
                message.getId(),
                message.getConversationId(),
                new UserBriefResponse(message.getSenderId(), message.getSenderName(), message.getSenderEmail()),
                viewerId.equals(message.getSenderId()),
                message.getKind(),
                message.getContent(),
                attachments,
                postings,
                message.getCreatedAt());
    }

    private MessageAttachmentResponse toAttachmentResponse(MessageAttachmentRow row) {
        String shareMode = row.getShareMode() == null ? "TEMPORARY" : row.getShareMode();
        String availability = attachmentAvailability(row);
        String downloadUrl = "AVAILABLE".equals(availability)
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
                downloadUrl);
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
}
