package com.careertuner.ai.common.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/** '선택 tier 부터 시작 + 하위 폴백' 순서 계산과 모델 파싱(fail-open) 검증. */
class AiProviderChainTest {

    private static final List<AiProviderTier> ORDER =
            List.of(AiProviderTier.CAREERTUNER, AiProviderTier.CLAUDE, AiProviderTier.OPENAI);

    @Test
    void autoUsesFullDefaultOrder() {
        assertThat(AiProviderChain.startingFrom(RequestedAiModel.AUTO, ORDER))
                .containsExactly(AiProviderTier.CAREERTUNER, AiProviderTier.CLAUDE, AiProviderTier.OPENAI);
    }

    @Test
    void careertunerStartsAtSelfAndKeepsFallback() {
        assertThat(AiProviderChain.startingFrom(RequestedAiModel.CAREERTUNER, ORDER))
                .containsExactly(AiProviderTier.CAREERTUNER, AiProviderTier.CLAUDE, AiProviderTier.OPENAI);
    }

    @Test
    void claudeSkipsSelfButKeepsOpenAiFallback() {
        assertThat(AiProviderChain.startingFrom(RequestedAiModel.CLAUDE, ORDER))
                .containsExactly(AiProviderTier.CLAUDE, AiProviderTier.OPENAI);
    }

    @Test
    void openAiIsolatesToOpenAiOnly() {
        assertThat(AiProviderChain.startingFrom(RequestedAiModel.OPENAI, ORDER))
                .containsExactly(AiProviderTier.OPENAI);
    }

    @Test
    void tierNotInDomainFallsOpenToFullOrder() {
        // 이 도메인이 제공하지 않는 tier(CAREERTUNER)를 골라도 화면이 깨지지 않게 기본 순서로 폴백.
        List<AiProviderTier> onlyClaudeOpenAi = List.of(AiProviderTier.CLAUDE, AiProviderTier.OPENAI);
        assertThat(AiProviderChain.startingFrom(RequestedAiModel.CAREERTUNER, onlyClaudeOpenAi))
                .containsExactly(AiProviderTier.CLAUDE, AiProviderTier.OPENAI);
    }

    @Test
    void parseIsCaseInsensitiveAndFailOpen() {
        assertThat(RequestedAiModel.parse("openai")).isEqualTo(RequestedAiModel.OPENAI);
        assertThat(RequestedAiModel.parse(" Claude ")).isEqualTo(RequestedAiModel.CLAUDE);
        assertThat(RequestedAiModel.parse("CAREERTUNER")).isEqualTo(RequestedAiModel.CAREERTUNER);
        assertThat(RequestedAiModel.parse(null)).isEqualTo(RequestedAiModel.AUTO);
        assertThat(RequestedAiModel.parse("")).isEqualTo(RequestedAiModel.AUTO);
        assertThat(RequestedAiModel.parse("gpt-9")).isEqualTo(RequestedAiModel.AUTO);
    }
}
