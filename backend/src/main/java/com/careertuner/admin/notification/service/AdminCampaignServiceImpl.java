package com.careertuner.admin.notification.service;

import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.careertuner.admin.common.AdminAccess;
import com.careertuner.admin.notification.dto.AdminCampaignRequest;
import com.careertuner.admin.notification.dto.AdminCampaignResponse;
import com.careertuner.admin.notification.mapper.AdminCampaignRecipientMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;
import com.careertuner.notification.domain.Notification;
import com.careertuner.notification.service.NotificationService;

import lombok.RequiredArgsConstructor;

/**
 * 관리자 캠페인(공지/광고/추천) 발송 — ACTIVE 사용자 전원 팬아웃.
 * <p>AdminNotificationFanoutServiceImpl 패턴을 따른다: 클래스 트랜잭션 없이
 * notify()가 알림별 자체 트랜잭션으로 insert + 커밋 후 Web Push까지 처리한다.
 * (전체 팬아웃을 한 트랜잭션으로 묶으면 대량 발송 시 커넥션 장기 점유·전체 롤백 위험)
 */
@Service
@RequiredArgsConstructor
public class AdminCampaignServiceImpl implements AdminCampaignService {

    private static final Logger log = LoggerFactory.getLogger(AdminCampaignServiceImpl.class);

    /** 캠페인으로 발송 가능한 알림 type 화이트리스트. */
    private static final Set<String> ALLOWED_TYPES =
            Set.of("NOTICE", "MARKETING_AD", "RECOMMENDED_JOB", "RECOMMENDED_POST");

    private final AdminCampaignRecipientMapper recipientMapper;
    private final NotificationService notificationService;

    @Override
    public AdminCampaignResponse sendCampaign(AuthUser authUser, AdminCampaignRequest request) {
        AdminAccess.requireAdmin(authUser);

        String type = request.type() == null ? "" : request.type().trim().toUpperCase();
        if (!ALLOWED_TYPES.contains(type)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "발송할 수 없는 캠페인 유형입니다.");
        }
        if (isBlank(request.title()) || isBlank(request.message())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "캠페인 제목과 내용은 필수입니다.");
        }
        String link = isBlank(request.link()) ? null : request.link().trim();

        List<Long> userIds = recipientMapper.findActiveUserIds();
        int sent = 0;
        for (Long userId : userIds) {
            // 개별 사용자 발송 실패가 전체 캠페인을 중단시키지 않도록 best-effort.
            try {
                notificationService.notify(Notification.builder()
                        .userId(userId)
                        .type(type)
                        .title(request.title())
                        .message(request.message())
                        .link(link)
                        .build());
                sent++;
            } catch (Exception e) {
                log.error("캠페인 알림 발송 실패: type={} userId={}", type, userId, e);
            }
        }
        log.info("캠페인 알림 팬아웃 완료: type={} 대상 {}명 중 {}명 발송", type, userIds.size(), sent);
        return new AdminCampaignResponse(type, sent);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
