package com.careertuner.admin.permission;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

class AdminSoftDeleteMapperContractTest {

    private static final List<String> PROTECTED_TABLES = List.of(
            "notice", "faq", "community_guideline", "user_level_policy",
            "legal_document_version", "legal_clause", "admin_fit_analysis_memo",
            "admin_career_run_memo", "chatbot_conversation_memory", "advertisement",
            "interview_knowledge", "collaboration_conversation_ban",
            "admin_permission_group_item");

    @Test
    void protectedAdminEntitiesAreNeverPhysicallyDeletedByMappers() throws Exception {
        StringBuilder allMappers = new StringBuilder();
        try (var files = Files.walk(Path.of("src/main/resources/mapper"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".xml")).toList()) {
                allMappers.append(Files.readString(file)).append('\n');
            }
        }
        String sql = allMappers.toString().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        for (String table : PROTECTED_TABLES) {
            assertThat(sql).as(table + " 물리 삭제 금지").doesNotContain("delete from " + table);
        }
    }

    @Test
    void userFacingConsumersExcludeSoftDeletedRows() throws Exception {
        for (String mapper : List.of(
                "ai/ChatMemoryMapper.xml",
                "support/FaqMapper.xml",
                "support/ChatbotFaqMapper.xml",
                "support/NoticeMapper.xml",
                "community/guideline/GuidelineMapper.xml",
                "legal/LegalMapper.xml",
                "reward/RewardMapper.xml")) {
            String source = Files.readString(Path.of("src/main/resources/mapper", mapper));
            assertThat(source).as(mapper).contains("deleted_at IS NULL");
        }
    }

    @Test
    void deletedLevelPolicyCannotBeRecreatedOrOrphanCurrentUsers() throws Exception {
        String mapper = Files.readString(Path.of(
                "src/main/resources/mapper/admin/reward/AdminRewardMapper.xml"))
                .replaceAll("\\s+", " ");

        assertThat(mapper)
                .contains("<select id=\"countLevelByNumber\"")
                .contains("WHERE level = #{level}")
                .contains("WHERE user_level = #{level}")
                .doesNotContain("ON DUPLICATE KEY UPDATE", "deleted_at = NULL");
    }
}
