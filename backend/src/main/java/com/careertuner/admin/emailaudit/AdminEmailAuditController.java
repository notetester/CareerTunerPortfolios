package com.careertuner.admin.emailaudit;

import com.careertuner.admin.permission.annotation.RequireAdminPermission;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.admin.common.AdminAccess;
import com.careertuner.admin.emailaudit.dto.EmailAuditRow;
import com.careertuner.admin.emailaudit.mapper.EmailAuditMapper;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import lombok.RequiredArgsConstructor;

/**
 * 이메일 인증/재설정 토큰 <b>전역</b> 감사 콘솔 API.
 *
 * <p>사용자 단위 감사(AdminEmailAuditPage/UserContextExplorer)와 별개로, 전체 계정에 걸친
 * 최근 발급 이력을 email·purpose·status 로 검색한다(스프레이성 재설정 요청 등 이상 탐지용).
 * TripTogether 이메일 토큰 감사의 전역 목록 축을 이식했다. 토큰 값은 노출하지 않는다.</p>
 */
@RestController
@RequestMapping("/api/admin/email-audit")
@RequireAdminPermission({"AUDIT_READ"})
@RequiredArgsConstructor
public class AdminEmailAuditController {

    private final EmailAuditMapper mapper;

    /** 최근 발급 이력. email 부분검색, purpose(VERIFY/RESET_PW), status(USED/EXPIRED/PENDING) 필터. */
    @GetMapping
    public ApiResponse<List<EmailAuditRow>> list(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String purpose,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "200") int limit) {
        AdminAccess.requireAdmin(authUser);
        int capped = Math.max(1, Math.min(limit, 1000));
        return ApiResponse.ok(mapper.findRecent(
                blankToNull(email), blankToNull(purpose), blankToNull(status), capped));
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
