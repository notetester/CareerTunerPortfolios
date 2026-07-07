package com.careertuner.correction.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.careertuner.correction.ai.CorrectionAiClient.CorrectionCommand;
import com.careertuner.correction.ai.CorrectionAiClient.CorrectionPayload;
import com.careertuner.correction.ai.CorrectionAiClient.Usage;
import com.careertuner.correction.ai.SelfCorrectionOutputParser.InvalidOutputException;
import com.careertuner.correction.ai.SelfLlmCorrectionProvider.RepairContext;
import com.careertuner.correction.ai.SelfLlmCorrectionProvider.SelfLlmCallException;

class CorrectionAiClientTest {

    @Test
    @DisplayName("self provider disabled by default, so OpenAI path is used")
    void correct_usesOpenAiByDefault() {
        Fixture fixture = fixture(false);
        CorrectionPayload expected = payload("openai");
        when(fixture.openAi.correct(fixture.command)).thenReturn(expected);

        assertThat(fixture.client.correct(fixture.command)).isSameAs(expected);

        verify(fixture.openAi).correct(fixture.command);
        verify(fixture.self, never()).correct(any(), any(), any(), any());
        verify(fixture.anthropic, never()).correct(any());
    }

    @Test
    @DisplayName("3B success stops the fallback chain")
    void correct_selfSuccess() {
        Fixture fixture = fixture(true);
        CorrectionPayload expected = payload("3b");
        when(fixture.self.correct(eq(fixture.command), eq("3b"), any(Duration.class), isNull()))
                .thenReturn(expected);

        assertThat(fixture.client.correct(fixture.command)).isSameAs(expected);

        verify(fixture.self).correct(eq(fixture.command), eq("3b"), any(Duration.class), isNull());
        verify(fixture.anthropic, never()).correct(any());
        verify(fixture.openAi, never()).correct(any());
    }

    @Test
    @DisplayName("schema failure sends the validation reason and previous output to one 3B repair attempt")
    void correct_repairsSchemaFailureWithSameModel() {
        Fixture fixture = fixture(true);
        CorrectionPayload expected = payload("3b");
        InvalidOutputException invalid = new InvalidOutputException(
                "Correction self LLM output validation failed: root is missing changes.",
                "{\"status\":\"ok\"}");
        when(fixture.self.correct(eq(fixture.command), eq("3b"), any(Duration.class), isNull()))
                .thenThrow(invalid);
        when(fixture.self.correct(
                eq(fixture.command), eq("3b"), any(Duration.class), any(RepairContext.class)))
                .thenReturn(expected);

        assertThat(fixture.client.correct(fixture.command)).isSameAs(expected);

        ArgumentCaptor<RepairContext> repair = ArgumentCaptor.forClass(RepairContext.class);
        verify(fixture.self, times(2)).correct(
                eq(fixture.command), eq("3b"), any(Duration.class), repair.capture());
        RepairContext repairRequest = repair.getAllValues().get(1);
        assertThat(repairRequest.validationError()).contains("missing changes");
        assertThat(repairRequest.previousOutput()).isEqualTo("{\"status\":\"ok\"}");
        verify(fixture.anthropic, never()).correct(any());
    }

    @Test
    @DisplayName("non-retryable 3B failure moves directly to Anthropic")
    void correct_nonRetryableSelfFailureUsesAnthropic() {
        Fixture fixture = fixture(true);
        CorrectionPayload expected = payload("haiku");
        when(fixture.self.correct(eq(fixture.command), eq("3b"), any(Duration.class), isNull()))
                .thenThrow(new SelfLlmCallException("timeout", false));
        when(fixture.anthropic.configured()).thenReturn(true);
        when(fixture.anthropic.correct(fixture.command)).thenReturn(expected);

        assertThat(fixture.client.correct(fixture.command)).isSameAs(expected);

        verify(fixture.self).correct(eq(fixture.command), eq("3b"), any(Duration.class), isNull());
        verify(fixture.anthropic).correct(fixture.command);
        verify(fixture.openAi, never()).correct(any());
    }

    @Test
    @DisplayName("two invalid 3B outputs use Anthropic after the repair attempt")
    void correct_failedRepairUsesAnthropic() {
        Fixture fixture = fixture(true);
        CorrectionPayload expected = payload("haiku");
        when(fixture.self.correct(eq(fixture.command), eq("3b"), any(Duration.class), isNull()))
                .thenThrow(new InvalidOutputException("missing changes", "{}"));
        when(fixture.self.correct(
                eq(fixture.command), eq("3b"), any(Duration.class), any(RepairContext.class)))
                .thenThrow(new InvalidOutputException("changes is empty", "{}"));
        when(fixture.anthropic.configured()).thenReturn(true);
        when(fixture.anthropic.correct(fixture.command)).thenReturn(expected);

        assertThat(fixture.client.correct(fixture.command)).isSameAs(expected);

        verify(fixture.self, times(2)).correct(
                eq(fixture.command), eq("3b"), any(Duration.class), any());
        verify(fixture.anthropic).correct(fixture.command);
    }

