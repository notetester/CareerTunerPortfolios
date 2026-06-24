package com.careertuner.correction.ai;

import org.springframework.stereotype.Service;

import com.careertuner.applicationcase.domain.ApplicationCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CorrectionAiClient {

    private final CorrectionAiProperties properties;
    private final OpenAiCorrectionProvider openAiProvider;
    private final SelfLlmCorrectionProvider selfLlmProvider;

    public CorrectionPayload correct(CorrectionCommand command) {
        if (!properties.selfProviderEnabled()) {
            return openAiProvider.correct(command);
        }
        try {
            return selfLlmProvider.correct(command);
        } catch (RuntimeException ex) {
            if (!properties.isFallbackEnabled()) {
                throw ex;
            }
            log.warn("Self LLM correction provider failed. Falling back to OpenAI.", ex);
            return openAiProvider.correct(command);
        }
    }

    public record CorrectionCommand(
            String correctionType,
            String sourceType,
            Long sourceRefId,
            Long applicationCaseId,
            ApplicationCase applicationCase,
            String originalText,
            String questionText
    ) {
    }

    public record CorrectionPayload(
            String improvedText,
            String summary,
            java.util.List<String> issues,
            java.util.List<String> changeReasons,
            java.util.List<String> suggestions,
            Usage usage
    ) {
    }

    public record Usage(String model, int inputTokens, int outputTokens, int totalTokens) {
    }
}
