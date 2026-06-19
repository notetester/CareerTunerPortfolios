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
 * 관리자용 면접 질문 추출 배치/단건 실행 서비스.
 *
 * 배치는 별도 스레드에서 순차 실행 (moderationExecutor 큐 포화 방지).
 * extractInterviewQuestions() 내부가 Ollama 동기 호출이라, 한 건씩 처리하면 자연스럽게 속도 조절됨.
 */
@Service
public class AdminInterviewExtractService {

    private static final Logger log = LoggerFactory.getLogger(AdminInterviewExtractService.class);

    private final CommunityPostMapper postMapper;
    private final PostAiResultMapper aiResultMapper;
    private final PostModerationService moderationService;

    private volatile boolean batchRunning = false;
    private final AtomicInteger batchTotal = new AtomicInteger(0);
    private final AtomicInteger batchCompleted = new AtomicInteger(0);
    private final AtomicInteger batchFailed = new AtomicInteger(0);

    public AdminInterviewExtractService(CommunityPostMapper postMapper,
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
        return postMapper.findPostIdsForInterviewExtract(force).size();
    }

    /**
     * 배치 추출 시작. 즉시 반환, 내부에서 비동기 순차 처리.
     * @return 대상 건수
     */
    public int startBackfill(boolean force) {
        if (batchRunning) {
            throw new IllegalStateException("배치가 이미 실행 중입니다. GET /status로 진행 상태를 확인하세요.");
        }

        List<Long> postIds = postMapper.findPostIdsForInterviewExtract(force);
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
     * 별도 스레드에서 순차 추출. moderationExecutor가 아닌 기본 async 풀 사용.
     */
    @Async
    void runBackfillAsync(List<Long> postIds) {
        log.info("면접 질문 추출 배치 시작: {}건", postIds.size());
        try {
            for (Long postId : postIds) {
                try {
                    moderationService.extractInterviewQuestions(postId);
                    batchCompleted.incrementAndGet();
                } catch (Exception e) {
                    batchFailed.incrementAndGet();
                    log.warn("배치 면접 질문 추출 실패: postId={}", postId, e);
                }
            }
        } finally {
            batchRunning = false;
            log.info("면접 질문 추출 배치 완료: total={}, completed={}, failed={}",
                    batchTotal.get(), batchCompleted.get(), batchFailed.get());
        }
    }

    /**
     * 단건 재추출.
     */
    public void extractSingle(Long postId, boolean force) {
        if (!force) {
            PostAiResult existing = aiResultMapper.findByPostIdAndTaskType(postId, AiTaskType.INTERVIEW_EXTRACT);
            if (existing != null && "COMPLETED".equals(existing.getStatus())) {
                throw new IllegalStateException(
                        "이미 추출 완료된 게시글입니다 (postId=" + postId + "). 강제 재실행은 ?force=true");
            }
        }
        moderationService.extractInterviewQuestions(postId);
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
