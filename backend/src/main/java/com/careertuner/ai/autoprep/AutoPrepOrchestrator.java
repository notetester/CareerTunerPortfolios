package com.careertuner.ai.autoprep;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
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
 * AI 오케스트레이터. 두뇌가 세운 PrepPlan 의 steps 를 의존 그래프대로 병렬 실행한다.
 *
 * <p>독립 파트(A 프로필·B 공고·E 자소서·F 커뮤니티)는 동시에 출발하고, FIT·INTERVIEW 는
 * JOB(공고 분석)이 DB 에 커밋된 뒤 시작한다. run(동기)/runStream(SSE) 둘 다 같은 병렬 로직을 쓰고
 * 진행 보고(PartListener)만 다르다. 미구현/비활성은 SKIPPED, 실패해도 FAILED 로 기록하고 완주한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoPrepOrchestrator {

    private static final long SSE_TIMEOUT_MS = 300_000L;

    // 파트 의존: FIT·INTERVIEW 는 JOB(공고 분석 결과 DB)이 끝난 뒤 실행. 나머지는 독립 → 동시 출발.
    private static final Map<String, List<String>> DEPS = Map.of(
            "FIT", List.of("JOB"),
            "INTERVIEW", List.of("JOB"));

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

    /** 진행 이벤트 수신자. run 은 무시(NOOP), runStream 은 SSE 전송. */
    private interface PartListener {
        void onPartStart(String key);
        void onSubstep(String key, String name, String desc);
        void onPartDone(PrepStepResult result);
    }

    private static final PartListener NOOP_LISTENER = new PartListener() {
        @Override public void onPartStart(String key) { }
        @Override public void onSubstep(String key, String name, String desc) { }
        @Override public void onPartDone(PrepStepResult result) { }
    };

    /** 동기 실행 — 병렬로 돌리고 결과를 plan 순서로 정렬해 한 번에 반환. */
    public AutoPrepResponse run(Long userId, AutoPrepRequest request) {
        PrepPlan plan = planner.plan(userId, request);
        List<PrepAttachment> attachments = attachmentLoader.load(userId, request.attachmentFileIds());
        List<PrepStepResult> results = executeParallel(userId, request, plan, attachments, NOOP_LISTENER);
        List<PrepStepResult> ordered = new ArrayList<>(results);
        ordered.sort(Comparator.comparingInt(r -> indexOf(plan.steps(), r.key())));
        return new AutoPrepResponse(plan, ordered, summary(results));
    }

    /** SSE 실행 — 의존 그래프 병렬 + plan/part-start/substep/part-done/done 실시간 전송. */
    public SseEmitter runStream(Long userId, AutoPrepRequest request) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        sseExecutor.execute(() -> {
            try {
                PrepPlan plan = planner.plan(userId, request);
                send(emitter, "plan", plan);
                List<PrepAttachment> attachments = attachmentLoader.load(userId, request.attachmentFileIds());
                executeParallel(userId, request, plan, attachments, new PartListener() {
                    @Override public void onPartStart(String key) {
                        send(emitter, "part-start", Map.of("key", key));
                    }
                    @Override public void onSubstep(String key, String name, String desc) {
                        send(emitter, "substep", Map.of("key", key, "name", name, "desc", desc == null ? "" : desc));
                    }
                    @Override public void onPartDone(PrepStepResult result) {
                        send(emitter, "part-done", result);
                    }
                });
                send(emitter, "done", Map.of("message", "완료"));
                emitter.complete();
            } catch (RuntimeException ex) {
                log.warn("AutoPrep 스트림 실패: {}", ex.getMessage());
                emitter.completeWithError(ex);
            }
        });
        return emitter;
    }

    /** 의존 그래프 기반 병렬 실행. dep 파트의 future 가 끝난 뒤 시작한다. */
    private List<PrepStepResult> executeParallel(Long userId, AutoPrepRequest request, PrepPlan plan,
                                                 List<PrepAttachment> attachments, PartListener listener) {
        Map<String, PrepStepHandler> byKey = byKey();
        Map<String, Object> prior = new ConcurrentHashMap<>();
        List<PrepStepResult> results = Collections.synchronizedList(new ArrayList<>());
        Map<String, CompletableFuture<Void>> futures = new HashMap<>();

        for (String key : plan.steps()) {
            CompletableFuture<?>[] depFutures = DEPS.getOrDefault(key, List.of()).stream()
                    .map(futures::get)
                    .filter(Objects::nonNull)
                    .toArray(CompletableFuture[]::new);
            CompletableFuture<Void> future = CompletableFuture.allOf(depFutures).thenRunAsync(
                    () -> runPart(userId, key, byKey, plan, attachments, request, prior, results, listener),
                    sseExecutor);
            futures.put(key, future);
        }
        CompletableFuture.allOf(futures.values().toArray(new CompletableFuture[0])).join();
        return results;
    }

    private void runPart(Long userId, String key, Map<String, PrepStepHandler> byKey, PrepPlan plan,
                         List<PrepAttachment> attachments, AutoPrepRequest request,
                         Map<String, Object> prior, List<PrepStepResult> results, PartListener listener) {
        listener.onPartStart(key);
        PrepStepHandler handler = byKey.get(key);
        if (handler == null || !handler.enabled()) {
            PrepStepResult skipped = PrepStepResult.skipped(key, "준비중");
            results.add(skipped);
            listener.onPartDone(skipped);
            return;
        }
        PrepProgress progress = (name, desc) -> listener.onSubstep(key, name, desc);
        PrepStepResult result = executeOne(userId, request, plan, attachments, handler, prior, progress);
        results.add(result);
        accumulate(prior, result);
        listener.onPartDone(result);
    }

    private PrepStepResult executeOne(Long userId, AutoPrepRequest request, PrepPlan plan,
                                      List<PrepAttachment> attachments, PrepStepHandler handler,
                                      Map<String, Object> prior, PrepProgress progress) {
        String key = handler.key();
        long start = System.nanoTime();
        try {
            PrepStepContext context = new PrepStepContext(
                    userId, plan.slots().applicationCaseId(), plan.slots(), request.coverLetterText(), attachments, prior);
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

    private static int indexOf(List<String> steps, String key) {
        int i = steps.indexOf(key);
        return i < 0 ? Integer.MAX_VALUE : i;
    }

    /** SSE 전송. 병렬 파트가 동시에 호출하므로 emitter 단위로 동기화한다. */
    private void send(SseEmitter emitter, String event, Object data) {
        try {
            synchronized (emitter) {
                emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
            }
        } catch (IOException ex) {
            throw new IllegalStateException("SSE 전송 중단(클라이언트 종료)", ex);
        }
    }
}
