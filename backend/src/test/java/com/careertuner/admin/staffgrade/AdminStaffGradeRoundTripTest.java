package com.careertuner.admin.staffgrade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.admin.staffgrade.dto.AdminStaffGradeRow;
import com.careertuner.admin.staffgrade.dto.AdminStaffGradeUpsertRequest;
import com.careertuner.admin.staffgrade.service.AdminStaffGradeService;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.security.AuthUser;

/**
 * 관리자/직원 등급·급여 <b>실 DB round-trip</b> — 등급/급여 upsert + 변경 이력을 team1_db 상대로 검증.
 * SUPER_ADMIN 전용 접근과 일반 관리자 거부(양상태)를 함께 확인한다. {@code @Transactional} 롤백.
 */
@SpringBootTest
@Transactional
class AdminStaffGradeRoundTripTest {

    @Autowired AdminStaffGradeService service;
    @Autowired JdbcTemplate jdbc;

    private final AuthUser superAdmin = new AuthUser(1L, "super@ct.test", "SUPER_ADMIN");

    private Long createStaff(String email) {
        jdbc.update("INSERT INTO users(email, name, role, status, plan, credit, email_verified, password_enabled) "
                + "VALUES(?, ?, 'ADMIN', 'ACTIVE', 'FREE', 0, 1, 1)", email, "등급테스트");
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    @Test
    void upsert_savesGradeAndSalary_andRecordsHistory() {
        Long userId = createStaff("rt.grade.staff@ct.test");

        AdminStaffGradeRow saved = service.upsert(superAdmin, userId, new AdminStaffGradeUpsertRequest(
                "플랫폼", "SENIOR", "T3", "B", "G4", "2", 62_000_000, "KRW", LocalDate.parse("2026-01-01"), "정기 인상"));

        assertThat(saved.getDepartment()).isEqualTo("플랫폼");
        assertThat(saved.getSeniority()).isEqualTo("SENIOR");
        assertThat(saved.getBaseSalary()).isEqualTo(62_000_000);
        assertThat(service.history(superAdmin, userId)).hasSize(1);

        // 재편집 → 급여 갱신 + 이력 2건
        AdminStaffGradeRow updated = service.upsert(superAdmin, userId, new AdminStaffGradeUpsertRequest(
                "플랫폼", "LEAD", "T4", "A", "G5", "1", 75_000_000, "KRW", LocalDate.parse("2026-07-01"), "승진"));
        assertThat(updated.getBaseSalary()).isEqualTo(75_000_000);
        assertThat(updated.getJobGrade()).isEqualTo("G5");
        assertThat(service.history(superAdmin, userId)).hasSize(2);
    }

    @Test
    void upsert_rejectedForNonSuperAdmin() {
        Long userId = createStaff("rt.grade.staff2@ct.test");
        AuthUser normalAdmin = new AuthUser(2L, "admin@ct.test", "ADMIN");

        assertThatThrownBy(() -> service.upsert(normalAdmin, userId, new AdminStaffGradeUpsertRequest(
                null, null, null, null, null, null, 1000, "KRW", null, null)))
                .isInstanceOf(BusinessException.class);
    }
}
