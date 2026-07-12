package com.careertuner.community.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class CommunityRelationshipSoftDeleteContractTest {

    @Test
    void userRelationshipsAndContentLinksUseActiveRowsAndSoftDelete() throws Exception {
        String collaboration = read("src/main/resources/mapper/collaboration/CollaborationMapper.xml");
        String notification = read("src/main/resources/mapper/notification/NotificationMapper.xml");
        String reaction = read("src/main/resources/mapper/community/ReactionMapper.xml");
        String subscription = read("src/main/resources/mapper/community/CommunitySubscriptionMapper.xml");
        String scrap = read("src/main/resources/mapper/community/PostScrapMapper.xml");
        String tag = read("src/main/resources/mapper/community/CommunityTagMapper.xml");
        String post = read("src/main/resources/mapper/community/CommunityPostMapper.xml");

        assertThat(collaboration)
                .doesNotContain("DELETE FROM collaboration_friendship")
                .doesNotContain("DELETE FROM collaboration_conversation_permission")
                .doesNotContain("DELETE FROM collaboration_conversation_invite_allow")
                .contains("deleted_at = COALESCE(deleted_at, NOW())")
                .contains("deleted_at = NULL");
        assertThat(notification)
                .doesNotContain("DELETE FROM notification")
                .contains("n.deleted_at IS NULL")
                .contains("SET deleted_at = COALESCE(deleted_at, NOW())");
        assertThat(reaction)
                .doesNotContain("DELETE FROM post_reaction")
                .doesNotContain("DELETE FROM comment_reaction")
                .contains("pr.deleted_at IS NULL")
                .contains("cr.deleted_at IS NULL")
                .contains("deleted_at = NULL");
        assertThat(subscription)
                .doesNotContain("DELETE FROM post_subscription")
                .doesNotContain("DELETE FROM comment_subscription")
                .contains("deleted_at IS NULL")
                .contains("ON DUPLICATE KEY UPDATE deleted_at = NULL");
        assertThat(scrap)
                .doesNotContain("DELETE FROM post_scrap")
                .contains("ps.deleted_at IS NULL");
        assertThat(tag)
                .doesNotContain("DELETE FROM community_post_tag")
                .contains("mapping.deleted_at IS NULL")
                .contains("deleted_at = NULL");
        assertThat(post)
                .doesNotContain("DELETE FROM community_interview_review")
                .contains("AND deleted_at IS NULL");
    }

    @Test
    void canonicalSchemaAndPatchCoverEveryConvertedTable() throws Exception {
        String schema = read("src/main/resources/db/schema.sql");
        String patch = read("src/main/resources/db/patches/20260712_f_relationship_content_soft_delete.sql");
        for (String table : new String[] {
                "collaboration_friendship", "collaboration_conversation_permission",
                "collaboration_conversation_invite_allow", "community_interview_review",
                "post_reaction", "comment_reaction", "community_post_tag", "notification",
                "post_scrap", "post_subscription", "comment_subscription"
        }) {
            assertThat(schema).contains("CREATE TABLE IF NOT EXISTS " + table);
            assertThat(patch).contains("table_name = '" + table + "'");
        }
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}
