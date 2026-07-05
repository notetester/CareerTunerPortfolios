package com.careertuner.admin.audit.service;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.admin.audit.dto.AdminLoginAuditRow;
import com.careertuner.admin.audit.mapper.AdminLoginAuditMapper;
import com.careertuner.admin.common.AdminAccess;
import com.careertuner.admin.common.grid.AdminGridSpec;
import com.careertuner.admin.common.grid.AdminListNormalizer;
import com.careertuner.admin.common.grid.AdminListQuery;
import com.careertuner.admin.common.grid.AdminListRequest;
import com.careertuner.admin.common.grid.BulkRequest;
import com.careertuner.admin.common.grid.ExportScope;
import com.careertuner.admin.common.grid.PageResult;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;

import lombok.RequiredArgsConstructor;

/**
 * 로그인 감사 목록 서비스 — 공통 그리드 계약. 감사 로그는 조회 전용이라
 * 일괄 작업 없이 목록/내보내기만 제공한다.
 */
@Service
@RequiredArgsConstructor
public class AdminLoginAuditService {

    private static final AdminGridSpec GRID_SPEC = AdminGridSpec.of(
            Set.of("all", "email", "identifier", "ip"),
            Set.of("createdAt", "eventType", "authProvider", "ipAddress", "userEmail"),
            "createdAt",
            "DESC",
            Map.of(
                    "eventType", Set.of("LOGIN", "LOGOUT", "REFRESH"),
                    "authProvider", Set.of("LOCAL", "KAKAO", "NAVER", "GOOGLE"),
                    "result", Set.of("SUCCESS", "FAIL")));

    private final AdminLoginAuditMapper mapper;

    @Transactional(readOnly = true)
    public PageResult<AdminLoginAuditRow> search(AuthUser authUser, AdminListRequest request) {
        AdminAccess.requireAdmin(authUser);
        AdminListQuery query = AdminListNormalizer.normalize(request, GRID_SPEC);
        long total = mapper.countLogins(query.toParams());
        query.clampPage(total);
        List<AdminLoginAuditRow> items = total == 0 ? List.of() : mapper.searchLogins(query.toParams());
        return PageResult.of(items, total, query.page(), query.size());
    }

    @Transactional(readOnly = true)
    public List<AdminLoginAuditRow> exportRows(AuthUser authUser, AdminListRequest request,
                                               ExportScope scope, List<Long> ids) {
        AdminAccess.requireAdmin(authUser);
        switch (scope) {
            case SELECTED -> {
                List<Long> sanitized = BulkRequest.sanitizeIds(ids, GRID_SPEC.selectedIdsMax());
                if (sanitized.isEmpty()) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT, "선택된 항목이 없습니다.");
                }
                AdminListQuery query = AdminListNormalizer.normalize(request, GRID_SPEC);
                return mapper.findLoginsByIds(sanitized, query.sortBy(), query.sortDir());
            }
            case PAGE -> {
                AdminListQuery query = AdminListNormalizer.normalize(request, GRID_SPEC);
                long total = mapper.countLogins(query.toParams());
                query.clampPage(total);
                return total == 0 ? List.of() : mapper.searchLogins(query.toParams());
            }
            case ALL -> {
                AdminListQuery query = AdminListNormalizer.normalize(new AdminListRequest(), GRID_SPEC);
                Map<String, Object> params = query.toParams();
                params.put("exportLimit", GRID_SPEC.exportMaxRows());
                return mapper.findLoginsForExport(params);
            }
            default -> {
                AdminListQuery query = AdminListNormalizer.normalize(request, GRID_SPEC);
                Map<String, Object> params = query.toParams();
                params.put("exportLimit", GRID_SPEC.exportMaxRows());
                return mapper.findLoginsForExport(params);
            }
        }
    }
}
