package com.careertuner.notification.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.careertuner.notification.domain.Notification;
import com.careertuner.notification.mapper.AdminRecipientMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminNotificationFanoutServiceImpl implements AdminNotificationFanoutService {

    private static final Logger log = LoggerFactory.getLogger(AdminNotificationFanoutServiceImpl.class);

    private final AdminRecipientMapper recipientMapper;
    private final NotificationService notificationService;

    @Override
    public void fanout(String type, String targetType, Long targetId,
                       String title, String message, String link) {
        List<Long> adminIds = recipientMapper.findActiveAdminIds();
        if (adminIds.isEmpty()) {
            log.warn("관리자 알림 팬아웃 대상 없음 type={}", type);
            return;
        }
        for (Long adminId : adminIds) {
            // notify()가 알림별 자체 트랜잭션으로 insert + 커밋 후 Web Push까지 처리한다.
            notificationService.notify(Notification.builder()
                    .userId(adminId)
                    .type(type)
                    .targetType(targetType)
                    .targetId(targetId)
                    .title(title)
                    .message(message)
                    .link(link)
                    .build());
        }
        log.info("관리자 알림 팬아웃 type={} 대상 {}명", type, adminIds.size());
    }
}
