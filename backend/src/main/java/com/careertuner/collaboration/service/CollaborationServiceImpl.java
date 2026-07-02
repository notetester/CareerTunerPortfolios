package com.careertuner.collaboration.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
import com.careertuner.collaboration.dto.CollaborationUserResponse;
import com.careertuner.collaboration.dto.ConversationSummaryResponse;
import com.careertuner.collaboration.dto.FriendRequestResponse;
import com.careertuner.collaboration.dto.FriendResponse;
import com.careertuner.collaboration.dto.MessageAttachmentResponse;
import com.careertuner.collaboration.dto.MessagePreviewResponse;
import com.careertuner.collaboration.dto.MessageResponse;
import com.careertuner.collaboration.dto.SendMessageRequest;
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
    private static final int MAX_MESSAGE_LIMIT = 120;
    private static final Set<String> MESSAGE_KINDS = Set.of("CHAT", "NOTE");

    private final CollaborationMapper mapper;
    private final NotificationService notificationService;
    private final FileAssetMapper fileAssetMapper;
    private final FileService fileService;

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
                "친구 요청이 수락되었습니다.", "이제 1:1 채팅과 쪽지를 주고받을 수 있습니다.");
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
                    .build();
            mapper.insertConversation(conversation);
            conversationId = conversation.getId();
            mapper.insertConversationMember(conversationId, userId);
            mapper.insertConversationMember(conversationId, targetUserId);
        }
        Long directConversationId = conversationId;
        return mapper.findConversations(userId).stream()
                .filter(row -> row.getId().equals(directConversationId))
                .findFirst()
                .map(this::toConversationResponse)
                .orElse(new ConversationSummaryResponse(
                        directConversationId,
                        new UserBriefResponse(target.getId(), target.getName(), target.getEmail()),
                        null,
                        0,
                        null));
    }

    @Override
    public List<ConversationSummaryResponse> listConversations(Long userId) {
        return mapper.findConversations(userId).stream()
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
        List<Long> attachmentIds = normalizeAttachmentIds(request.attachmentFileIds());
        if (content.isBlank() && attachmentIds.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "메시지 내용 또는 첨부 파일이 필요합니다.");
        }

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
            mapper.insertMessageAttachment(message.getId(), fileId);
            fileAssetMapper.updateRef(fileId, "COLLAB_MESSAGE", message.getId());
        }

        mapper.touchConversation(conversationId);
        notifyConversationMembers(userId, conversationId, message.getId(), kind, content, attachmentIds.size());
        return toMessageResponse(mapper.findMessageById(message.getId()), userId);
    }

    @Override
    public FileService.Download downloadAttachment(Long userId, Long fileId) {
        if (mapper.countAttachmentAccess(userId, fileId) == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "첨부 파일에 접근할 권한이 없습니다.");
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

    private String normalizeKind(String rawKind) {
        String kind = rawKind == null || rawKind.isBlank()
                ? "CHAT"
                : rawKind.trim().toUpperCase(Locale.ROOT);
        if (!MESSAGE_KINDS.contains(kind)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 메시지 유형입니다.");
        }
        return kind;
    }

    private List<Long> normalizeAttachmentIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream()
                .filter(id -> id != null && id > 0)
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                        set -> set.stream().toList()));
    }

    private void notifyConversationMembers(Long senderId, Long conversationId, Long messageId,
                                           String kind, String content, int attachmentCount) {
        String title = "NOTE".equals(kind) ? "새 쪽지가 도착했습니다." : "새 채팅 메시지가 도착했습니다.";
        String message = content.isBlank()
                ? "첨부 파일 " + attachmentCount + "개"
                : content;
        for (Long memberId : mapper.findConversationMemberIds(conversationId)) {
            if (!memberId.equals(senderId)) {
                notifyUser(memberId, senderId, "DIRECT_MESSAGE", "COLLAB_CONVERSATION", conversationId,
                        title, message);
            }
        }
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
        return new ConversationSummaryResponse(
                row.getId(),
                new UserBriefResponse(row.getPeerUserId(), row.getPeerName(), row.getPeerEmail()),
                latest,
                row.getUnreadCount(),
                row.getUpdatedAt());
    }

    private MessageResponse toMessageResponse(CollaborationMessage message, Long viewerId) {
        List<MessageAttachmentResponse> attachments = mapper.findAttachmentsByMessageId(message.getId()).stream()
                .map(this::toAttachmentResponse)
                .toList();
        return new MessageResponse(
                message.getId(),
                message.getConversationId(),
                new UserBriefResponse(message.getSenderId(), message.getSenderName(), message.getSenderEmail()),
                viewerId.equals(message.getSenderId()),
                message.getKind(),
                message.getContent(),
                attachments,
                message.getCreatedAt());
    }

    private MessageAttachmentResponse toAttachmentResponse(MessageAttachmentRow row) {
        return new MessageAttachmentResponse(
                row.getFileAssetId(),
                row.getOriginalName(),
                row.getContentType(),
                row.getSizeBytes(),
                "/api/collaboration/files/" + row.getFileAssetId() + "/content");
    }
}
