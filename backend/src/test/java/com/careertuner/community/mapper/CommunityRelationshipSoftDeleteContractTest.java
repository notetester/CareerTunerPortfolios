package com.careertuner.community.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import com.careertuner.community.domain.CommunityAuthorVisibility;

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

    @Test
    void privacyFilterIsResolvedBeforePagingTotalCategoryAndHotLimits() throws Exception {
        String post = read("src/main/resources/mapper/community/CommunityPostMapper.xml");
        String filter = section(post, "<sql id=\"blockedAuthorFilter\"", "</sql>");
        assertThat(filter)
                .contains("blockedNamedAuthorIdsJson")
                .contains("blockedAnonymousAuthorIdsJson")
                .contains("JSON_TABLE(")
                .contains("'$[*]' COLUMNS (author_id BIGINT PATH '$')")
                .contains("cp.is_anonymous = 0")
                .contains("cp.is_anonymous = 1");
        assertThat(occurrences(filter, "#{blockedNamedAuthorIdsJson}")).isEqualTo(1);
        assertThat(occurrences(filter, "#{blockedAnonymousAuthorIdsJson}")).isEqualTo(1);
        assertThat(post)
                .doesNotContain("viewerBlockFilter")
                .doesNotContain("countPublishedByCategoryAndAuthor")
                .doesNotContain("JSON_EXTRACT(ub.flags_json");

        String authors = section(post, "<select id=\"findAuthorSurfaces\"", "</select>");
        assertThat(authors)
                .contains("SELECT DISTINCT cp.user_id AS userId")
                .contains("cp.is_anonymous AS anonymous")
                .contains("cp.status = #{status}");

        assertFilterBefore(post, "findAll", "LIMIT #{limit} OFFSET #{offset}");
        assertThat(section(post, "<select id=\"countVisible\"", "</select>"))
                .contains("<include refid=\"blockedAuthorFilter\"/>");
        assertFilterBefore(post, "countPublishedByCategory", "GROUP BY cp.category");
        assertFilterBefore(post, "findPersonalizedCandidates", "LIMIT #{limit}");
        assertFilterBefore(post, "findFreshPopular", "LIMIT #{limit}");
        assertFilterBefore(post, "findHotPosts", "LIMIT #{limit}");
        assertThat(section(post, "<select id=\"findPersonalizedCandidates\"", "</select>"))
                .contains("cp.is_anonymous AS anonymous");
        assertThat(section(post, "<select id=\"findFreshPopular\"", "</select>"))
                .contains("cp.is_anonymous AS anonymous");
    }

    @Test
    void jsonPrivacyFilterKeepsPlaceholderCountFixedAsCandidateCountGrows() throws Exception {
        String resource = "src/main/resources/mapper/community/CommunityPostMapper.xml";
        Configuration configuration = new Configuration();
        try (var input = Files.newInputStream(Path.of(resource))) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }

        Set<Long> namedAuthors = authorIds(1, 1_201);
        Set<Long> anonymousAuthors = authorIds(10_001, 1_001);
        BoundSql singleAuthor = findAllBoundSql(configuration, "[1]", "[10001]");
        CommunityAuthorVisibility largeVisibility = new CommunityAuthorVisibility(namedAuthors, anonymousAuthors);
        BoundSql largeAuthorSet = findAllBoundSql(configuration,
                largeVisibility.blockedNamedAuthorIdsJson(),
                largeVisibility.blockedAnonymousAuthorIdsJson());

        assertThat(singleAuthor.getParameterMappings()).hasSize(5);
        assertThat(largeAuthorSet.getParameterMappings()).hasSize(5);
        assertThat(occurrences(largeAuthorSet.getSql(), "JSON_TABLE(")).isEqualTo(2);
        assertThat(largeVisibility.blockedNamedAuthorIdsJson()).contains("1", "1201");
        assertThat(largeVisibility.blockedAnonymousAuthorIdsJson()).contains("10001", "11001");
    }

    private static BoundSql findAllBoundSql(Configuration configuration, String namedJson, String anonymousJson) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("category", null);
        parameters.put("status", "PUBLISHED");
        parameters.put("sort", "latest");
        parameters.put("keyword", null);
        parameters.put("offset", 0);
        parameters.put("limit", 20);
        parameters.put("blockedNamedAuthorIdsJson", namedJson);
        parameters.put("blockedAnonymousAuthorIdsJson", anonymousJson);
        return configuration.getMappedStatement(
                "com.careertuner.community.mapper.CommunityPostMapper.findAll").getBoundSql(parameters);
    }

    private static Set<Long> authorIds(long first, int count) {
        Set<Long> ids = new LinkedHashSet<>();
        for (long id = first; id < first + count; id++) {
            ids.add(id);
        }
        return ids;
    }

    private static int occurrences(String source, String value) {
        return (source.length() - source.replace(value, "").length()) / value.length();
    }

    private static void assertFilterBefore(String source, String selectId, String boundary) {
        String select = section(source, "<select id=\"" + selectId + "\"", "</select>");
        assertThat(select.indexOf("<include refid=\"blockedAuthorFilter\"/>"))
                .as(selectId + " privacy filter")
                .isGreaterThanOrEqualTo(0)
                .isLessThan(select.indexOf(boundary));
    }

    private static String section(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start);
        assertThat(start).as(startMarker).isGreaterThanOrEqualTo(0);
        assertThat(end).as(endMarker).isGreaterThan(start);
        return source.substring(start, end);
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}
