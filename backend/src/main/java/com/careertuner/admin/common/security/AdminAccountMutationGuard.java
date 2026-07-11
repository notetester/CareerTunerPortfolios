package com.careertuner.admin.common.security;

import java.util.List;

import org.springframework.stereotype.Component;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;

import lombok.RequiredArgsConstructor;

/** 관리자 역할·상태 변경에서 자기 보호와 검증된 비seed 슈퍼 관리자 quorum을 집행한다. */
@Component
@RequiredArgsConstructor
public class AdminAccountMutationGuard {

    private static final int MIN_SAFE_SUPER_ADMIN_QUORUM = 3;

    private final AdminAccountGuardMapper mapper;

    public AdminAccountState validateStatusChange(AuthUser actor, Long targetUserId, String nextStatus) {
        return validateStatusChange(actor, targetUserId, nextStatus, false);
    }

    /** 자동 제재처럼 이미 소프트 삭제된 계정을 안전하게 건너뛰어야 하는 흐름용 가드. */
    public AdminAccountState validateStatusChangeOrSkipDeleted(
            AuthUser actor, Long targetUserId, String nextStatus) {
        return validateStatusChange(actor, targetUserId, nextStatus, true);
    }

    private AdminAccountState validateStatusChange(
            AuthUser actor, Long targetUserId, String nextStatus, boolean allowDeletedNoop) {
        List<Long> safeSuperAdminIds = mapper.lockSafeActiveSuperAdminIds();
        AdminAccountState target = mapper.findAccountForUpdate(targetUserId);
        if (target == null) {
            return null;
        }
        if ("DELETED".equals(target.status()) && !"DELETED".equals(nextStatus)) {
            if (allowDeletedNoop) {
                return target;
            }
            throw new BusinessException(ErrorCode.CONFLICT,
                    "소프트 삭제된 계정은 일반 상태 변경으로 복구할 수 없습니다.");
        }
        if (actor != null && actor.id().equals(targetUserId) && !"ACTIVE".equals(nextStatus)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "본인 관리자 계정을 비활성화할 수 없습니다.");
        }
        if (actor != null && "ADMIN".equals(actor.role()) && isAdminRole(target.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "일반 관리자는 다른 관리자 계정 상태를 변경할 수 없습니다.");
        }
        if (safeSuperAdminIds.contains(targetUserId)
                && !"ACTIVE".equals(nextStatus)
                && safeSuperAdminIds.size() - 1 < MIN_SAFE_SUPER_ADMIN_QUORUM) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "검증된 비seed 활성 슈퍼 관리자는 최소 3명 유지해야 합니다.");
        }
        return target;
    }

    public AdminAccountState validateRoleChange(AuthUser actor, Long targetUserId, String nextRole) {
        List<Long> safeSuperAdminIds = mapper.lockSafeActiveSuperAdminIds();
        AdminAccountState target = mapper.findAccountForUpdate(targetUserId);
        if (target == null) {
            return null;
        }
        if (actor != null && actor.id().equals(targetUserId) && !target.role().equals(nextRole)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "본인 관리자 역할을 변경할 수 없습니다.");
        }
        if (!"SUPER_ADMIN".equals(target.role()) && "SUPER_ADMIN".equals(nextRole)
                && !mapper.isSafeSuperAdminPromotionCandidate(targetUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "ACTIVE·이메일 검증·개별 BCrypt·비seed 조건을 만족한 계정만 슈퍼 관리자로 승격할 수 있습니다.");
        }
        if (safeSuperAdminIds.contains(targetUserId)
                && !"SUPER_ADMIN".equals(nextRole)
                && safeSuperAdminIds.size() - 1 < MIN_SAFE_SUPER_ADMIN_QUORUM) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "검증된 비seed 활성 슈퍼 관리자는 최소 3명 유지해야 합니다.");
        }
        return target;
    }

    private static boolean isAdminRole(String role) {
        return "ADMIN".equals(role) || "SUPER_ADMIN".equals(role);
    }
}
