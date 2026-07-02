package com.careertuner.correction.ai;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.careertuner.ai.common.budget.AiTotalTimeBudget;
import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.correction.ai.SelfCorrectionOutputParser.InvalidOutputException;
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
    private final CorrectionModelWarmupService warmupService;

    public CorrectionPayload correct(CorrectionCommand command) {
        if (!properties.selfProviderEnabled()) {
            return openAiProvider.correct(command);
        }

        var self = properties.getSelf();
        warmupService.awaitIfInProgress(self.getTimeout());
        // 총 시간예산 — 0 또는 음수면 무제한(예산 OFF)
        AiTotalTimeBudget budget = AiTotalTimeBudget.start(self.getTotalTimeBudget());
        try {
            return invokeModel(command, self.getModel(), self.getPrimaryMaxAttempts(), budget);
        } catch (RuntimeException primaryFailure) {
            if (!properties.isFallbackEnabled()) {
                throw primaryFailure;
            }
            log.warn("Primary correction model {} failed: {}", self.getModel(), primaryFailure.getMessage());
        }

        if (hasText(self.getFallbackModel()) && !budget.expired()) {
            try {
                return invokeModel(command, self.getFallbackModel(), self.getFallbackMaxAttempts(), budget);
            } catch (RuntimeException fallbackFailure) {
                log.warn("Fallback correction model {} failed: {}",
                        self.getFallbackModel(), fallbackFailure.getMessage());
            }
        }

        if (anthropicProvider.configured()) {
            try {
                return anthropicProvider.correct(command);
            } catch (RuntimeException anthropicFailure) {
                log.warn("Anthropic correction fallback failed: {}", anthropicFailure.getMessage());
            }
        } else {
            log.warn("Anthropic correction fallback is not configured. Skipping to OpenAI.");
        }

        log.warn("Self and Anthropic correction chain failed. Falling back to OpenAI.");
        return openAiProvider.correct(command);
    }

    private CorrectionPayload invokeModel(
            CorrectionCommand command,
            String model,
            int maxAttempts,
            AiTotalTimeBudget budget
    ) {
        RuntimeException last = null;
        int attempts = Math.max(1, maxAttempts);
        for (int attempt = 1; attempt <= attempts; attempt++) {
            if (budget.expired()) {
                throw new SelfTimeBudgetExceededException();
            }
            // per-attempt 타임아웃을 남은 예산으로 절삭 (무제한이면 설정값 그대로).
            // positive(): 설정 오류(timeout ≤ 0)를 1ms 로 방어 — 리팩터링 전 동작과 동일.
            Duration timeout = budget.cap(positive(properties.getSelf().getTimeout()));
            try {
                return selfLlmProvider.correct(command, model, timeout);
            } catch (InvalidOutputException ex) {
                last = ex;
            } catch (SelfLlmCallException ex) {
                last = ex;
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

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
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
