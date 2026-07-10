package com.careertuner.community.moderation;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.PreparedStatement;
import java.sql.Statement;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.community.moderation.mapper.PostAiResultMapper;
import com.careertuner.community.moderation.domain.AiTaskType;
import com.careertuner.community.moderation.service.AdminModerationService;
import com.careertuner.community.moderation.service.ModerationSettingService;

@SpringBootTest
@Transactional
class ModerationReviewQueueRoundTripTest {

    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    PostAiResultMapper aiResultMapper;
    @Autowired
    AdminModerationService adminModerationService;
    @Autowired
    ModerationSettingService settingService;

    @Test
    void publishedBoundaryResultLeavesQueuePermanentlyAfterKeepDecision() {
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM users ORDER BY id LIMIT 1", Long.class);
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO community_post (user_id, category, title, content, status, is_anonymous)
                    VALUES (?, 'FREE', '검토 큐 DB 왕복 테스트', '<p>경계 판정 본문</p>', 'PUBLISHED', 0)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, userId);
            return statement;
        }, keyHolder);
        Long postId = keyHolder.getKey().longValue();

        double confidence = Math.max(0.01, settingService.getHideThreshold() - 0.01);
        String resultJson = "{\"toxic\":true,\"category\":\"abuse\",\"confidence\":" + confidence + "}";
        jdbcTemplate.update("""
                INSERT INTO post_ai_result
                    (post_id, task_type, status, result_json, model, attempt_count, completed_at)
                VALUES (?, 'MODERATION', 'COMPLETED', CAST(? AS JSON), 'round-trip-test', 1, NOW())
                """, postId, resultJson);

        assertThat(aiResultMapper.findReviewQueue(settingService.getHideThreshold(), 0, 100))
                .extracting("postId")
                .contains(postId);

        adminModerationService.decideReviewQueue(userId, postId, "KEEP");
        adminModerationService.decideReviewQueue(userId, postId, "KEEP");

        assertThat(aiResultMapper.findReviewAction(postId)).isEqualTo("KEEP");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT reviewed_by FROM post_ai_result WHERE post_id = ? AND task_type = 'MODERATION'",
                Long.class, postId)).isEqualTo(userId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT reviewed_at IS NOT NULL FROM post_ai_result WHERE post_id = ? AND task_type = 'MODERATION'",
                Boolean.class, postId)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM community_post WHERE id = ?", String.class, postId)).isEqualTo("PUBLISHED");
        assertThat(aiResultMapper.findReviewQueue(settingService.getHideThreshold(), 0, 100))
                .extracting("postId")
                .doesNotContain(postId);

        // 같은 결과에는 다시 나오지 않되, 재검열은 새 판단이므로 기존 수동 결정을 초기화한다.
        aiResultMapper.upsertPending(postId, AiTaskType.MODERATION);
        assertThat(aiResultMapper.findReviewAction(postId)).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT reviewed_by IS NULL FROM post_ai_result WHERE post_id = ? AND task_type = 'MODERATION'",
                Boolean.class, postId)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT reviewed_at IS NULL FROM post_ai_result WHERE post_id = ? AND task_type = 'MODERATION'",
                Boolean.class, postId)).isTrue();
        aiResultMapper.complete(postId, AiTaskType.MODERATION, resultJson, "round-trip-test-v2");
        assertThat(aiResultMapper.findReviewQueue(settingService.getHideThreshold(), 0, 100))
                .extracting("postId")
                .contains(postId);
    }
}