    @Test
    @DisplayName("Anthropic failure uses OpenAI as the final fallback")
    void correct_fallsBackToOpenAiAfterAnthropicFailure() {
        Fixture fixture = fixture(true);
        CorrectionPayload expected = payload("openai");
        when(fixture.self.correct(eq(fixture.command), eq("3b"), any(Duration.class), isNull()))
                .thenThrow(new SelfLlmCallException("self down", false));
        when(fixture.anthropic.configured()).thenReturn(true);
        when(fixture.anthropic.correct(fixture.command))
                .thenThrow(new IllegalStateException("anthropic down"));
        when(fixture.openAi.correct(fixture.command)).thenReturn(expected);

        assertThat(fixture.client.correct(fixture.command)).isSameAs(expected);

        verify(fixture.openAi).correct(fixture.command);
    }

    @Test
    @DisplayName("disabled cloud fallback propagates the 3B failure")
    void correct_throwsWhenFallbackDisabled() {
        Fixture fixture = fixture(true);
        fixture.properties.setFallbackEnabled(false);
        when(fixture.self.correct(eq(fixture.command), eq("3b"), any(Duration.class), isNull()))
                .thenThrow(new SelfLlmCallException("boom", false));

        assertThatThrownBy(() -> fixture.client.correct(fixture.command))
                .isInstanceOf(SelfLlmCallException.class)
                .hasMessage("boom");

        verify(fixture.openAi, never()).correct(any());
    }

    @Test
    @DisplayName("zero totalTimeBudget gives the original timeout to both initial and repair attempts")
    void correct_zeroBudgetRunsRepairWithFullTimeout() {
        Fixture fixture = fixture(true);
        fixture.properties.getSelf().setTotalTimeBudget(Duration.ZERO);
        Duration fullTimeout = fixture.properties.getSelf().getTimeout();
        CorrectionPayload expected = payload("3b");
        when(fixture.self.correct(eq(fixture.command), eq("3b"), eq(fullTimeout), isNull()))
                .thenThrow(new InvalidOutputException("missing changes", "{}"));
        when(fixture.self.correct(
                eq(fixture.command), eq("3b"), eq(fullTimeout), any(RepairContext.class)))
                .thenReturn(expected);

        assertThat(fixture.client.correct(fixture.command)).isSameAs(expected);

        verify(fixture.self, times(2)).correct(
                eq(fixture.command), eq("3b"), eq(fullTimeout), any());
    }

    private Fixture fixture(boolean selfEnabled) {
        CorrectionAiProperties properties = new CorrectionAiProperties();
        properties.getSelf().setModel("3b");
        properties.getSelf().setRetryBackoff(Duration.ZERO);
        if (selfEnabled) {
            properties.setProvider("self");
            properties.getSelf().setBaseUrl("http://localhost:11434");
        }
        OpenAiCorrectionProvider openAi = mock(OpenAiCorrectionProvider.class);
        SelfLlmCorrectionProvider self = mock(SelfLlmCorrectionProvider.class);
        AnthropicCorrectionProvider anthropic = mock(AnthropicCorrectionProvider.class);
        CorrectionModelWarmupService warmup = mock(CorrectionModelWarmupService.class);
        CorrectionCommand command = command();
        return new Fixture(
                properties,
                openAi,
                self,
                anthropic,
                warmup,
                new CorrectionAiClient(properties, openAi, self, anthropic, warmup),
                command);
    }

    private CorrectionCommand command() {
        return new CorrectionCommand("SELF_INTRO", "DIRECT_INPUT", null, null, null, "original", null);
    }

    private CorrectionPayload payload(String model) {
        return new CorrectionPayload(
                "improved", "summary", List.of(), List.of(), List.of(), new Usage(model, 1, 2, 3));
    }

    private record Fixture(
            CorrectionAiProperties properties,
            OpenAiCorrectionProvider openAi,
            SelfLlmCorrectionProvider self,
            AnthropicCorrectionProvider anthropic,
            CorrectionModelWarmupService warmup,
            CorrectionAiClient client,
            CorrectionCommand command
    ) {
    }
}
