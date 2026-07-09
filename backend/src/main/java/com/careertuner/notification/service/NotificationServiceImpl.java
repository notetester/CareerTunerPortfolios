package com.careertuner.notification.service;

import java.util.List;
import java.util.Set;

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
import com.careertuner.privacy.service.PrivacyPolicyService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationServiceImpl implements NotificationService {

    /**
     * actorId 를 차단 억제 판정용으로만 쓰는 시스템 발신 type — 응답에 actor 를 노출하지 않는다.
     * 추천 원글이 익명 글이면 actor 노출이 곧 익명 작성자 신원 누출이다(P-03≡N-13).
     */
    private static final Set<String> ACTOR_HIDDEN_TYPES = Set.of("RECOMMENDED_POST", "RECOMMENDED_JOB");

    private final NotificationMapper notificationMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final SenderRelationResolver senderRelationResolver;
    private final PrivacyPolicyService privacyPolicyService;

    @Override
    @Transactional
    public void notify(Notification notification) {
        // 관계성 type 은 actorId 필수(N-14) — 없으면 차단 억제(isBlockedSender)를 평가할 수 없어
        // 차단 우회 구멍이 된다. 수신 표면이므로 fail-closed: 생성하지 않고 에러 로그만 남긴다(호출 흐름은 유지).
        if (notification.getActorId() == null
                && NotificationCategories.RELATION_AWARE_TYPES.contains(notification.getType())) {
            log.error("관계성 알림에 actorId 누락 — 차단 평가 불가로 미발행(fail-closed): type={}, userId={}, targetId={}",
                    notification.getType(), notification.getUserId(), notification.getTargetId());
            return;
        }
        // 개인 차단 정책 — 차단한 계정/IP 발신자의 알림은 생성 자체를 하지 않는다(조용한 실패).
        if (notification.getActorId() != null
                && privacyPolicyService.isBlockedSender(notification.getUserId(), notification.getActorId())) {
            return;
        }
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

    @Override
    @Transactional
    public void delete(Long notificationId, Long userId) {
        Notification notification = notificationMapper.findById(notificationId);
        if (notification == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "알림을 찾을 수 없습니다.");
        }
        if (!notification.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "본인의 알림만 삭제할 수 있습니다.");
        }
        notificationMapper.deleteByIdAndUser(notificationId, userId);
    }

    @Override
    @Transactional
    public void deleteAll(Long userId) {
        notificationMapper.deleteAllByUser(userId);
    }

    private NotificationResponse toResponse(Notification n) {
        NotificationResponse.ActorDto actor = null;
        // 억제 판정용 actorId(ACTOR_HIDDEN_TYPES)는 응답에 내리지 않는다 — 익명 작성자 신원 보호.
        if (n.getActorId() != null && !ACTOR_HIDDEN_TYPES.contains(n.getType())) {
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
