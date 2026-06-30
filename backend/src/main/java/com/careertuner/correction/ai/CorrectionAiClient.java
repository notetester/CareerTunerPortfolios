package com.careertuner.correction.ai;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

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
        long deadline = System.nanoTime() + positive(self.getTotalTimeBudget()).toNanos();
        try {
            return invokeModel(command, self.getModel(), self.getPrimaryMaxAttempts(), deadline);
        } catch (RuntimeException primaryFailure) {
            if (!properties.isFallbackEnabled()) {
                throw primaryFailure;
            }
            log.warn("Primary correction model {} failed: {}", self.getModel(), primaryFailure.getMessage());
        }

        if (hasText(self.getFallbackModel()) && remaining(deadline).toMillis() > 0) {
            try {
                return invokeModel(command, self.getFallbackModel(), self.getFallbackMaxAttempts(), deadline);
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
            long deadline
    ) {
        RuntimeException last = null;
        int attempts = Math.max(1, maxAttempts);
        for (int attempt = 1; attempt <= attempts; attempt++) {
            Duration remaining = remaining(deadline);
            if (remaining.isZero() || remaining.isNegative()) {
                throw new SelfTimeBudgetExceededException();
            }
            Duration timeout = min(positive(properties.getSelf().getTimeout()), remaining);
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
                sleepWithinBudget(properties.getSelf().getRetryBackoff(), deadline);
            }
        }
        throw last == null ? new IllegalStateException("Correction model failed.") : last;
    }

    private void sleepWithinBudget(Duration backoff, long deadline) {
        long millis = Math.min(Math.max(0, backoff.toMillis()), Math.max(0, remaining(deadline).toMillis()));
        if (millis == 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Correction model retry was interrupted.", ex);
        }
    }

    private Duration remaining(long deadline) {
        return Duration.ofNanos(Math.max(0, deadline - System.nanoTime()));
    }

    private Duration min(Duration left, Duration right) {
        return left.compareTo(right) <= 0 ? left : right;
    }

    private Duration positive(Duration value) {
        return value == null || value.isZero() || value.isNegative() ? Duration.ofMillis(1) : value;
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
