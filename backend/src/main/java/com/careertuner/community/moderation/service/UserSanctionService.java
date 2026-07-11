package com.careertuner.community.moderation.service;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.admin.common.security.AdminAccountMutationGuard;
import com.careertuner.admin.common.security.AdminAccountState;
import com.careertuner.admin.user.mapper.AdminUserMapper;
import com.careertuner.auth.mapper.AuthMapper;
import com.careertuner.community.mapper.CommunityPostMapper;
import com.careertuner.community.moderation.mapper.PostAiResultMapper;
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

    /** 소프트 스트라이크(이미지 블러) 누적 제재 — 하드(숨김)와 달리 rolling window·높은 임계로 관대하게. */
    private static final int BLUR_WINDOW_DAYS = 30;
    private static final int BLUR_SANCTION_THRESHOLD = 8; // 최근 30일 내 블러 8회 → 차단
    private static final int BLUR_WARN_THRESHOLD = 6;      // 6회에서 경고(마지막 기회)

    private final CommunityPostMapper postMapper;
    private final PostAiResultMapper aiResultMapper;
    private final AdminUserMapper userMapper;
    private final AuthMapper authMapper;
    private final NotificationService notificationService;
    private final ModerationSettingService settingService;
    private final AdminAccountMutationGuard accountMutationGuard;

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
        if (hiddenCount < settingService.getSanctionThreshold()) {
            return;
        }
        blockActiveUser(userId, "검열 누적 " + hiddenCount + "회 자동 제재");
    }

    /**
     * 이미지 블러 누적(소프트 스트라이크) 제재. 하드(숨김)와 분리 —
     * 최근 {@value #BLUR_WINDOW_DAYS}일 rolling window 라 오래된 실수는 빠지고,
     * {@value #BLUR_SANCTION_THRESHOLD}회 누적 시 차단, {@value #BLUR_WARN_THRESHOLD}회에서 경고(마지막 기회).
     */
    @Transactional
    public void sanctionIfNeededForBlur(Long userId) {
        if (userId == null) {
            return;
        }
        LocalDateTime since = LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul")).minusDays(BLUR_WINDOW_DAYS);
        int blurCount = aiResultMapper.countBlurredByUserSince(userId, since);
        if (blurCount >= BLUR_SANCTION_THRESHOLD) {
            blockActiveUser(userId,
                    "부적절 이미지 누적 " + blurCount + "회(최근 " + BLUR_WINDOW_DAYS + "일) 자동 제재");
        } else if (blurCount == BLUR_WARN_THRESHOLD) {
            notificationService.notify(Notification.builder()
                    .userId(userId)
                    .type("COMMUNITY_STRIKE_WARNING")
                    .targetType("USER")
                    .targetId(userId)
                    .title("이미지 검열 경고")
                    .message("첨부 이미지가 최근 " + BLUR_WINDOW_DAYS + "일 내 " + blurCount + "회 블러 처리되었습니다. "
                            + BLUR_SANCTION_THRESHOLD + "회 누적 시 커뮤니티 이용이 제한될 수 있습니다.")
                    .link("/community?view=guidelines")
                    .build());
            log.info("이미지 블러 누적 경고: userId={}, blurCount={}", userId, blurCount);
        }
    }

    /**
     * ACTIVE 사용자를 block_days 만큼 자동 차단 + 이력·세션해지·알림. 이미 비활성/차단이면 생략(수동조치 보존).
     * blockedUntil 은 DB NOW()(KST 벽시계)와 비교되므로 KST 로 저장해 시간원을 일치시킨다. actor=null = 시스템.
     */
    private void blockActiveUser(Long userId, String reason) {
        AdminAccountState locked = accountMutationGuard.validateStatusChangeOrSkipDeleted(null, userId, BLOCKED);
        if (locked == null || !ACTIVE.equals(locked.status())
                || "ADMIN".equals(locked.role()) || "SUPER_ADMIN".equals(locked.role())) {
            return;
        }
        int blockDays = settingService.getBlockDays();
        LocalDateTime blockedUntil = LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul")).plusDays(blockDays);
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
        log.warn("사용자 자동 제재: userId={}, reason={}, blockedUntil={}", userId, reason, blockedUntil);
    }
}
