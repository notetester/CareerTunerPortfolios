package com.careertuner.community.moderation.service;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.admin.user.dto.AdminUserRow;
import com.careertuner.admin.user.mapper.AdminUserMapper;
import com.careertuner.auth.mapper.AuthMapper;
import com.careertuner.community.mapper.CommunityPostMapper;
import com.careertuner.notification.domain.Notification;
import com.careertuner.notification.service.NotificationService;

import lombok.RequiredArgsConstructor;

/**
 * 검열 누적 → 사용자 단위 자동 제재.
 *
 * 게시글이 AI 검열로 숨김 처리될 때 호출되어, 해당 사용자의 누적 숨김 글 수가
 * 제재 임계(sanction_threshold) 이상이면 사용자를 BLOCKED 로 자동 차단한다.
 * 게시글 단위 자동숨김(hide_threshold, 신뢰도)과 임계가 분리돼 있다.
 *
 * A 도메인(users 상태)의 기존 인프라를 재사용한다(AdminUserMapper/AuthMapper 미수정, 호출만).
 * 시스템 자동 변경이므로 actor=null 로 기록한다(status_changed_by/actor_user_id 는 NULL 허용 — "시스템 변경이면 NULL").
 */
@Service
@RequiredArgsConstructor
public class UserSanctionService {

    private static final Logger log = LoggerFactory.getLogger(UserSanctionService.class);
    private static final String ACTIVE = "ACTIVE";
    private static final String BLOCKED = "BLOCKED";

    private final CommunityPostMapper postMapper;
    private final AdminUserMapper userMapper;
    private final AuthMapper authMapper;
    private final NotificationService notificationService;
    private final ModerationSettingService settingService;

    /**
     * 숨김 처리 직후 호출. 누적 숨김 글 수가 제재 임계 이상이고 사용자가 ACTIVE 면 자동 차단.
     * 이미 BLOCKED/DORMANT/DELETED 면 관리자 수동 조치와 충돌하지 않도록 건너뛴다.
     */
    @Transactional
    public void sanctionIfNeeded(Long userId) {
        if (userId == null) {
            return;
        }

        int hiddenCount = postMapper.countHiddenByUser(userId);
        int threshold = settingService.getSanctionThreshold();
        if (hiddenCount < threshold) {
            return;
        }

        AdminUserRow user = userMapper.findUser(userId);
        if (user == null || !ACTIVE.equals(user.getStatus())) {
            return; // 이미 비활성/차단/탈퇴 상태면 자동 제재 생략(수동 조치 보존)
        }

        int blockDays = settingService.getBlockDays();
        LocalDateTime blockedUntil = LocalDateTime.now().plusDays(blockDays);
        String reason = "검열 누적 " + hiddenCount + "회 자동 제재";

        // A 도메인 인프라 재사용: 상태 변경 + 이력 + 세션 해지 (actor=null = 시스템)
        userMapper.updateStatus(userId, BLOCKED, reason, blockedUntil, null);
        userMapper.insertStatusHistory(userId, null, ACTIVE, BLOCKED, reason, null, blockedUntil);
        authMapper.revokeAllForUser(userId);

        notificationService.notify(Notification.builder()
                .userId(userId)
                .type("ACCOUNT_BLOCKED")
                .targetType("USER")
                .targetId(userId)
                .title("커뮤니티 활동이 제한되었습니다")
                .message("부적절한 게시글 누적으로 " + blockDays + "일간 이용이 제한됩니다.")
                .link("/support/contact")
                .build());

        log.warn("사용자 자동 제재: userId={}, hiddenCount={}, threshold={}, blockedUntil={}",
                userId, hiddenCount, threshold, blockedUntil);
    }
}
