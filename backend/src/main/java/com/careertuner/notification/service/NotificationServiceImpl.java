package com.careertuner.notification.service;

import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.notification.domain.Notification;
import com.careertuner.notification.dto.NotificationPageResponse;
import com.careertuner.notification.dto.NotificationResponse;
import com.careertuner.notification.event.NotificationPushEvent;
import com.careertuner.notification.mapper.NotificationMapper;
import com.careertuner.notification.push.NotificationCategories;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final SenderRelationResolver senderRelationResolver;

    @Override
    @Transactional
    public void notify(Notification notification) {
        // 관계 기반 알림(댓글·답글·쪽지·채팅 등)은 생성 시점의 발신자 관계를 기록해
        // 푸시 필터(PushDispatcher)와 클라이언트 토스트 필터가 같은 값을 쓴다.
        if (notification.getSenderRelation() == null
                && notification.getActorId() != null
                && NotificationCategories.RELATION_AWARE_TYPES.contains(notification.getType())) {
            notification.setSenderRelation(
                    senderRelationResolver.resolve(notification.getUserId(), notification.getActorId()));
        }
        notificationMapper.insert(notification);
        // 푸시는 알림 트랜잭션 밖에서 비동기로 발송(AFTER_COMMIT) — 커밋 지연·유령 푸시 방지.
        eventPublisher.publishEvent(new NotificationPushEvent(notification));
    }

    @Override
    public NotificationPageResponse getNotifications(Long userId, int page, int size) {
        int offset = page * size;
        List<Notification> list = notificationMapper.findByUserId(userId, offset, size);
        int total = notificationMapper.countByUserId(userId);
        List<NotificationResponse> responses = list.stream().map(this::toResponse).toList();
        return new NotificationPageResponse(responses, total, page, size, offset + size < total);
    }

    @Override
    public int getUnreadCount(Long userId) {
        return notificationMapper.countUnreadByUserId(userId);
    }

    @Override
    @Transactional
    public void markAsRead(Long notificationId, Long userId) {
        Notification notification = notificationMapper.findById(notificationId);
        if (notification == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "알림을 찾을 수 없습니다.");
        }
        if (!notification.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "본인의 알림만 읽음 처리할 수 있습니다.");
        }
        notificationMapper.markAsRead(notificationId);
    }

    @Override
    @Transactional
    public void markAllAsRead(Long userId) {
        notificationMapper.markAllAsRead(userId);
    }

    private NotificationResponse toResponse(Notification n) {
        NotificationResponse.ActorDto actor = null;
        if (n.getActorId() != null) {
            actor = new NotificationResponse.ActorDto(
                    n.getActorId(),
                    n.getActorName(),
                    n.getActorAvatarUrl()
            );
        }
        return new NotificationResponse(
                n.getId(),
                n.getType(),
                n.getTargetType(),
                n.getTargetId(),
                n.getSenderRelation(),
                n.getTitle(),
                n.getMessage(),
                n.getLink(),
                n.isRead(),
                n.getCreatedAt(),
                actor
        );
    }
}
