package com.careertuner.community.moderation.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import com.careertuner.community.domain.CommunityPost;
import com.careertuner.community.domain.PostStatus;
import com.careertuner.community.mapper.CommunityPostMapper;
import com.careertuner.community.moderation.client.OllamaClient;
import com.careertuner.community.moderation.config.OllamaProperties;
import com.careertuner.community.moderation.domain.AiTaskType;
import com.careertuner.community.moderation.dto.ModerationResult;
import com.careertuner.community.moderation.mapper.PostAiResultMapper;

import tools.jackson.databind.ObjectMapper;

/**
 * 게시글 AI 검열 서비스.
 *
 * - judge(): 순수 판정만 (DB 접근 없음). 테스트 엔드포인트용.
 * - moderate(): 전체 파이프라인 (UPSERT → 판정 → 결과 저장 → 숨김).
 *
 * @Transactional 금지 — Ollama 호출(최대 30초)을 트랜잭션으로 묶으면
 * DB 커넥션을 오래 점유하여 커넥션 풀 고갈 위험.
 */
@Service
public class PostModerationService {

    private static final Logger log = LoggerFactory.getLogger(PostModerationService.class);

    private static final int MAX_TEXT_LENGTH = 8000;

    /** Ollama structured output용 JSON 스키마 (gemma4.md 검증 완료 형식) */
    private static final Map<String, Object> MODERATION_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "toxic", Map.of("type", "boolean"),
                    "category", Map.of("type", "string",
                            "enum", List.of("normal", "abuse", "spam", "ad")),
                    "confidence", Map.of("type", "number")
            ),
            "required", List.of("toxic", "category", "confidence")
    );

    private final OllamaClient ollamaClient;
    private final OllamaProperties ollamaProperties;
    private final PostAiResultMapper aiResultMapper;
    private final CommunityPostMapper postMapper;
    private final ObjectMapper objectMapper;

    /** 시스템 프롬프트 (classpath 파일에서 1회 로드) */
    private final String systemPrompt;

    /** toxic 판정 시 숨김 처리 임계값 */
    private final double hideThreshold;

    public PostModerationService(
            OllamaClient ollamaClient,
            OllamaProperties ollamaProperties,
            PostAiResultMapper aiResultMapper,
            CommunityPostMapper postMapper,
            ObjectMapper objectMapper,
            @Value("classpath:prompts/moderation-system.txt") Resource promptResource,
            @Value("${ai.moderation.hide-threshold:0.8}") double hideThreshold
    ) {
        this.ollamaClient = ollamaClient;
        this.ollamaProperties = ollamaProperties;
        this.aiResultMapper = aiResultMapper;
        this.postMapper = postMapper;
        this.objectMapper = objectMapper;
        this.hideThreshold = hideThreshold;

        // 시스템 프롬프트 파일을 1회 읽어 필드에 보관
        try {
            this.systemPrompt = promptResource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("moderation-system.txt 로드 실패", e);
        }
    }

    /**
     * 순수 판정 — DB 접근 없음. 테스트 엔드포인트가 직접 호출한다.
     */
    public ModerationResult judge(String title, String content) {
        String text = "제목: " + title + "\n본문: " + content;
        if (text.length() > MAX_TEXT_LENGTH) {
            text = text.substring(0, MAX_TEXT_LENGTH);
        }

        String json = ollamaClient.chat(systemPrompt, text, MODERATION_SCHEMA);
        return parseResult(json);
    }

    /**
     * 전체 검열 파이프라인 — 이벤트 리스너가 비동기로 호출한다.
     * @Transactional 금지 (공통 금지사항)
     */
    public void moderate(Long postId) {
        try {
            // 1. UPSERT: PENDING 상태로 기록 (재시도 시 attempt_count 증가)
            aiResultMapper.upsertPending(postId, AiTaskType.MODERATION);

            // 2. 게시글 조회 — null이거나 DELETED면 검열 불필요
            CommunityPost post = postMapper.findById(postId);
            if (post == null || PostStatus.DELETED.name().equals(post.getStatus())) {
                log.info("검열 스킵: postId={} (삭제됨 또는 존재하지 않음)", postId);
                return;
            }

            // 3. 판정
            ModerationResult result = judge(post.getTitle(), post.getContent());

            // 4. 결과 저장 (COMPLETED)
            String resultJson = objectMapper.writeValueAsString(result);
            aiResultMapper.complete(postId, AiTaskType.MODERATION,
                    resultJson, ollamaProperties.getModel());

            // 5. 숨김 처리 (toxic + 높은 confidence)
            if (result.toxic() && result.confidence() >= hideThreshold) {
                int updated = postMapper.hideIfPublished(postId);
                if (updated > 0) {
                    log.warn("게시글 숨김 처리: postId={}, category={}, confidence={}",
                            postId, result.category(), result.confidence());
                }
            }

            log.info("검열 완료: postId={}, toxic={}, category={}, confidence={}",
                    postId, result.toxic(), result.category(), result.confidence());

        } catch (Exception e) {
            // 6. 실패 기록 — 예외를 다시 던지지 않는다
            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.length() > 500) {
                errorMsg = errorMsg.substring(0, 500);
            }
            aiResultMapper.fail(postId, AiTaskType.MODERATION, errorMsg);
            log.error("검열 실패: postId={}", postId, e);
        }
    }

    private ModerationResult parseResult(String json) {
        try {
            return objectMapper.readValue(json, ModerationResult.class);
        } catch (Exception e) {
            throw new IllegalStateException("검열 결과 JSON 파싱 실패: " + json, e);
        }
    }
}
