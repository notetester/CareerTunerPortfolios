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
        assertThat(xml).contains("<select id=\"findCorrectionForUpdate\"");
        assertThat(xml).contains("FOR UPDATE");
    }

    @Test
    void creditAdjustmentLocksActiveBalanceAndGuardsOverflow() throws IOException {
        String xml = Files.readString(Path.of(
                "src/main/resources/mapper/admin/credit/AdminCreditMapper.xml"));

        assertThat(xml).contains("FOR UPDATE");
        assertThat(xml).contains("status &lt;&gt; 'DELETED'");
        assertThat(xml).contains("deleted_at IS NULL");
        assertThat(xml).contains("credit &lt;= 2147483647 - #{amount}");
        assertThat(xml).contains("credit &gt;= #{amount}");
        assertThat(xml).contains("type = 'ADMIN_ADJUST'");
        assertThat(xml).contains("request_key = #{requestKey}");
        assertThat(xml).contains("<insert id=\"insertAdminAdjustment\"");
        assertThat(xml).contains("#{reason}, #{requestKey}");

        String sharedCreditXml = Files.readString(Path.of(
                "src/main/resources/mapper/credit/CreditMapper.xml"));
        assertThat(sharedCreditXml).doesNotContain("request_key");
    }

    @Test
    void correctionMemoSchemaAndPatchStayAligned() throws IOException {
        String schema = Files.readString(Path.of("src/main/resources/db/schema.sql"));
        String patch = Files.readString(Path.of(
                "src/main/resources/db/patches/20260705_e_correction_admin_memo.sql"));

        assertThat(schema).contains("admin_memo          VARCHAR(2000) NULL");
        assertThat(patch).contains("ADD COLUMN admin_memo VARCHAR(2000) NULL");
    }

    @Test
    void creditRequestKeySchemaAndPatchStayAligned() throws IOException {
        String schema = Files.readString(Path.of("src/main/resources/db/schema.sql"));
        String patch = Files.readString(Path.of(
                "src/main/resources/db/patches/20260711_e_admin_credit_idempotency.sql"));

        assertThat(schema).contains("request_key     VARCHAR(120) NULL");
        assertThat(schema).contains("uq_credit_transaction_user_type_request");
        assertThat(schema).contains("feature_type    VARCHAR(80) NULL");
        assertThat(patch).contains("CHARACTER_MAXIMUM_LENGTH <> 80");
        assertThat(patch).contains("ADD COLUMN request_key VARCHAR(120) NULL");
        assertThat(patch).contains("CHARACTER_MAXIMUM_LENGTH <> 120");
        assertThat(patch).contains("IS_NULLABLE <> 'YES'");
        assertThat(patch).contains("MIN(NON_UNIQUE) = 0");
        assertThat(patch).contains("GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX)");
        assertThat(patch).contains("DROP INDEX uq_credit_transaction_user_type_request");
        assertThat(patch).contains("CREATE UNIQUE INDEX uq_credit_transaction_user_type_request");
    }
}
