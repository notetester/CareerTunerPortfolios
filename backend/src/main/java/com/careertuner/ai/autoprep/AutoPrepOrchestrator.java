package com.careertuner.ai.autoprep;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.careertuner.ai.autoprep.dto.AutoPrepRequest;
import com.careertuner.ai.autoprep.dto.AutoPrepResponse;
import com.careertuner.common.exception.BusinessException;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AI 오케스트레이터. 두뇌가 세운 PrepPlan 의 steps 를 순서대로 실행한다.
 *
 * <p>두 진입: run(동기, 결과 한방) / runStream(SSE, 진행 실시간). 둘 다 같은 단계 실행 로직을 공유하고
 * 진행 보고(PrepProgress)만 다르다 — run 은 NOOP, runStream 은 SSE 전송.
 * 첨부 파일은 시작 시 1회 로딩(플랜 게이팅)해 모든 단계에 같은 context 로 넘긴다.
 * 구현이 없거나 비활성 단계는 SKIPPED, 실패해도 FAILED 로 기록하고 끝까지 완주한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoPrepOrchestrator {

    private static final long SSE_TIMEOUT_MS = 300_000L;

    private final AutoPrepPlanner planner;
    private final AutoPrepAttachmentLoader attachmentLoader;
    private final List<PrepStepHandler> handlers;

    private final ExecutorService sseExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "autoprep-sse");
        t.setDaemon(true);
        return t;
    });

    @PreDestroy
    void shutdown() {
        sseExecutor.shutdownNow();
    }

    /** 동기 실행 — 6파트 다 돌고 결과를 한 번에 반환. */
    public AutoPrepResponse run(Long userId, AutoPrepRequest request) {
        PrepPlan plan = planner.plan(userId, request);
        List<PrepAttachment> attachments = attachmentLoader.load(userId, request.attachmentFileIds());
        Map<String, PrepStepHandler> byKey = byKey();
        Map<String, Object> prior = new LinkedHashMap<>();
        List<PrepStepResult> results = new ArrayList<>();

        for (String key : plan.steps()) {
            PrepStepHandler handler = byKey.get(key);
            if (handler == null || !handler.enabled()) {
                results.add(PrepStepResult.skipped(key, "준비중"));
                continue;
            }
            PrepStepResult result = executeOne(userId, request, plan, attachments, handler, prior, PrepProgress.NOOP);
            results.add(result);
            accumulate(prior, result);
        }
        return new AutoPrepResponse(plan, results, summary(results));
    }

    /** SSE 실행 — plan → (파트마다 part-start / substep* / part-done) → done 이벤트를 실시간 전송. */
    public SseEmitter runStream(Long userId, AutoPrepRequest request) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        sseExecutor.execute(() -> streamRun(userId, request, emitter));
        return emitter;
    }

    private void streamRun(Long userId, AutoPrepRequest request, SseEmitter emitter) {
        try {
            PrepPlan plan = planner.plan(userId, request);
            send(emitter, "plan", plan);

            List<PrepAttachment> attachments = attachmentLoader.load(userId, request.attachmentFileIds());
            Map<String, PrepStepHandler> byKey = byKey();
            Map<String, Object> prior = new LinkedHashMap<>();
            List<PrepStepResult> results = new ArrayList<>();

            for (String key : plan.steps()) {
                send(emitter, "part-start", Map.of("key", key));
                PrepStepHandler handler = byKey.get(key);
                if (handler == null || !handler.enabled()) {
                    PrepStepResult skipped = PrepStepResult.skipped(key, "준비중");
                    results.add(skipped);
                    send(emitter, "part-done", skipped);
                    continue;
                }
                PrepProgress progress = (name, desc) -> send(emitter, "substep",
                        Map.of("key", key, "name", name, "desc", desc == null ? "" : desc));
                PrepStepResult result = executeOne(userId, request, plan, attachments, handler, prior, progress);
                results.add(result);
                accumulate(prior, result);
                send(emitter, "part-done", result);
            }

            send(emitter, "done", Map.of("message", summary(results)));
            emitter.complete();
        } catch (RuntimeException ex) {
            log.warn("AutoPrep 스트림 실패: {}", ex.getMessage());
            emitter.completeWithError(ex);
        }
    }

    private PrepStepResult executeOne(Long userId, AutoPrepRequest request, PrepPlan plan,
                                      List<PrepAttachment> attachments, PrepStepHandler handler,
                                      Map<String, Object> prior, PrepProgress progress) {
        String key = handler.key();
        long start = System.nanoTime();
        try {
            PrepStepContext context = new PrepStepContext(
                    userId, plan.slots().applicationCaseId(), plan.slots(),
                    request.coverLetterText(), attachments, prior);
            return handler.handle(context, progress);
        } catch (BusinessException ex) {
            long ms = (System.nanoTime() - start) / 1_000_000;
            return PrepStepResult.failed(key, ex.getMessage(), ms);
        } catch (RuntimeException ex) {
            long ms = (System.nanoTime() - start) / 1_000_000;
            log.warn("AutoPrep 단계 실패 key={}: {}", key, ex.getMessage());
            return PrepStepResult.failed(key, "처리 중 오류가 발생했습니다.", ms);
        }
    }

    private void accumulate(Map<String, Object> prior, PrepStepResult result) {
        if ("DONE".equals(result.status()) && result.detail() != null) {
            prior.put(result.key(), result.detail());
        }
    }

    private Map<String, PrepStepHandler> byKey() {
        return handlers.stream().collect(Collectors.toMap(PrepStepHandler::key, Function.identity(), (a, b) -> a));
    }

    private String summary(List<PrepStepResult> results) {
        long failed = results.stream().filter(r -> "FAILED".equals(r.status())).count();
        long done = results.stream().filter(r -> "DONE".equals(r.status())).count();
        return "완료 %d · 건너뜀 %d · 실패 %d".formatted(done, results.size() - done - failed, failed);
    }

    private void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException ex) {
            throw new IllegalStateException("SSE 전송 중단(클라이언트 종료)", ex);
        }
    }
}
