package com.careertuner.community.moderation.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import com.careertuner.community.domain.CommunityPost;
import com.careertuner.community.domain.PostStatus;
import com.careertuner.community.mapper.CommunityPostMapper;
import com.careertuner.community.moderation.client.OllamaClient;
import com.careertuner.community.moderation.config.OllamaProperties;
import com.careertuner.community.moderation.domain.AiTaskType;
import com.careertuner.community.moderation.domain.Strictness;
import com.careertuner.community.moderation.dto.ModerationResult;
import com.careertuner.community.moderation.mapper.PostAiResultMapper;
import com.careertuner.notification.domain.Notification;
import com.careertuner.notification.service.NotificationService;

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
    private final NotificationService notificationService;
    private final ModerationSettingService settingService;
    private final ObjectMapper objectMapper;

    /** 기본 시스템 프롬프트 (classpath 파일에서 1회 로드) */
    private final String baseSystemPrompt;

    /** 엄격도별 지침 텍스트 (classpath 파일에서 1회 로드) */
    private final Map<Strictness, String> strictnessTexts;

    public PostModerationService(
            OllamaClient ollamaClient,
            OllamaProperties ollamaProperties,
            PostAiResultMapper aiResultMapper,
            CommunityPostMapper postMapper,
            NotificationService notificationService,
            ModerationSettingService settingService,
            ObjectMapper objectMapper,
            ResourceLoader resourceLoader,
            @Value("classpath:prompts/moderation-system.txt") Resource promptResource
    ) {
        this.ollamaClient = ollamaClient;
        this.ollamaProperties = ollamaProperties;
        this.aiResultMapper = aiResultMapper;
        this.postMapper = postMapper;
        this.notificationService = notificationService;
        this.settingService = settingService;
        this.objectMapper = objectMapper;

        // 기본 프롬프트 로드
        try {
            this.baseSystemPrompt = promptResource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("moderation-system.txt 로드 실패", e);
        }

        // 엄격도별 지침 텍스트 로드
        this.strictnessTexts = new EnumMap<>(Strictness.class);
        for (Strictness s : Strictness.values()) {
            Resource res = resourceLoader.getResource(
                    "classpath:prompts/strictness/" + s.name() + ".txt");
            try {
                strictnessTexts.put(s, res.getContentAsString(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new UncheckedIOException(s.name() + ".txt 로드 실패", e);
            }
        }
    }

    /**
     * 현재 설정의 엄격도를 반영한 시스템 프롬프트를 조립한다.
     */
    private String buildSystemPrompt() {
        Strictness strictness = settingService.getStrictness();
        String strictnessText = strictnessTexts.get(strictness);
        return baseSystemPrompt + "\n\n[엄격도 지침]\n" + strictnessText;
    }

    /**
     * 순수 판정 — DB 접근 없음. 테스트 엔드포인트가 직접 호출한다.
     * 항상 "현재 설정"으로 판정한다.
     */
    public ModerationResult judge(String title, String content) {
        String text = "제목: " + title + "\n본문: " + content;
        if (text.length() > MAX_TEXT_LENGTH) {
            text = text.substring(0, MAX_TEXT_LENGTH);
        }

        String prompt = buildSystemPrompt();
        String json = ollamaClient.chat(prompt, text, MODERATION_SCHEMA);
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

            // 3. 현재 설정 스냅샷
            Strictness currentStrictness = settingService.getStrictness();
            double currentThreshold = settingService.getHideThreshold();

            // 4. 판정
            ModerationResult result = judge(post.getTitle(), post.getContent());

            // 5. 결과 저장 (COMPLETED) — applied 스냅샷 병합
            String resultJson = buildResultJson(result, currentStrictness, currentThreshold);
            aiResultMapper.complete(postId, AiTaskType.MODERATION,
                    resultJson, ollamaProperties.getModel());

            // 6. 숨김 처리 (toxic + 캐시된 threshold 기준)
            if (result.toxic() && result.confidence() >= currentThreshold) {
                int updated = postMapper.hideIfPublished(postId);
                if (updated > 0) {
                    log.warn("게시글 숨김 처리: postId={}, category={}, confidence={}",
                            postId, result.category(), result.confidence());
                    sendHiddenNotification(post);
                }
            }

            log.info("검열 완료: postId={}, toxic={}, category={}, confidence={}, strictness={}, threshold={}",
                    postId, result.toxic(), result.category(), result.confidence(),
                    currentStrictness, currentThreshold);

        } catch (Exception e) {
            // 7. 실패 기록 — 예외를 다시 던지지 않는다
            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.length() > 500) {
                errorMsg = errorMsg.substring(0, 500);
            }
            aiResultMapper.fail(postId, AiTaskType.MODERATION, errorMsg);
            log.error("검열 실패: postId={}", postId, e);
        }
    }

    /**
     * 모델 응답 JSON에 applied 스냅샷을 병합한다.
     */
    @SuppressWarnings("unchecked")
    private String buildResultJson(ModerationResult result,
                                   Strictness strictness, double hideThreshold) {
        try {
            // ModerationResult → Map으로 변환
            String raw = objectMapper.writeValueAsString(result);
            Map<String, Object> map = objectMapper.readValue(raw, LinkedHashMap.class);

            // applied 스냅샷 추가
            Map<String, Object> applied = new LinkedHashMap<>();
            applied.put("strictness", strictness.name());
            applied.put("hideThreshold", hideThreshold);
            map.put("applied", applied);

            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            log.warn("applied 병합 실패, 원본 JSON 사용", e);
            try {
                return objectMapper.writeValueAsString(result);
            } catch (Exception ex) {
                throw new IllegalStateException("JSON 직렬화 실패", ex);
            }
        }
    }

    /**
     * 검열 숨김 시 작성자에게 알림 발송.
     */
    private void sendHiddenNotification(CommunityPost post) {
        String postTitle = truncate(post.getTitle(), 30);
        Notification noti = Notification.builder()
                .userId(post.getUserId())
                .type("POST_HIDDEN")
                .targetType("POST")
                .targetId(post.getId())
                .title("게시글이 커뮤니티 가이드라인 검토 대기 상태로 전환되었습니다")
                .message("'" + postTitle + "' 게시글이 자동 검수에 의해 검토 대기 상태로 전환되었습니다. "
                        + "관리자 검토 후 복원되거나 삭제될 수 있습니다.")
                .link("/community?view=guidelines")
                .build();
        notificationService.notify(noti);
    }

    /**
     * 관리자 복원 시 작성자에게 알림 발송.
     */
    void sendRestoredNotification(CommunityPost post) {
        String postTitle = truncate(post.getTitle(), 30);
        Notification noti = Notification.builder()
                .userId(post.getUserId())
                .type("POST_RESTORED")
                .targetType("POST")
                .targetId(post.getId())
                .title("게시글이 복원되었습니다")
                .message("'" + postTitle + "' 게시글이 관리자 검토를 통과하여 복원되었습니다.")
                .link("/community/posts/" + post.getId())
                .build();
        notificationService.notify(noti);
    }

    /**
     * 관리자 삭제 시 작성자에게 알림 발송.
     */
    void sendDeletedNotification(CommunityPost post) {
        String postTitle = truncate(post.getTitle(), 30);
        Notification noti = Notification.builder()
                .userId(post.getUserId())
                .type("POST_REMOVED")
                .targetType("POST")
                .targetId(post.getId())
                .title("게시글이 가이드라인 위반으로 삭제되었습니다")
                .message("'" + postTitle + "' 게시글이 커뮤니티 가이드라인 위반으로 삭제 처리되었습니다.")
                .link("/community/posts/" + post.getId())
                .build();
        notificationService.notify(noti);
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "…";
    }

    private ModerationResult parseResult(String json) {
        try {
            return objectMapper.readValue(json, ModerationResult.class);
        } catch (Exception e) {
            throw new IllegalStateException("검열 결과 JSON 파싱 실패: " + json, e);
        }
    }
}
