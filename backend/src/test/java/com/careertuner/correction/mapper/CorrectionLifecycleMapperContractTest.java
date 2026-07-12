package com.careertuner.correction.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.junit.jupiter.api.Test;

class CorrectionLifecycleMapperContractTest {

    @Test
    void userReadsAndDeleteRespectCorrectionTombstoneAndApplicationLifecycle() {
        String mapper = normalized(resource("mapper/correction/CorrectionMapper.xml"));
        assertThat(mapper)
                .contains("source_snapshot")
                .contains("cr.deleted_at IS NULL")
                .contains("active_case.deleted_at IS NULL")
                .contains("<update id=\"softDelete\">")
                .contains("status = 'DELETED'")
                .doesNotContain("DELETE FROM correction_request");
    }

    @Test
    void accountDeletionScrubsCorrectionPersonalContent() {
        String mapper = normalized(resource("mapper/user/UserAccountMapper.xml"));
        assertThat(mapper)
                .contains("<update id=\"scrubCorrectionRequests\">")
                .contains("original_text = ''")
                .contains("improved_text = NULL")
                .contains("source_snapshot = NULL")
                .contains("admin_memo = NULL")
                .contains("deleted_at = COALESCE(deleted_at, NOW())");
    }

    @Test
    void migrationAddsProvenanceAndSoftDeleteIdempotently() {
        String patch = normalized(resource("db/patches/20260712_e_correction_context_lifecycle.sql"));
        assertThat(patch)
                .contains("information_schema.columns")
                .contains("column_name = 'source_snapshot'")
                .contains("column_name = 'deleted_at'")
                .contains("idx_correction_request_active")
                .contains("account.status = 'DELETED'");
    }

    @Test
    void adminSurfacesExcludeDeletedUsersCasesAndCorrections() {
        String mapper = normalized(resource("mapper/admin/correction/AdminCorrectionMapper.xml"));
        assertThat(mapper)
                .contains("cr.deleted_at IS NULL")
                .contains("u.status != 'DELETED'")
                .contains("u.deleted_at IS NULL")
                .contains("ac.deleted_at IS NULL")
                .contains("cr.source_snapshot");
    }

    private static String resource(String path) {
        try (InputStream input = Objects.requireNonNull(
                Thread.currentThread().getContextClassLoader().getResourceAsStream(path), path)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String normalized(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }
}
