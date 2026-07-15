package com.careertuner.profile.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ProfileVersionMapperXmlTest {

    private static final Path SCHEMA = Path.of("src/main/resources/db/schema.sql");
    private static final Path PROFILE_MAPPER = Path.of("src/main/resources/mapper/profile/ProfileMapper.xml");
    private static final Path AI_MAPPER = Path.of("src/main/resources/mapper/profile/ProfileAiAnalysisMapper.xml");
    private static final Path ACCOUNT_MAPPER = Path.of("src/main/resources/mapper/user/UserAccountMapper.xml");
    private static final Path PATCH = Path.of("src/main/resources/db/patches/20260712_user_profile_version.sql");

    @Test
    void schemaAndMapperPreserveVersionedAnalysisInput() throws Exception {
        String schema = Files.readString(SCHEMA);
        String profileMapper = Files.readString(PROFILE_MAPPER);
        String aiMapper = Files.readString(AI_MAPPER);

        assertThat(schema)
                .contains("CREATE TABLE IF NOT EXISTS user_profile_version")
                .contains("UNIQUE KEY uk_user_profile_version (user_id, version_no)")
                .contains("profile_version_id BIGINT NULL")
                .contains("CONSTRAINT fk_profile_ai_profile_version")
                .contains("idx_user_profile_version_deleted");
        assertThat(profileMapper)
                .contains("<select id=\"findByUserIdForUpdate\"")
                .contains("FOR UPDATE")
                .contains("<update id=\"initialize\"")
                .contains("version_no = user_profile.version_no + 1")
                .contains("<insert id=\"insertVersionFromCurrent\">")
                .contains("<insert id=\"insertVersionSnapshot\">")
                .contains("#{profile.versionNo}")
                .contains("<select id=\"findVersionByNo\"")
                .contains("version_no = #{versionNo}")
                .contains("INSERT IGNORE INTO user_profile_version")
                .contains("ORDER BY version_no DESC")
                .contains("deleted_at IS NULL");
        assertThat(aiMapper)
                .contains("profile_version_id = VALUES(profile_version_id)")
                .contains("AND deleted_at IS NULL");
    }

    @Test
    void migrationIsRerunnableAndDeletionScrubsEveryPrivateProfileCopy() throws Exception {
        String patch = Files.readString(PATCH);
        String accountMapper = Files.readString(ACCOUNT_MAPPER);

        assertThat(patch)
                .contains("CREATE TABLE IF NOT EXISTS user_profile_version")
                .contains("INSERT IGNORE INTO user_profile_version")
                .contains("@ct_profile_version_fk_exists")
                .contains("CHECK (guard_ok = 1)")
                .contains("profile.resume_text = NULL")
                .contains("version.resume_text = NULL")
                .contains("analysis.summary = NULL")
                .contains("detail.education_json = NULL");
        assertThat(accountMapper)
                .contains("<update id=\"scrubUserProfile\">")
                .contains("<update id=\"scrubUserProfileVersions\">")
                .contains("<update id=\"scrubProfileAiAnalyses\">")
                .contains("<update id=\"scrubResumeDetail\">")
                .contains("deleted_at = COALESCE(deleted_at, NOW())");
    }
}
