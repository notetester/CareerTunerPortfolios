package com.careertuner.dashboard.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.Test;

class DashboardSoftDeleteContractTest {

    @Test
    void dashboardTodoDeleteCreatesTombstoneAndAllMutationsRespectIt() {
        String xml = normalize(resource("mapper/dashboard/DashboardMapper.xml"));

        assertThat(xml)
                .doesNotContain("DELETE FROM dashboard_todo")
                .contains("<update id=\"deleteTodo\"> UPDATE dashboard_todo SET deleted_at = COALESCE(deleted_at, NOW())")
                .contains("FROM dashboard_todo WHERE user_id = #{userId} AND deleted_at IS NULL")
                .contains("AND derived_key IS NULL AND deleted_at IS NULL")
                .contains("completed_at = VALUES(completed_at), deleted_at = NULL");
    }

    @Test
    void cAdminReadModelsExcludeDeletedUsersAndApplicationData() {
        for (String mapper : List.of(
                "mapper/admin/home/AdminHomeMapper.xml",
                "mapper/admin/dashboard/AdminDashboardMapper.xml",
                "mapper/admin/analytics/AdminAnalyticsMapper.xml",
                "mapper/admin/fitanalysis/AdminFitAnalysisMapper.xml")) {
            String xml = normalize(resource(mapper));
            assertThat(xml).as(mapper)
                    .contains("deleted_at IS NULL")
                    .contains("users.status != 'DELETED'")
                    .contains("users.deleted_at IS NULL");
        }

        assertThat(normalize(resource("mapper/admin/dashboard/AdminDashboardMapper.xml")))
                .contains("session.deleted_at IS NULL")
                .contains("ac.deleted_at IS NULL");
        assertThat(normalize(resource("mapper/admin/fitanalysis/AdminFitAnalysisMapper.xml")))
                .contains("WHERE fa.id = #{id} AND ac.deleted_at IS NULL")
                .contains("UPDATE fit_analysis_gate_result")
                .contains("INNER JOIN users active_user ON active_user.id = active_case.user_id");
        assertThat(normalize(resource("mapper/admin/analytics/AdminAnalyticsMapper.xml")))
                .contains("UPDATE analysis_quality_flag")
                .contains("INNER JOIN users active_user ON active_user.id = active_case.user_id")
                .contains("INNER JOIN users target_user ON target_user.id = run.user_id");
    }

    @Test
    void migrationIsIdempotentAndAddsActiveLookupIndex() {
        String patch = normalize(resource("db/patches/20260712_c_dashboard_todo_soft_delete.sql"));

        assertThat(patch)
                .contains("information_schema.columns")
                .contains("column_name = 'deleted_at'")
                .contains("ADD COLUMN deleted_at DATETIME NULL")
                .contains("information_schema.statistics")
                .contains("idx_dashboard_todo_active (user_id, deleted_at, created_at)");
    }

    private static String resource(String path) {
        try (InputStream input = Objects.requireNonNull(
                Thread.currentThread().getContextClassLoader().getResourceAsStream(path),
                "Missing resource: " + path)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read resource: " + path, exception);
        }
    }

    private static String normalize(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }
}
