package com.careertuner.privacy.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class PrivacyBlockSoftDeleteMapperContractTest {

    private static final Path SCHEMA = Path.of("src/main/resources/db/schema.sql");
    private static final Path PRIVACY_MAPPER = Path.of("src/main/resources/mapper/privacy/PrivacyMapper.xml");
    private static final Path COMMUNITY_MAPPER = Path.of("src/main/resources/mapper/community/CommunityPostMapper.xml");
    private static final Path PATCH = Path.of("src/main/resources/db/patches/20260712_privacy_block_soft_delete.sql");

    @Test
    void allPersonalBlockRelationsUseSoftDeleteAndRestoreTheUniqueRow() throws Exception {
        String schema = Files.readString(SCHEMA);
        String mapper = Files.readString(PRIVACY_MAPPER);
        String communityMapper = Files.readString(COMMUNITY_MAPPER);

        assertThat(schema)
                .contains("CREATE TABLE IF NOT EXISTS user_block")
                .contains("CREATE TABLE IF NOT EXISTS user_ip_block")
                .contains("CREATE TABLE IF NOT EXISTS conversation_block")
                .contains("차단 해제 시각. 재차단하면 NULL로 복원")
                .contains("IP 차단 해제 시각. 재등록하면 NULL로 복원")
                .contains("대화방 차단 해제 시각. 재차단하면 NULL로 복원");
        assertThat(mapper)
                .contains("<update id=\"softDeleteBlock\">")
                .contains("<update id=\"softDeleteIpBlock\">")
                .contains("<update id=\"softDeleteIpBlocksBySource\">")
                .contains("<update id=\"softDeleteConversationBlock\">")
                .contains("SET deleted_at = COALESCE(deleted_at, NOW())")
                .contains("id = LAST_INSERT_ID(id)")
                .contains("deleted_at = NULL")
                .contains("b.deleted_at IS NULL")
                .contains("cb.deleted_at IS NULL")
                .doesNotContain("DELETE FROM user_block")
                .doesNotContain("DELETE FROM user_ip_block")
                .doesNotContain("DELETE FROM conversation_block");
        assertThat(communityMapper).contains("AND ub.deleted_at IS NULL");
    }

    @Test
    void migrationIsIdempotentAndVerifiesAllThreeColumns() throws Exception {
        String patch = Files.readString(PATCH);

        assertThat(patch)
                .contains("information_schema.COLUMNS")
                .contains("TABLE_NAME = 'user_block'")
                .contains("TABLE_NAME = 'user_ip_block'")
                .contains("TABLE_NAME = 'conversation_block'")
                .contains("COLUMN_NAME = 'deleted_at'")
                .contains("CHECK (guard_ok = 1)")
                .contains("@ct_privacy_block_soft_delete_columns = 3");
    }
}
