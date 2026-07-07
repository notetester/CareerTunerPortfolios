package com.careertuner.admin.activitylog;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.activitylog.domain.UserActivityLog;
import com.careertuner.activitylog.domain.UserSecurityHistory;
import com.careertuner.activitylog.mapper.ActivityLogMapper;
import com.careertuner.activitylog.mapper.SecurityHistoryMapper;
import com.careertuner.admin.common.AdminAccess;
import com.careertuner.common.security.AuthUser;

import lombok.RequiredArgsConstructor;

/** 관리자 활동 로그 + 보안 이력 조회 — 도메인/타입/성공/사용자/기간/키워드 필터 + 페이징. */
@Service
@RequiredArgsConstructor
public class AdminActivityLogService {

    private final ActivityLogMapper mapper;
    private final SecurityHistoryMapper securityMapper;

    public record ActivityLogPage(List<UserActivityLog> items, int total, int page, int size) {
    }

    public record SecurityHistoryPage(List<UserSecurityHistory> items, int total, int page, int size) {
    }

    @Transactional(readOnly = true)
    public ActivityLogPage search(AuthUser authUser, String keyword, String domain, String activityType,
                                  Boolean success, Long userId, String from, String to, int page, int size) {
        AdminAccess.requireAdmin(authUser);
        int p = Math.max(0, page);
        int s = size <= 0 || size > 200 ? 50 : size;
        List<UserActivityLog> items = mapper.findActivityLogs(blankToNull(keyword), blankToNull(domain),
                blankToNull(activityType), success, userId, blankToNull(from), blankToNull(to), s, p * s);
        int total = mapper.countActivityLogs(blankToNull(keyword), blankToNull(domain),
                blankToNull(activityType), success, userId, blankToNull(from), blankToNull(to));
        return new ActivityLogPage(items, total, p, s);
    }

    @Transactional(readOnly = true)
    public SecurityHistoryPage searchSecurity(AuthUser authUser, String keyword, String eventType, String eventStage,
                                              Boolean success, Long userId, String from, String to, int page, int size) {
        AdminAccess.requireAdmin(authUser);
        int p = Math.max(0, page);
        int s = size <= 0 || size > 200 ? 50 : size;
        List<UserSecurityHistory> items = securityMapper.findSecurityHistories(blankToNull(keyword), blankToNull(eventType),
                blankToNull(eventStage), success, userId, blankToNull(from), blankToNull(to), s, p * s);
        int total = securityMapper.countSecurityHistories(blankToNull(keyword), blankToNull(eventType),
                blankToNull(eventStage), success, userId, blankToNull(from), blankToNull(to));
        return new SecurityHistoryPage(items, total, p, s);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
