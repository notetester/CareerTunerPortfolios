package com.careertuner.admin.chatbot.service;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.admin.chatbot.dto.AdminUnansweredQuestionResponse;
import com.careertuner.admin.chatbot.mapper.AdminUnansweredMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUnansweredServiceImpl implements AdminUnansweredService {

    /** 조회 가능한 상태값(전 상태). */
    private static final Set<String> QUERYABLE = Set.of("NEW", "REVIEWED", "CONVERTED", "DISMISSED");
    /** 운영자가 PATCH 로 옮길 수 있는 상태(전환 CONVERTED 는 2단계 전용 → 제외). */
    private static final Set<String> PATCHABLE = Set.of("REVIEWED", "DISMISSED");

    private final AdminUnansweredMapper mapper;

    @Override
    public List<AdminUnansweredQuestionResponse> getUnanswered(AuthUser authUser, String status, int page, int size) {
        requireAdmin(authUser);
        String normStatus = (status == null || status.isBlank()) ? "NEW" : status.trim().toUpperCase();
        if (!QUERYABLE.contains(normStatus)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "조회할 수 없는 상태입니다: " + status);
        }
        int safeSize = Math.max(1, Math.min(size, 100));
        int safePage = Math.max(0, page);
        return mapper.findGrouped(normStatus, safeSize, safePage * safeSize);
    }

    @Override
    @Transactional
    public void updateStatus(AuthUser authUser, Long id, String status) {
        requireAdmin(authUser);
        if (id == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "대상 id 가 필요합니다.");
        }
        String normStatus = (status == null) ? "" : status.trim().toUpperCase();
        if (!PATCHABLE.contains(normStatus)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "변경 가능한 상태는 REVIEWED/DISMISSED 입니다.");
        }
        int changed = mapper.updateStatusByGroup(id, normStatus);
        if (changed == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "대상 질문을 찾을 수 없습니다.");
        }
    }

    private void requireAdmin(AuthUser authUser) {
        if (authUser == null || !"ADMIN".equals(authUser.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "관리자 권한이 필요합니다.");
        }
    }
}
