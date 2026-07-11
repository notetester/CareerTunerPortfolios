package com.careertuner.admin.user.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.careertuner.admin.user.dto.AdminUserDetail;
import com.careertuner.admin.user.dto.AdminUserCreateRequest;
import com.careertuner.admin.user.dto.AdminUserLoginHistoryRow;
import com.careertuner.admin.user.dto.AdminUserRow;
import com.careertuner.admin.user.dto.AdminUserStatusUpdateRequest;
import com.careertuner.admin.user.mapper.AdminUserMapper;
import com.careertuner.admin.common.AdminAccess;
import com.careertuner.admin.common.security.AdminAccountMutationGuard;
import com.careertuner.admin.common.security.AdminAccountState;
import com.careertuner.admin.common.grid.AdminGridSpec;
import com.careertuner.admin.common.grid.AdminListNormalizer;
import com.careertuner.admin.common.grid.AdminListQuery;
import com.careertuner.admin.common.grid.AdminListRequest;
import com.careertuner.admin.common.grid.BulkActionResult;
import com.careertuner.admin.common.grid.BulkRequest;
import com.careertuner.admin.common.grid.ExportScope;
import com.careertuner.admin.common.grid.PageResult;
import com.careertuner.admin.ops.service.AdminActionLogService;
import com.careertuner.auth.mapper.AuthMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;
import com.careertuner.user.domain.User;
import com.careertuner.user.mapper.UserMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private static final Set<String> STATUSES = Set.of("ACTIVE", "DORMANT", "BLOCKED", "DELETED");
    private static final Set<String> MUTABLE_STATUSES = Set.of("ACTIVE", "DORMANT", "BLOCKED");
    private static final Set<String> ROLES = Set.of("USER", "ADMIN", "SUPER_ADMIN");

    /** 회원 그리드 화이트리스트(검색 컬럼/정렬 키/enum 필터). */
    private static final AdminGridSpec GRID_SPEC = AdminGridSpec.of(
            Set.of("all", "email", "name"),
            Set.of("createdAt", "email", "name", "status", "role", "plan", "credit", "lastLoginAt", "loginFailCount"),
            "createdAt",
            "DESC",
            Map.of("status", STATUSES, "role", ROLES));

    private final AdminUserMapper mapper;
    private final AuthMapper authMapper;
    private final AdminActionLogService actionLogService;
    private final AdminAccountMutationGuard accountMutationGuard;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AdminUserRow create(AuthUser authUser, AdminUserCreateRequest request) {
        requireAdmin(authUser);
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userMapper.countByEmail(email) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 사용 중인 이메일입니다.");
        }

        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(request.password()))
                .passwordEnabled(true)
                .name(request.name().trim())
                .emailVerified(false)
                .userType("JOB_SEEKER")
                .role("USER")
                .status("ACTIVE")
                .plan("FREE")
                .credit(0)
                .build();
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 사용 중인 이메일입니다.");
        }
        actionLogService.record(authUser, user.getId(), "USER_CREATED", "USER",
                null, "{\"role\":\"USER\",\"status\":\"ACTIVE\"}", "관리자 콘솔 회원 생성");
        return mapper.findUser(user.getId());
    }

    @Transactional(readOnly = true)
    public List<AdminUserRow> users(AuthUser authUser, String keyword, String status, String role, int limit) {
        requireAdmin(authUser);
        return mapper.findUsers(blankToNull(keyword), normalize(status, STATUSES, false),
                normalize(role, ROLES, false), normalizeLimit(limit));
    }

    /**
     * 공통 그리드 계약 목록 조회. 정규화(1차 화이트리스트) → count →
     * page 클램프(2차) → 목록 조회 순서를 지킨다.
     */
    @Transactional(readOnly = true)
    public PageResult<AdminUserRow> search(AuthUser authUser, AdminListRequest request) {
        requireAdmin(authUser);
        AdminListQuery query = AdminListNormalizer.normalize(request, GRID_SPEC);
        long total = mapper.countUsers(query.toParams());
        query.clampPage(total);
        List<AdminUserRow> items = total == 0 ? List.of() : mapper.searchUsers(query.toParams());
        return PageResult.of(items, total, query.page(), query.size());
    }

    /** 내보내기 대상 행 조회. scope 별 분기(all/search/selected/page). */
    @Transactional(readOnly = true)
    public List<AdminUserRow> exportRows(AuthUser authUser, AdminListRequest request,
                                         ExportScope scope, List<Long> ids) {
        requireAdmin(authUser);
        switch (scope) {
            case SELECTED -> {
                List<Long> sanitized = BulkRequest.sanitizeIds(ids, GRID_SPEC.selectedIdsMax());
                if (sanitized.isEmpty()) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT, "선택된 항목이 없습니다.");
                }
                AdminListQuery query = AdminListNormalizer.normalize(request, GRID_SPEC);
                return mapper.findUsersByIds(sanitized, query.sortBy(), query.sortDir());
            }
            case PAGE -> {
                AdminListQuery query = AdminListNormalizer.normalize(request, GRID_SPEC);
                long total = mapper.countUsers(query.toParams());
                query.clampPage(total);
                return total == 0 ? List.of() : mapper.searchUsers(query.toParams());
            }
            case ALL -> {
                // 검색 조건을 비운 요청으로 전량(상한 내) 내보내기.
                AdminListQuery query = AdminListNormalizer.normalize(new AdminListRequest(), GRID_SPEC);
                Map<String, Object> params = query.toParams();
                params.put("exportLimit", GRID_SPEC.exportMaxRows());
                return mapper.findUsersForExport(params);
            }
            default -> {
                AdminListQuery query = AdminListNormalizer.normalize(request, GRID_SPEC);
                Map<String, Object> params = query.toParams();
                params.put("exportLimit", GRID_SPEC.exportMaxRows());
                return mapper.findUsersForExport(params);
            }
        }
    }

    /**
     * 회원 상태 일괄 변경. 가드: id 양수·중복 제거·상한, 본인 계정 포함 거부,
     * 상태값 화이트리스트. 동일 상태/미존재 대상은 건너뛴다.
     */
    @Transactional
    public BulkActionResult bulkStatus(AuthUser authUser, BulkRequest request) {
        requireAdmin(authUser);
        List<Long> ids = request.sanitizedIds(GRID_SPEC.bulkIdsMax());
        if (ids.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "일괄 처리할 대상이 없습니다.");
        }
        if (authUser != null && ids.contains(authUser.id())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "본인 계정은 일괄 상태 변경 대상에 포함할 수 없습니다.");
        }
        String nextStatus = normalize(request.param("status"), MUTABLE_STATUSES, true);
        String reason = blankToNull(request.param("reason"));
        LocalDateTime blockedUntil = "BLOCKED".equals(nextStatus)
                ? parseDateTime(request.param("blockedUntil"))
                : null;

        int updated = 0;
        int skipped = 0;
        for (Long id : ids) {
            AdminAccountState locked = accountMutationGuard.validateStatusChange(authUser, id, nextStatus);
            if (locked == null || nextStatus.equals(locked.status())) {
                skipped++;
                continue;
            }
            AdminUserRow existing = mapper.findUser(id);
            mapper.updateStatus(id, nextStatus, reason, blockedUntil, authUser.id());
            mapper.insertStatusHistory(id, authUser.id(), existing.getStatus(), nextStatus, reason,
                    null, blockedUntil);
            actionLogService.record(authUser, id, "USER_STATUS_BULK_UPDATED", "USER",
                    "{\"status\":\"%s\"}".formatted(existing.getStatus()),
                    "{\"status\":\"%s\"}".formatted(nextStatus),
                    reason);
            if (!"ACTIVE".equals(nextStatus)) {
                authMapper.revokeAllForUser(id);
            }
            updated++;
        }
        return new BulkActionResult(ids.size(), updated, skipped);
    }

    @Transactional(readOnly = true)
    public AdminUserDetail detail(AuthUser authUser, Long id) {
        requireAdmin(authUser);
        AdminUserRow user = findExisting(id);
        return new AdminUserDetail(
                user,
                mapper.findLoginHistory(id, 100),
                mapper.findStatusHistory(id, 100),
                mapper.findConsents(id),
                mapper.findEmailVerifications(id, 100),
                mapper.findRefreshTokens(id, 50),
                mapper.findAiUsage(id, 100),
                mapper.findProfile(id));
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
        String nextStatus = normalize(request.status(), MUTABLE_STATUSES, true);
        AdminAccountState locked = accountMutationGuard.validateStatusChange(authUser, id, nextStatus);
        if (locked == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "회원을 찾을 수 없습니다.");
        }
        AdminUserRow existing = findExisting(id);
        LocalDateTime blockedUntil = "BLOCKED".equals(nextStatus) ? request.blockedUntil() : null;
        String reason = blankToNull(request.reason());
        int updated = mapper.updateStatus(id, nextStatus, reason, blockedUntil, authUser.id());
        if (updated == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "회원을 찾을 수 없습니다.");
        }
        mapper.insertStatusHistory(id, authUser.id(), existing.getStatus(), nextStatus, reason,
                blankToNull(request.memo()), blockedUntil);
        actionLogService.record(authUser, id, "USER_STATUS_UPDATED", "USER",
                "{\"status\":\"%s\"}".formatted(existing.getStatus()),
                "{\"status\":\"%s\"}".formatted(nextStatus),
                reason);
        if (!"ACTIVE".equals(nextStatus)) {
            authMapper.revokeAllForUser(id);
        }
        return mapper.findUser(id);
    }

    @Transactional
    public AdminUserRow softDelete(AuthUser authUser, Long id, String reason) {
        requireAdmin(authUser);
        AdminAccountState locked = accountMutationGuard.validateStatusChange(authUser, id, "DELETED");
        if (locked == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "회원을 찾을 수 없습니다.");
        }
        if ("DELETED".equals(locked.status())) {
            return findExisting(id);
        }
        AdminUserRow existing = findExisting(id);
        String normalizedReason = blankToNull(reason);
        mapper.updateStatus(id, "DELETED", normalizedReason, null, authUser.id());
        mapper.insertStatusHistory(id, authUser.id(), existing.getStatus(), "DELETED",
                normalizedReason, "관리자 소프트 삭제", null);
        actionLogService.record(authUser, id, "USER_SOFT_DELETED", "USER",
                "{\"status\":\"%s\"}".formatted(existing.getStatus()),
                "{\"status\":\"DELETED\"}", normalizedReason);
        authMapper.revokeAllForUser(id);
        return mapper.findUser(id);
    }

    @Transactional
    public BulkActionResult bulkSoftDelete(AuthUser authUser, BulkRequest request) {
        requireAdmin(authUser);
        List<Long> ids = request.sanitizedIds(GRID_SPEC.bulkIdsMax());
        if (ids.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "일괄 삭제할 대상이 없습니다.");
        }
        if (ids.contains(authUser.id())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "본인 계정은 일괄 삭제 대상에 포함할 수 없습니다.");
        }
        String reason = blankToNull(request.param("reason"));
        int updated = 0;
        int skipped = 0;
        for (Long id : ids) {
            AdminAccountState locked = accountMutationGuard.validateStatusChange(authUser, id, "DELETED");
            if (locked == null || "DELETED".equals(locked.status())) {
                skipped++;
                continue;
            }
            AdminUserRow existing = findExisting(id);
            mapper.updateStatus(id, "DELETED", reason, null, authUser.id());
            mapper.insertStatusHistory(id, authUser.id(), existing.getStatus(), "DELETED",
                    reason, "관리자 일괄 소프트 삭제", null);
            actionLogService.record(authUser, id, "USER_SOFT_DELETED", "USER",
                    "{\"status\":\"%s\"}".formatted(existing.getStatus()),
                    "{\"status\":\"DELETED\"}", reason);
            authMapper.revokeAllForUser(id);
            updated++;
        }
        return new BulkActionResult(ids.size(), updated, skipped);
    }

    private AdminUserRow findExisting(Long id) {
        AdminUserRow user = mapper.findUser(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "회원을 찾을 수 없습니다.");
        }
        return user;
    }

    private static void requireAdmin(AuthUser authUser) {
        AdminAccess.requireAdmin(authUser);
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

    /** ISO-8601(yyyy-MM-dd'T'HH:mm[:ss]) 문자열 파싱. 비어 있으면 null, 형식 오류는 400. */
    private static LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "차단 만료 시각 형식이 올바르지 않습니다.");
        }
    }
}
