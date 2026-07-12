package com.careertuner.user.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class UserAccountMapperXmlTest {

    @Test
    void socialUnlinkLocksStableUserRowBeforeCheckingLoginMethods() throws Exception {
        String xml = Files.readString(Path.of(
                "src/main/resources/mapper/user/UserAccountMapper.xml"));
        int start = xml.indexOf("<select id=\"findByIdForUpdate\"");
        int end = xml.indexOf("</select>", start);

        assertThat(start).isGreaterThanOrEqualTo(0);
        assertThat(end).isGreaterThan(start);
        assertThat(xml.substring(start, end))
                .contains("FROM users")
                .contains("id = #{id}")
                .contains("FOR UPDATE");
    }

    @Test
    void socialProviderUniquenessIsPresentInSchemaAndIdempotentPatch() throws Exception {
        String schema = Files.readString(Path.of("src/main/resources/db/schema.sql"));
        String patch = Files.readString(Path.of(
                "src/main/resources/db/patches/20260711_user_social_provider_unique.sql"));

        assertThat(schema).contains(
                "UNIQUE KEY uk_user_social_user_provider (user_id, provider)");
        assertThat(patch)
                .contains("DELETE duplicate_social")
                .contains("MIN(id) AS keep_id")
                .contains("GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX)")
                .contains("user_id,provider")
                .contains("ADD UNIQUE INDEX uk_user_social_user_provider");
    }

    @Test
    void ownAccountDeletionKeepsRowsAndAnonymizesLoginIdentifiers() throws Exception {
        String xml = Files.readString(Path.of(
                "src/main/resources/mapper/user/UserAccountMapper.xml"));
        int start = xml.indexOf("<update id=\"anonymizeAndSoftDeleteOwnAccount\"");
        int end = xml.indexOf("</update>", start);

        assertThat(start).isGreaterThanOrEqualTo(0);
        assertThat(end).isGreaterThan(start);
        assertThat(xml.substring(start, end))
                .contains("UPDATE users")
                .contains("status = 'DELETED'")
                .contains("email = NULL")
                .contains("login_id = NULL")
                .contains("phone = NULL")
                .contains("password = NULL")
                .contains("password_enabled = 0")
                .contains("name = '탈퇴한 사용자'")
                .contains("deleted_at = COALESCE(deleted_at, NOW())")
                .contains("status &lt;&gt; 'DELETED'")
                .doesNotContain("DELETE FROM users");
    }

    @Test
    void ownAccountDeletionRemovesDeliveryCredentialsButPreservesContentRows() throws Exception {
        String xml = Files.readString(Path.of(
                "src/main/resources/mapper/user/UserAccountMapper.xml"));

        assertThat(xml)
                .contains("<delete id=\"deleteAllSocialLinks\">")
                .contains("DELETE FROM user_social WHERE user_id = #{userId}")
                .contains("<delete id=\"deleteAllPushSubscriptions\">")
                .contains("DELETE FROM push_subscription WHERE user_id = #{userId}")
                .contains("<update id=\"expireAllEmailVerifications\">")
                .contains("<delete id=\"deleteAllSmsOtpCodes\">")
                .contains("<update id=\"hideAndAnonymizeNicknameProfiles\">")
                .contains("avatar_file_id = NULL")
                .contains("status = 'HIDDEN'")
                .contains("<update id=\"anonymizeChatProfiles\">")
                .contains("avatar_url = NULL")
                .contains("<update id=\"deactivateConversationMemberships\">")
                .contains("status = 'LEFT'")
                .doesNotContain("DELETE FROM community_post")
                .doesNotContain("DELETE FROM community_comment")
                .doesNotContain("DELETE FROM collaboration_message");
    }

    @Test
    void existingDeletedAccountPatchIsRerunnableAndKeepsDomainForeignKeys() throws Exception {
        String patch = Files.readString(Path.of(
                "src/main/resources/db/patches/20260712_account_deletion_privacy.sql"));

        assertThat(patch)
                .contains("WHERE status = 'DELETED'")
                .contains("name <> '탈퇴한 사용자'")
                .contains("np.status <> 'HIDDEN'")
                .contains("cmp.nickname_profile_id IS NOT NULL")
                .contains("DELETE us")
                .contains("DELETE ps")
                .contains("UPDATE refresh_token rt")
                .contains("rt.revoked = 1")
                .contains("np.status = 'HIDDEN'")
                .contains("cm.status = 'LEFT'")
                .doesNotContain("DELETE FROM users")
                .doesNotContain("DELETE FROM community_post")
                .doesNotContain("DELETE FROM collaboration_message");
        assertThat(patch).doesNotContain("WHERE status = 'ACTIVE'");

        String schema = Files.readString(Path.of("src/main/resources/db/schema.sql"));
        assertThat(schema)
                .contains("DELETED는 행/FK를 보존하고 공개 식별자·로그인 수단을 tombstone 처리")
                .contains("탈퇴 시 행은 보존하고 식별정보·인증수단은 비식별화");
    }
}
