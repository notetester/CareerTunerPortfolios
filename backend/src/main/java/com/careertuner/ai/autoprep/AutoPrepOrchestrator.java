package com.careertuner.ai.autoprep;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.careertuner.ai.autoprep.dto.AutoPrepRequest;
import com.careertuner.ai.autoprep.dto.AutoPrepResponse;
import com.careertuner.common.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AI 오케스트레이터. 두뇌가 세운 PrepPlan 의 steps 를 순서대로 실행한다.
 *
 * <p>핸들러는 {@link PrepStepHandler} 구현 @Component 들을 Spring 이 자동 주입한다(key 로 매칭).
 * 구현이 없거나 비활성(enabled=false)인 단계는 SKIPPED 로 기록하고 계속 진행한다.
 * 한 단계가 실패해도 FAILED 로 기록하고 다음 단계로 넘어가, 어떤 경우에도 끝까지 완주한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoPrepOrchestrator {

    private final AutoPrepPlanner planner;
    private final List<PrepStepHandler> handlers;

    public AutoPrepResponse run(Long userId, AutoPrepRequest request) {
        PrepPlan plan = planner.plan(userId, request);
        Map<String, PrepStepHandler> byKey = handlers.stream()
                .collect(Collectors.toMap(PrepStepHandler::key, Function.identity(), (a, b) -> a));

        Map<String, Object> prior = new LinkedHashMap<>();
        List<PrepStepResult> results = new ArrayList<>();

        for (String key : plan.steps()) {
            PrepStepHandler handler = byKey.get(key);
            if (handler == null || !handler.enabled()) {
                results.add(PrepStepResult.skipped(key, "준비중"));
                continue;
            }
            long start = System.nanoTime();
            try {
                PrepStepContext context = new PrepStepContext(
                        userId, plan.slots().applicationCaseId(), plan.slots(),
                        request.coverLetterText(), prior);
                PrepStepResult result = handler.handle(context);
                results.add(result);
                if (result.detail() != null) {
                    prior.put(key, result.detail());
                }
            } catch (BusinessException ex) {
                long ms = (System.nanoTime() - start) / 1_000_000;
                results.add(PrepStepResult.failed(key, ex.getMessage(), ms));
            } catch (RuntimeException ex) {
                long ms = (System.nanoTime() - start) / 1_000_000;
                log.warn("AutoPrep 단계 실패 key={}: {}", key, ex.getMessage());
                results.add(PrepStepResult.failed(key, "처리 중 오류가 발생했습니다.", ms));
            }
        }

        long failed = results.stream().filter(r -> "FAILED".equals(r.status())).count();
        long done = results.stream().filter(r -> "DONE".equals(r.status())).count();
        String message = "완료 %d · 건너뜀 %d · 실패 %d".formatted(
                done, results.size() - done - failed, failed);
        return new AutoPrepResponse(plan, results, message);
    }
}
