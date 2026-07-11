package com.careertuner.admin.reward;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import com.careertuner.admin.reward.dto.AdminLevelPolicyRequest;
import com.careertuner.admin.reward.mapper.AdminRewardMapper;
import com.careertuner.admin.reward.service.AdminRewardService;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;
import com.careertuner.reward.domain.UserLevelPolicy;
import com.careertuner.reward.service.CouponService;

class AdminRewardLevelTombstoneTest {

    private final AdminRewardMapper mapper = mock(AdminRewardMapper.class);
    private final CouponService couponService = mock(CouponService.class);
    private final AdminRewardService service = new AdminRewardService(mapper, couponService);
    private final AuthUser admin = new AuthUser(2L, "billing@test.dev", "ADMIN");
    private final AdminLevelPolicyRequest request = new AdminLevelPolicyRequest(
            3, "골드", 1_000, 100, null, null, true);

    @Test
    void createRejectsExistingSoftDeletedLevelNumberBeforeInsert() {
        when(mapper.countLevelByNumber(3)).thenReturn(1);

        assertThatThrownBy(() -> service.createLevel(admin, request))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.CONFLICT));

        verify(mapper, never()).insertLevel(any(UserLevelPolicy.class));
    }

    @Test
    void concurrentDuplicateIsReturnedAsConflictInsteadOfRevivingTombstone() {
        when(mapper.countLevelByNumber(3)).thenReturn(0);
        doThrow(new DuplicateKeyException("duplicate"))
                .when(mapper).insertLevel(any(UserLevelPolicy.class));

        assertThatThrownBy(() -> service.createLevel(admin, request))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
    }

    @Test
    void mapperCountsTombstonesAndInsertNeverClearsDeletedAt() throws Exception {
        String xml = Files.readString(Path.of(
                "src/main/resources/mapper/admin/reward/AdminRewardMapper.xml"));
        int countStart = xml.indexOf("<select id=\"countLevelByNumber\"");
        int insertStart = xml.indexOf("<insert id=\"insertLevel\"");

        assertThat(countStart).isGreaterThanOrEqualTo(0);
        assertThat(insertStart).isGreaterThanOrEqualTo(0);
        assertThat(xml.substring(countStart, xml.indexOf("</select>", countStart)))
                .doesNotContain("deleted_at IS NULL");
        assertThat(xml.substring(insertStart, xml.indexOf("</insert>", insertStart)))
                .doesNotContain("ON DUPLICATE KEY UPDATE", "deleted_at = NULL");
    }
}
