package com.careertuner.correction.ai;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.careertuner.ai.common.budget.AiTotalTimeBudget;
import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.correction.ai.SelfCorrectionOutputParser.InvalidOutputException;
import com.careertuner.correction.ai.SelfLlmCorrectionProvider.RepairContext;
import com.careertuner.correction.ai.SelfLlmCorrectionProvider.SelfLlmCallException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CorrectionAiClient {

    private final CorrectionAiProperties properties;
    private final OpenAiCorrectionProvider openAiProvider;
    private final SelfLlmCorrectionProvider selfLlmProvider;
    private final AnthropicCorrectionProvider anthropicProvider;
    private final MockCorrectionProvider mockProvider;
    private final CorrectionModelWarmupService warmupService;

    /**
     * 첨삭 폴백 체인: 자체(Self) → Claude → OpenAI → <b>Mock(결정론 최종 안전망)</b>.
     *
     * <p>설정된 각 tier 는 최소 한 번 시도되고, 어떤 tier 도 예외를 던지면 로그만 남기고 다음 tier 로
     * 넘어간다. 마지막 {@link MockCorrectionProvider} 는 절대 예외를 던지지 않으므로, OpenAI 가 죽거나
     * 키가 없어도 첨삭 화면은 깨지지 않는다(이전에는 OpenAI 실패가 그대로 전파돼 화면이 깨졌다).
     */
    public CorrectionPayload correct(CorrectionCommand command) {
        // 1) 자체모델(Self) — 활성 시 항상 시도. 실패해도 예외를 밖으로 내지 않는다.
        if (properties.selfProviderEnabled()) {
            var self = properties.getSelf();
            warmupService.awaitIfInProgress(self.getTimeout());
            // 총 시간예산 — 0 또는 음수면 무제한(예산 OFF). self tier 의 최소 보장은 self.timeout.
            AiTotalTimeBudget budget = AiTotalTimeBudget.start(self.getTotalTimeBudget());
            try {
                return invokeModel(command, self.getModel(), self.getMaxAttempts(), budget);
            } catch (RuntimeException selfFailure) {
                log.warn("Self correction model {} failed: {}", self.getModel(), selfFailure.getMessage());
                if (!properties.isFallbackEnabled()) {
                    // 외부 폴백을 끈 설정 — Claude/OpenAI 는 건너뛰되 화면은 Mock 으로 안전하게 유지.
                    log.warn("Correction fallback disabled → Mock 결정론 안전망 반환");
                    return mockProvider.correct(command);
                }
            }
        }

        // 2) Claude tier — 설정 시 항상 시도.
        if (anthropicProvider.configured()) {
            try {
                return anthropicProvider.correct(command);
            } catch (RuntimeException anthropicFailure) {
                log.warn("Anthropic correction fallback failed: {}", anthropicFailure.getMessage());
            }
        } else {
            log.warn("Anthropic correction fallback is not configured. Skipping to OpenAI.");
        }

        // 3) OpenAI tier — 항상 시도. 이전과 달리 예외를 잡아 Mock 으로 폴백(화면 무깨짐).
        try {
            return openAiProvider.correct(command);
        } catch (RuntimeException openAiFailure) {
            log.warn("OpenAI correction fallback failed → Mock 결정론 안전망: {}", openAiFailure.getMessage());
        }

        // 4) Mock — 진짜 최후 안전망. 절대 예외를 던지지 않는다.
        return mockProvider.correct(command);
    }

    private CorrectionPayload invokeModel(
            CorrectionCommand command,
            String model,
            int maxAttempts,
            AiTotalTimeBudget budget
    ) {
        RuntimeException last = null;
        RepairContext repairContext = null;
        int attempts = Math.max(1, maxAttempts);
        for (int attempt = 1; attempt <= attempts; attempt++) {
            if (budget.expired()) {
                throw new SelfTimeBudgetExceededException();
            }
            // per-attempt 타임아웃을 남은 예산으로 절삭 (무제한이면 설정값 그대로).
            // positive(): 설정 오류(timeout ≤ 0)를 1ms 로 방어 — 리팩터링 전 동작과 동일.
            Duration timeout = budget.cap(positive(properties.getSelf().getTimeout()));
            try {
                return selfLlmProvider.correct(command, model, timeout, repairContext);
            } catch (InvalidOutputException ex) {
                last = ex;
                repairContext = new RepairContext(ex.getMessage(), ex.previousOutput());
            } catch (SelfLlmCallException ex) {
                last = ex;
                repairContext = null;
                if (!ex.retrySameModel()) {
                    throw ex;
                }
            }
            if (attempt < attempts) {
                sleep(budget.capBackoffMs(properties.getSelf().getRetryBackoff().toMillis()));
            }
        }
        throw last == null ? new IllegalStateException("Correction model failed.") : last;
    }

    private static Duration positive(Duration value) {
        return value == null || value.isZero() || value.isNegative() ? Duration.ofMillis(1) : value;
    }

    private void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Correction model retry was interrupted.", ex);
        }
    }

    public record CorrectionCommand(
            String correctionType,
            String sourceType,
            Long sourceRefId,
            Long applicationCaseId,
            ApplicationCase applicationCase,
            String originalText,
            String questionText,
            SelfCorrectionInput selfInput
    ) {
        public CorrectionCommand(
                String correctionType,
                String sourceType,
                Long sourceRefId,
                Long applicationCaseId,
                ApplicationCase applicationCase,
                String originalText,
                String questionText
        ) {
            this(correctionType, sourceType, sourceRefId, applicationCaseId,
                    applicationCase, originalText, questionText, null);
        }
    }

    public record CorrectionPayload(
            String improvedText,
            String summary,
            List<String> issues,
            List<String> changeReasons,
            List<String> suggestions,
            Usage usage,
            Map<String, Object> modelResult
    ) {
        public CorrectionPayload(
                String improvedText,
                String summary,
                List<String> issues,
                List<String> changeReasons,
                List<String> suggestions,
                Usage usage
        ) {
            this(improvedText, summary, issues, changeReasons, suggestions, usage, Map.of());
        }
    }

    public record Usage(String model, int inputTokens, int outputTokens, int totalTokens) {
    }

    static class SelfTimeBudgetExceededException extends RuntimeException {
        SelfTimeBudgetExceededException() {
            super("Correction self LLM time budget was exhausted.");
        }
    }
}
