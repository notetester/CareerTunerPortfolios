package com.careertuner.correction.ai;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.careertuner.ai.common.budget.AiTotalTimeBudget;
import com.careertuner.ai.common.model.AiProviderChain;
import com.careertuner.ai.common.model.AiProviderTier;
import com.careertuner.ai.common.model.RequestedAiModel;
import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.correction.ai.SelfCorrectionOutputParser.InvalidOutputException;
import com.careertuner.correction.ai.SelfLlmCorrectionProvider.RepairContext;
import com.careertuner.correction.ai.SelfLlmCorrectionProvider.SelfLlmCallException;
import com.careertuner.runtimesetting.service.RuntimeSettingService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CorrectionAiClient {

    /** E 첨삭 self tier 총 시간예산 DB 런타임 키. 행이 없으면 정적 self.totalTimeBudget 로 fallback(동작 불변). */
    private static final String SELF_TOTAL_TIME_BUDGET_KEY = "ai.correction.self-total-time-budget-seconds";

    /** 첨삭 기본 tier 순서. AUTO 는 이 전체를, 명시 선택은 그 tier 부터 하위까지 시도한다. */
    private static final List<AiProviderTier> DEFAULT_ORDER =
            List.of(AiProviderTier.CAREERTUNER, AiProviderTier.CLAUDE, AiProviderTier.OPENAI);

    private final CorrectionAiProperties properties;
    private final OpenAiCorrectionProvider openAiProvider;
    private final SelfLlmCorrectionProvider selfLlmProvider;
    private final AnthropicCorrectionProvider anthropicProvider;
    private final CorrectionModelWarmupService warmupService;
    private final RuntimeSettingService runtimeSettings;

    /**
     * 첨삭 폴백 체인: 자체(Self) → Claude → OpenAI.
     *
     * <p>설정된 각 tier 는 최소 한 번 시도되고, 어떤 tier 도 예외를 던지면 로그를 남기고 다음 tier 로
     * 넘어간다. 실제 provider 가 모두 실패하면 무가치한 원문 복제 결과를 성공으로 저장하거나 과금하지
     * 않도록 {@link ErrorCode#AI_UNAVAILABLE}로 종료한다.
     */
    public CorrectionPayload correct(CorrectionCommand command) {
        return correct(command, RequestedAiModel.AUTO);
    }

    /**
     * 사용자가 고른 모델 tier 부터 시작하는 첨삭 폴백. {@code AUTO} 는 자체→Claude→OpenAI 현행과 동일하고,
     * 명시 선택은 그 tier 부터 시작하되 실패 시 하위 tier 로 폴백한다. 첨삭은 무가치한 원문 복제를 성공으로
     * 저장하지 않도록 <b>안전망 Mock 없이</b> 모든 tier 실패 시 {@link ErrorCode#AI_UNAVAILABLE}로 종료한다
     * (화면은 정직한 오류 안내). 명시 {@code CAREERTUNER}는 전역 self 토글이 꺼져 있어도 자체모델을 시도한다.
     * 과금·멱등·출력검증 계약은 어느 tier 를 타든 동일하다.
     */
    public CorrectionPayload correct(CorrectionCommand command, RequestedAiModel requestedModel) {
        boolean explicitSelf = requestedModel == RequestedAiModel.CAREERTUNER;
        for (AiProviderTier tier : AiProviderChain.startingFrom(requestedModel, DEFAULT_ORDER)) {
            switch (tier) {
                case CAREERTUNER -> {
                    // 자체모델(Self) — 활성이거나(명시 선택 + 엔드포인트 설정 시) 시도. 실패하면 다음 tier 로.
                    // 명시 CAREERTUNER 는 전역 self 토글을 우회하되, 엔드포인트(self.configured)가 있어야 시도한다.
                    if (properties.selfProviderEnabled() || (explicitSelf && properties.getSelf().configured())) {
                        var self = properties.getSelf();
                        warmupService.awaitIfInProgress(self.getTimeout());
                        // 총 시간예산 — 0/음수면 무제한. DB 런타임 우선, 행 없으면 정적 self.totalTimeBudget.
                        AiTotalTimeBudget budget = AiTotalTimeBudget.start(selfTotalTimeBudget(self));
                        try {
                            return invokeModel(command, self.getModel(), self.getMaxAttempts(), budget);
                        } catch (RuntimeException selfFailure) {
                            log.warn("Self correction model {} failed: {}", self.getModel(), selfFailure.getMessage());
                            if (!properties.isFallbackEnabled()) {
                                log.warn("Correction fallback disabled after self provider failure");
                                throw unavailable();
                            }
                        }
                    }
                }
                case CLAUDE -> {
                    if (anthropicProvider.configured()) {
                        try {
                            return anthropicProvider.correct(command);
                        } catch (RuntimeException anthropicFailure) {
                            log.warn("Anthropic correction fallback failed: {}", anthropicFailure.getMessage());
                        }
                    } else {
                        log.warn("Anthropic correction fallback is not configured. Skipping to OpenAI.");
                    }
                }
                case OPENAI -> {
                    try {
                        return openAiProvider.correct(command);
                    } catch (RuntimeException openAiFailure) {
                        log.warn("OpenAI correction fallback failed: {}", openAiFailure.getMessage());
                    }
                }
            }
        }

        throw unavailable();
    }

    private BusinessException unavailable() {
        return new BusinessException(
                ErrorCode.AI_UNAVAILABLE,
                "AI 첨삭 서비스를 일시적으로 사용할 수 없습니다. 잠시 후 다시 시도해 주세요.");
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

    /** self tier 총 시간예산: DB 런타임 키 우선(초 단위), 행이 없으면 정적 self.totalTimeBudget 그대로. */
    private Duration selfTotalTimeBudget(CorrectionAiProperties.Self self) {
        return Duration.ofSeconds(
                runtimeSettings.getInt(SELF_TOTAL_TIME_BUDGET_KEY, (int) self.getTotalTimeBudget().toSeconds()));
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
