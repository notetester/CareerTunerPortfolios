package com.careertuner.admin.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;

class AdminAccountMutationGuardTest {

    private final AdminAccountGuardMapper mapper = mock(AdminAccountGuardMapper.class);
    private final AdminAccountMutationGuard guard = new AdminAccountMutationGuard(mapper);
    private final AuthUser superAdmin = new AuthUser(1L, "super@test.dev", "SUPER_ADMIN");

    @Test
    void rejectsSelfDemotionAndSelfDeactivation() {
        when(mapper.lockSafeActiveSuperAdminIds()).thenReturn(List.of(1L, 2L, 3L));
        when(mapper.findAccountForUpdate(1L))
                .thenReturn(new AdminAccountState(1L, "SUPER_ADMIN", "ACTIVE"));

        assertThatThrownBy(() -> guard.validateRoleChange(superAdmin, 1L, "ADMIN"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThatThrownBy(() -> guard.validateStatusChange(superAdmin, 1L, "BLOCKED"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void rejectsDroppingSafeSuperAdminQuorumBelowThree() {
        when(mapper.lockSafeActiveSuperAdminIds()).thenReturn(List.of(2L, 3L, 4L));
        when(mapper.findAccountForUpdate(2L))
                .thenReturn(new AdminAccountState(2L, "SUPER_ADMIN", "ACTIVE"));

        assertThatThrownBy(() -> guard.validateRoleChange(superAdmin, 2L, "ADMIN"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
        assertThatThrownBy(() -> guard.validateStatusChange(superAdmin, 2L, "DELETED"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
    }

    @Test
    void regularAdminCannotChangeAnotherAdminStatus() {
        AuthUser admin = new AuthUser(3L, "admin@test.dev", "ADMIN");
        when(mapper.lockSafeActiveSuperAdminIds()).thenReturn(List.of(1L, 2L, 5L));
        when(mapper.findAccountForUpdate(4L))
                .thenReturn(new AdminAccountState(4L, "ADMIN", "ACTIVE"));

        assertThatThrownBy(() -> guard.validateStatusChange(admin, 4L, "BLOCKED"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void publicSeedSuperAdminDoesNotCountTowardSafeQuorum() {
        when(mapper.lockSafeActiveSuperAdminIds()).thenReturn(List.of(2L, 3L, 4L));
        when(mapper.findAccountForUpdate(9L))
                .thenReturn(new AdminAccountState(9L, "SUPER_ADMIN", "ACTIVE"));

        assertThat(guard.validateRoleChange(superAdmin, 9L, "ADMIN")).isNotNull();
        assertThat(guard.validateStatusChange(superAdmin, 9L, "DELETED")).isNotNull();
    }

    @Test
    void rejectsUnsafeRuntimeSuperAdminPromotion() {
        when(mapper.lockSafeActiveSuperAdminIds()).thenReturn(List.of(1L, 2L, 3L));
        when(mapper.findAccountForUpdate(7L))
                .thenReturn(new AdminAccountState(7L, "ADMIN", "ACTIVE"));
        when(mapper.isSafeSuperAdminPromotionCandidate(7L)).thenReturn(false);

        assertThatThrownBy(() -> guard.validateRoleChange(superAdmin, 7L, "SUPER_ADMIN"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void allowsVerifiedNonSeedBcryptRuntimePromotion() {
        when(mapper.lockSafeActiveSuperAdminIds()).thenReturn(List.of(1L, 2L, 3L));
        when(mapper.findAccountForUpdate(7L))
                .thenReturn(new AdminAccountState(7L, "ADMIN", "ACTIVE"));
        when(mapper.isSafeSuperAdminPromotionCandidate(7L)).thenReturn(true);

        assertThat(guard.validateRoleChange(superAdmin, 7L, "SUPER_ADMIN")).isNotNull();
    }

    @Test
    void deletedAccountCannotBeRestoredByOrdinaryStatusUpdate() {
        when(mapper.lockSafeActiveSuperAdminIds()).thenReturn(List.of(1L, 2L, 3L));
        when(mapper.findAccountForUpdate(8L))
                .thenReturn(new AdminAccountState(8L, "USER", "DELETED"));

        assertThatThrownBy(() -> guard.validateStatusChange(superAdmin, 8L, "ACTIVE"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
    }

    @Test
    void automatedSanctionCanSkipAnAlreadyDeletedAccountWithoutRestoringIt() {
        when(mapper.lockSafeActiveSuperAdminIds()).thenReturn(List.of(1L, 2L, 3L));
        AdminAccountState deleted = new AdminAccountState(8L, "USER", "DELETED");
        when(mapper.findAccountForUpdate(8L)).thenReturn(deleted);

        assertThat(guard.validateStatusChangeOrSkipDeleted(null, 8L, "BLOCKED"))
                .isSameAs(deleted);
    }
}
