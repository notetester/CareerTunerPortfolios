package com.careertuner.admin.common.security;

import java.util.List;

import org.springframework.stereotype.Component;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;

import lombok.RequiredArgsConstructor;

/** 관리자 역할·상태 변경에서 자기 보호와 마지막 활성 슈퍼 관리자 보존을 집행한다. */
@Component
@RequiredArgsConstructor
public class AdminAccountMutationGuard {

    private final AdminAccountGuardMapper mapper;

    public AdminAccountState validateStatusChange(AuthUser actor, Long targetUserId, String nextStatus) {
        List<Long> activeSuperAdminIds = mapper.lockActiveSuperAdminIds();
        AdminAccountState target = mapper.findAccountForUpdate(targetUserId);
        if (target == null) {
            return null;
        }
        if (actor.id().equals(targetUserId) && !"ACTIVE".equals(nextStatus)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "본인 관리자 계정을 비활성화할 수 없습니다.");
        }
        if ("ADMIN".equals(actor.role()) && isAdminRole(target.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "일반 관리자는 다른 관리자 계정 상태를 변경할 수 없습니다.");
        }
        if ("SUPER_ADMIN".equals(target.role())
                && "ACTIVE".equals(target.status())
                && !"ACTIVE".equals(nextStatus)
                && activeSuperAdminIds.size() <= 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "마지막 활성 슈퍼 관리자 계정은 비활성화할 수 없습니다.");
        }
        return target;
    }

    public AdminAccountState validateRoleChange(AuthUser actor, Long targetUserId, String nextRole) {
        List<Long> activeSuperAdminIds = mapper.lockActiveSuperAdminIds();
        AdminAccountState target = mapper.findAccountForUpdate(targetUserId);
        if (target == null) {
            return null;
        }
        if (actor.id().equals(targetUserId) && !target.role().equals(nextRole)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "본인 관리자 역할을 변경할 수 없습니다.");
        }
        if ("SUPER_ADMIN".equals(target.role())
                && "ACTIVE".equals(target.status())
                && !"SUPER_ADMIN".equals(nextRole)
                && activeSuperAdminIds.size() <= 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "마지막 활성 슈퍼 관리자 역할은 해제할 수 없습니다.");
        }
        return target;
    }

    private static boolean isAdminRole(String role) {
        return "ADMIN".equals(role) || "SUPER_ADMIN".equals(role);
    }
}
