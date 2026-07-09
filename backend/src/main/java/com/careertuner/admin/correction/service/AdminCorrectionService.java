package com.careertuner.admin.correction.service;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.admin.common.AdminAccess;
import com.careertuner.admin.correction.dto.AdminCorrectionDetail;
import com.careertuner.admin.correction.dto.AdminCorrectionFailureRow;
import com.careertuner.admin.correction.dto.AdminCorrectionPage;
import com.careertuner.admin.correction.dto.AdminCorrectionSearchCriteria;
import com.careertuner.admin.correction.dto.AdminCorrectionSummary;
import com.careertuner.admin.correction.mapper.AdminCorrectionMapper;
import com.careertuner.admin.ops.service.AdminActionLogService;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminCorrectionService {

    private static final Set<String> TYPES = Set.of(
            "SELF_INTRO", "INTERVIEW_ANSWER", "RESUME", "PORTFOLIO");
    private static final Set<String> STATUSES = Set.of("SUCCESS", "FAILED");
    private static final Set<String> MEMO_STATES = Set.of("HAS_MEMO", "NO_MEMO");

    private final AdminCorrectionMapper mapper;
    private final AdminActionLogService actionLogService;

    @Transactional(readOnly = true)
    public AdminCorrectionPage corrections(
            AuthUser authUser,
            String keyword,
            String correctionType,
            String status,
            String memoState,
            int page,
            int size
    ) {
        AdminAccess.requireAdmin(authUser);
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = size <= 0 ? 20 : Math.min(size, 100);
        AdminCorrectionSearchCriteria criteria = new AdminCorrectionSearchCriteria(
                blankToNull(keyword),
                normalizeOptional(correctionType, TYPES, "correctionType"),
                normalizeOptional(status, STATUSES, "status"),
                normalizeOptional(memoState, MEMO_STATES, "memoState"),
                normalizedPage,
                normalizedSize,
                (long) (normalizedPage - 1) * normalizedSize);
        return new AdminCorrectionPage(
                mapper.findCorrections(criteria),
                mapper.countCorrections(criteria),
                normalizedPage,
                normalizedSize);
    }

    @Transactional(readOnly = true)
    public AdminCorrectionSummary summary(AuthUser authUser) {
        AdminAccess.requireAdmin(authUser);
        return mapper.findSummary();
    }

    @Transactional(readOnly = true)
    public AdminCorrectionDetail detail(AuthUser authUser, Long id) {
        AdminAccess.requireAdmin(authUser);
        return requireCorrection(id);
    }

    @Transactional(readOnly = true)
    public List<AdminCorrectionFailureRow> aiFailures(AuthUser authUser, int limit) {
        AdminAccess.requireAdmin(authUser);
        int normalizedLimit = limit <= 0 ? 50 : Math.min(limit, 200);
        return mapper.findAiFailures(normalizedLimit);
    }

    @Transactional
    public void updateMemo(AuthUser authUser, Long id, String memo) {
        AdminAccess.requireAdmin(authUser);
        AdminCorrectionDetail before = requireCorrection(id);
        String normalizedMemo = blankToNull(memo);
        if (normalizedMemo != null && normalizedMemo.length() > 2000) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "운영 메모는 2000자 이하여야 합니다.");
        }
        if (mapper.updateAdminMemo(id, normalizedMemo) != 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "첨삭 요청을 찾을 수 없습니다.");
        }
        actionLogService.record(
                authUser,
                before.getUserId(),
                "CORRECTION_MEMO_UPDATED",
                "CORRECTION",
                before.getAdminMemo(),
                normalizedMemo,
                "첨삭 운영 메모 수정");
    }

    private AdminCorrectionDetail requireCorrection(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "첨삭 요청 ID가 올바르지 않습니다.");
        }
        AdminCorrectionDetail detail = mapper.findCorrection(id);
        if (detail == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "첨삭 요청을 찾을 수 없습니다.");
        }
        return detail;
    }

    private String normalizeOptional(String value, Set<String> allowed, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, field + " 값이 올바르지 않습니다.");
        }
        return normalized;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
