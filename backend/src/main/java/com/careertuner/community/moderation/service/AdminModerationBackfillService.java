package com.careertuner.community.moderation.service;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.careertuner.community.mapper.CommunityPostMapper;
import com.careertuner.community.moderation.domain.AiTaskType;
import com.careertuner.community.moderation.domain.PostAiResult;
import com.careertuner.community.moderation.mapper.PostAiResultMapper;

/**
 * 관리자용 검열 배치/단건 재실행 서비스.
 *
 * SQL 직접 INSERT 등으로 작성 이벤트가 누락된 게시글을 사후에 검열하기 위한 용도.
 * 배치는 별도 스레드에서 순차 실행하며, moderate() 내부가 Ollama 동기 호출이라
 * 한 건씩 처리하면 자연스럽게 속도가 조절된다. (태깅/면접추출 배치와 동일 패턴)
 */
@Service
public class AdminModerationBackfillService {

    private static final Logger log = LoggerFactory.getLogger(AdminModerationBackfillService.class);

    private final CommunityPostMapper postMapper;
    private final PostAiResultMapper aiResultMapper;
    private final PostModerationService moderationService;

    private volatile boolean batchRunning = false;
    private final AtomicInteger batchTotal = new AtomicInteger(0);
    private final AtomicInteger batchCompleted = new AtomicInteger(0);
    private final AtomicInteger batchFailed = new AtomicInteger(0);

    public AdminModerationBackfillService(CommunityPostMapper postMapper,
                                          PostAiResultMapper aiResultMapper,
                                          PostModerationService moderationService) {
        this.postMapper = postMapper;
        this.aiResultMapper = aiResultMapper;
        this.moderationService = moderationService;
    }

    /**
     * 배치 대상 건수 조회 (dry-run 및 실행 전 공통).
     */
    public int countTargets(boolean force) {
        return postMapper.findPostIdsForModeration(force).size();
    }

    /**
     * 배치 검열 시작. 즉시 반환, 내부에서 비동기 순차 처리.
     * @return 대상 건수
     */
    public int startBackfill(boolean force) {
        if (batchRunning) {
            throw new IllegalStateException("배치가 이미 실행 중입니다. GET /status로 진행 상태를 확인하세요.");
        }

        List<Long> postIds = postMapper.findPostIdsForModeration(force);
        if (postIds.isEmpty()) {
            return 0;
        }

        batchTotal.set(postIds.size());
        batchCompleted.set(0);
        batchFailed.set(0);
        batchRunning = true;

        runBackfillAsync(postIds);
        return postIds.size();
    }

    /**
     * 별도 스레드에서 순차 검열. moderationExecutor가 아닌 기본 async 풀 사용.
     */
    @Async
    void runBackfillAsync(List<Long> postIds) {
        log.info("검열 배치 시작: {}건", postIds.size());
        try {
            for (Long postId : postIds) {
                try {
                    moderationService.moderate(postId);
                    batchCompleted.incrementAndGet();
                } catch (Exception e) {
                    batchFailed.incrementAndGet();
                    log.warn("배치 검열 실패: postId={}", postId, e);
                }
            }
        } finally {
            batchRunning = false;
            log.info("검열 배치 완료: total={}, completed={}, failed={}",
                    batchTotal.get(), batchCompleted.get(), batchFailed.get());
        }
    }

    /**
     * 단건 재검열.
     */
    public void moderateSingle(Long postId, boolean force) {
        if (!force) {
            PostAiResult existing = aiResultMapper.findByPostIdAndTaskType(postId, AiTaskType.MODERATION);
            if (existing != null && "COMPLETED".equals(existing.getStatus())) {
                throw new IllegalStateException(
                        "이미 검열 완료된 게시글입니다 (postId=" + postId + "). 강제 재실행은 ?force=true");
            }
        }
        moderationService.moderate(postId);
    }

    /**
     * 배치 진행 상태.
     */
    public BatchStatus getStatus() {
        return new BatchStatus(
                batchRunning,
                batchTotal.get(),
                batchCompleted.get(),
                batchFailed.get(),
                batchTotal.get() - batchCompleted.get() - batchFailed.get()
        );
    }

    public record BatchStatus(
            boolean running,
            int total,
            int completed,
            int failed,
            int pending
    ) {}
}
