package com.careertuner.notification.service;

import java.util.List;
import java.util.Map;

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

    /**
     * 알림 type → 필요 권한 코드(ANY-of) 매핑.
     *
     * <p>수신자 = (매핑된 권한을 실효 보유한 ADMIN ∪ SUPER_ADMIN) − opt-out.
     * 권한 코드는 기존 admin_permission_policy 카탈로그 값(20260624·20260702 patch seed).
     * NEW_COMPANY_APPLICATION / NEW_JOB_POSTING_REVIEW 는 W1(기업/공고 트랙)이 발행하며
     * 팬아웃은 type 문자열만 알면 된다. 매핑에 없는 type 은 종전대로 활성 관리자 전원(−opt-out).</p>
     */
    private static final Map<String, List<String>> TYPE_TO_REQUIRED_PERMISSIONS = Map.of(
            "NEW_REPORT", List.of("CONTENT_MANAGE", "CONTENT_ADMIN"),
            "NEW_TICKET", List.of("CONTENT_MANAGE", "CONTENT_ADMIN"),
            "NEW_JOB_POSTING_REVIEW", List.of("CONTENT_MANAGE", "CONTENT_ADMIN"),
            "NEW_COMPANY_APPLICATION", List.of("MEMBER_ADMIN", "USER_STATUS_WRITE"),
            "NEW_USER", List.of("MEMBER_ADMIN", "USER_READ"));

    /** notification_preference.categories_json 의 opt-out 하위 키 접두사. */
    private static final String OPT_OUT_KEY_PREFIX = "admin.";

    private final AdminRecipientMapper recipientMapper;
    private final NotificationService notificationService;

    @Override
    public void fanout(String type, String targetType, Long targetId,
                       String title, String message, String link) {
        List<String> requiredPermissions = TYPE_TO_REQUIRED_PERMISSIONS.get(type);
        List<Long> adminIds = recipientMapper.findAdminIdsForType(
                requiredPermissions, OPT_OUT_KEY_PREFIX + type);
        if (adminIds.isEmpty()) {
            log.warn("관리자 알림 팬아웃 대상 없음 type={} (권한 매핑={})", type, requiredPermissions);
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
