package com.careertuner.notification.service;

import org.springframework.stereotype.Component;

import com.careertuner.notification.mapper.NotificationMapper;
import com.careertuner.notification.push.SenderRelation;

import lombok.RequiredArgsConstructor;

/**
 * 알림 발신자(actor)와 수신자의 관계(stranger/friend/company/operator)를 판정한다.
 * 판정 실패는 알림 생성 흐름을 막지 않도록 null(관계 미상 = 필터 미적용)로 처리한다.
 */
@Component
@RequiredArgsConstructor
public class SenderRelationResolver {

    private final NotificationMapper notificationMapper;

    public String resolve(Long recipientUserId, Long actorId) {
        if (recipientUserId == null || actorId == null || recipientUserId.equals(actorId)) {
            return null;
        }
        try {
            String role = notificationMapper.findUserRole(actorId);
            if ("ADMIN".equals(role) || "SUPER_ADMIN".equals(role)) {
                return SenderRelation.OPERATOR;
            }
            if ("COMPANY".equals(role)) {
                return SenderRelation.COMPANY;
            }
            if (notificationMapper.countFriendship(recipientUserId, actorId) > 0) {
                return SenderRelation.FRIEND;
            }
            return SenderRelation.STRANGER;
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
