package com.careertuner.admin.user.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.admin.user.dto.AdminUserDetail;
import com.careertuner.admin.user.dto.AdminUserLoginHistoryRow;
import com.careertuner.admin.user.dto.AdminUserRow;
import com.careertuner.admin.user.dto.AdminUserStatusUpdateRequest;
import com.careertuner.admin.user.mapper.AdminUserMapper;
import com.careertuner.auth.mapper.AuthMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private static final Set<String> STATUSES = Set.of("ACTIVE", "DORMANT", "BLOCKED", "DELETED");
    private static final Set<String> ROLES = Set.of("USER", "ADMIN");

    private final AdminUserMapper mapper;
    private final AuthMapper authMapper;

    @Transactional(readOnly = true)
    public List<AdminUserRow> users(AuthUser authUser, String keyword, String status, String role, int limit) {
        requireAdmin(authUser);
        return mapper.findUsers(blankToNull(keyword), normalize(status, STATUSES, false),
                normalize(role, ROLES, false), normalizeLimit(limit));
    }

    @Transactional(readOnly = true)
    public AdminUserDetail detail(AuthUser authUser, Long id) {
        requireAdmin(authUser);
        AdminUserRow user = findExisting(id);
        return new AdminUserDetail(
                user,
                mapper.findLoginHistory(id, 100),
                mapper.findStatusHistory(id, 100),
                mapper.findConsents(id));
    }

    @Transactional(readOnly = true)
    public List<AdminUserLoginHistoryRow> loginHistory(AuthUser authUser, Long id, int limit) {
        requireAdmin(authUser);
        findExisting(id);
        return mapper.findLoginHistory(id, normalizeLimit(limit));
    }

    @Transactional
    public AdminUserRow updateStatus(AuthUser authUser, Long id, AdminUserStatusUpdateRequest request) {
        requireAdmin(authUser);
        AdminUserRow existing = findExisting(id);
        String nextStatus = normalize(request.status(), STATUSES, true);
        LocalDateTime blockedUntil = "BLOCKED".equals(nextStatus) ? request.blockedUntil() : null;
        String reason = blankToNull(request.reason());
        int updated = mapper.updateStatus(id, nextStatus, reason, blockedUntil, authUser.id());
        if (updated == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "회원을 찾을 수 없습니다.");
        }
        mapper.insertStatusHistory(id, authUser.id(), existing.getStatus(), nextStatus, reason,
                blankToNull(request.memo()), blockedUntil);
        if (!"ACTIVE".equals(nextStatus)) {
            authMapper.revokeAllForUser(id);
        }
        return mapper.findUser(id);
    }

    private AdminUserRow findExisting(Long id) {
        AdminUserRow user = mapper.findUser(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "회원을 찾을 수 없습니다.");
        }
        return user;
    }

    private static void requireAdmin(AuthUser authUser) {
        if (authUser == null || !"ADMIN".equals(authUser.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "관리자 권한이 필요합니다.");
        }
    }

    private static int normalizeLimit(int limit) {
        if (limit <= 0) {
            return 50;
        }
        return Math.min(limit, 200);
    }

    private static String normalize(String value, Set<String> allowed, boolean required) {
        if (value == null || value.isBlank()) {
            if (required) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "필수 값이 누락되었습니다.");
            }
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "허용되지 않는 값입니다.");
        }
        return normalized;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
