package com.careertuner.admin.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class AdminCorrectionCreditMapperXmlTest {

    @Test
    void correctionMapperSeparatesSuccessfulRequestsAndFailedUsageLogs() throws IOException {
        String xml = Files.readString(Path.of(
                "src/main/resources/mapper/admin/correction/AdminCorrectionMapper.xml"));

        assertThat(xml).contains("FROM correction_request cr");
        assertThat(xml).contains("FROM ai_usage_log aul");
        assertThat(xml).contains("'CORRECTION_SELF_INTRO'");
        assertThat(xml).contains("'CORRECTION_INTERVIEW_ANSWER'");
        assertThat(xml).contains("'CORRECTION_RESUME'");
        assertThat(xml).contains("'CORRECTION_PORTFOLIO'");
        assertThat(xml).contains("SET admin_memo = #{adminMemo}");
    }

    @Test
    void creditAdjustmentLocksBalanceAndGuardsOverflow() throws IOException {
        String xml = Files.readString(Path.of(
                "src/main/resources/mapper/admin/credit/AdminCreditMapper.xml"));

        assertThat(xml).contains("FOR UPDATE");
        assertThat(xml).contains("credit &lt;= 2147483647 - #{amount}");
        assertThat(xml).contains("type = 'ADMIN_ADJUST'");
    }

    @Test
    void correctionMemoSchemaAndPatchStayAligned() throws IOException {
        String schema = Files.readString(Path.of("src/main/resources/db/schema.sql"));
        String patch = Files.readString(Path.of(
                "src/main/resources/db/patches/20260705_e_correction_admin_memo.sql"));

        assertThat(schema).contains("admin_memo          VARCHAR(2000) NULL");
        assertThat(patch).contains("ADD COLUMN admin_memo VARCHAR(2000) NULL");
    }
}
