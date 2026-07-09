package com.careertuner.community.moderation.schedule;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.careertuner.community.mapper.CommunityPostMapper;
import com.careertuner.community.moderation.mapper.CommentAiResultMapper;
import com.careertuner.community.moderation.service.PostModerationService;

/**
 * 검열/태깅/면접추출 큐 거부·유실분 자동 재처리 스케줄러.
 *
 * <p>배경: {@code moderationExecutor}(ModerationAsyncConfig) 큐 포화 시
 * RejectedExecutionHandler 가 로그만 남기고 작업을 폐기한다. 폐기된 글은
 * post_ai_result 에 PENDING 흔적조차 남지 않으므로, 회수는 관리자가 수동으로
 * backfill(AdminModerationBackfillService 등)을 돌리는 것뿐이었다.
 *
 * <p>해결: 기존 backfill 이 사용하는 "COMPLETED 결과가 없는(NOT EXISTS) 대상"
 * 조회 쿼리(findPostIdsForModeration/Tagging/InterviewExtract, force=false)와
 * 단건 처리 메서드(moderate/tag/extractInterviewQuestions)를 그대로 재사용해,
 * 주기적으로 유실분을 회수한다. 새 회수 쿼리나 호출 구조는 만들지 않는다.
 *
 * <p>거부 직전 PENDING 선기록은 거부 경로에 DB I/O 부하를 더하므로 채택하지
 * 않고, NOT EXISTS 기반 자동 backfill 스케줄러로 유실분을 주기 회수한다.
 *
 * <p>@Async self-invocation 이슈가 있던 AdminModerationBackfillService#runBackfillAsync
 * 가 아니라, 스케줄러 스레드에서 PostModerationService 를 직접(동기) 호출한다.
 * 스케줄러는 외부 빈에서 호출되므로 프록시 self-invocation 문제가 없다.
 *
 * <p>@EnableScheduling 은 ApplicationCaseExtractionSchedulingConfig 에 이미
 * 선언돼 있으므로 여기서 다시 선언하지 않는다.
 */
@Component
public class ModerationRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(ModerationRetryScheduler.class);

    /** 한 주기에 작업 유형별로 처리할 최대 건수 (폭주 방지). */
    private static final int BATCH_LIMIT = 20;

    private final CommunityPostMapper postMapper;
    private final CommentAiResultMapper commentAiResultMapper;
    private final PostModerationService moderationService;

    /** 직전 실행이 길어질 때 다음 실행이 겹치지 않도록 보호. */
    private final AtomicBoolean running = new AtomicBoolean(false);

    public ModerationRetryScheduler(CommunityPostMapper postMapper,
                                    CommentAiResultMapper commentAiResultMapper,
                                    PostModerationService moderationService) {
        this.postMapper = postMapper;
        this.commentAiResultMapper = commentAiResultMapper;
        this.moderationService = moderationService;
    }

    /**
     * 초기지연 90초, 고정지연 5분. 미처리(COMPLETED 없는) 검열/태깅/면접추출
     * 대상을 비-force 로 회수한다. 스케줄러 스레드가 죽지 않도록 self-contained.
     */
    @Scheduled(initialDelay = 90_000L, fixedDelay = 300_000L)
    public void retryMissedTasks() {
        if (!running.compareAndSet(false, true)) {
            log.info("검열 재처리 스케줄러: 직전 실행이 아직 진행 중 — 이번 주기 스킵");
            return;
        }
        try {
            int moderated = retryModeration();
            int commentModerated = retryCommentModeration();
            int tagged = retryTagging();
            int extracted = retryInterviewExtract();
            if (moderated > 0 || commentModerated > 0 || tagged > 0 || extracted > 0) {
                log.info("검열 재처리 스케줄러 완료: 검열={}건, 댓글검열={}건, 태깅={}건, 면접추출={}건 (유형별 상한 {})",
                        moderated, commentModerated, tagged, extracted, BATCH_LIMIT);
            }
        } catch (RuntimeException ex) {
            log.warn("검열 재처리 스케줄러 주기 스킵: {}", rootCauseMessage(ex));
        } finally {
            running.set(false);
        }
    }

    private int retryModeration() {
        List<Long> postIds = limit(postMapper.findPostIdsForModeration(false));
        int processed = 0;
        for (Long postId : postIds) {
            try {
                moderationService.moderate(postId);
                processed++;
            } catch (Exception e) {
                log.warn("검열 재처리 실패: postId={}", postId, e);
            }
        }
        return processed;
    }

    private int retryCommentModeration() {
        List<Long> commentIds = limit(commentAiResultMapper.findCommentIdsForModeration(false));
        int processed = 0;
        for (Long commentId : commentIds) {
            try {
                moderationService.moderateComment(commentId);
                processed++;
            } catch (Exception e) {
                log.warn("댓글 검열 재처리 실패: commentId={}", commentId, e);
            }
        }
        return processed;
    }

    private int retryTagging() {
        List<Long> postIds = limit(postMapper.findPostIdsForTagging(false));
        int processed = 0;
        for (Long postId : postIds) {
            try {
                moderationService.tag(postId);
                processed++;
            } catch (Exception e) {
                log.warn("태깅 재처리 실패: postId={}", postId, e);
            }
        }
        return processed;
    }

    private int retryInterviewExtract() {
        List<Long> postIds = limit(postMapper.findPostIdsForInterviewExtract(false));
        int processed = 0;
        for (Long postId : postIds) {
            try {
                moderationService.extractInterviewQuestions(postId);
                processed++;
            } catch (Exception e) {
                log.warn("면접 질문 추출 재처리 실패: postId={}", postId, e);
            }
        }
        return processed;
    }

    /** 한 주기 처리량 상한 적용 (폭주 방지). */
    private static List<Long> limit(List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return List.of();
        }
        if (postIds.size() <= BATCH_LIMIT) {
            return postIds;
        }
        return postIds.subList(0, BATCH_LIMIT);
    }

    private static String rootCauseMessage(Throwable ex) {
        Throwable current = ex;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        if (message == null || message.isBlank()) {
            message = current.getClass().getSimpleName();
        }
        return message.replace('\n', ' ').replace('\r', ' ');
    }
}
