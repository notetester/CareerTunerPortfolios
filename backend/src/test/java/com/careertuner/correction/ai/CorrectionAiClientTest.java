package com.careertuner.correction.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.careertuner.correction.ai.CorrectionAiClient.CorrectionCommand;
import com.careertuner.correction.ai.CorrectionAiClient.CorrectionPayload;
import com.careertuner.correction.ai.CorrectionAiClient.Usage;
import com.careertuner.correction.ai.SelfCorrectionOutputParser.InvalidOutputException;
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
        verify(fixture.self, never()).correct(any(), any(), any());
    }

    @Test
    @DisplayName("primary 8B success stops the fallback chain")
    void correct_primarySuccess() {
        Fixture fixture = fixture(true);
        CorrectionPayload expected = payload("8b");
        when(fixture.self.correct(eq(fixture.command), eq("8b"), any(Duration.class))).thenReturn(expected);

        assertThat(fixture.client.correct(fixture.command)).isSameAs(expected);

        verify(fixture.self).correct(eq(fixture.command), eq("8b"), any(Duration.class));
        verify(fixture.self, never()).correct(eq(fixture.command), eq("3b"), any(Duration.class));
        verify(fixture.openAi, never()).correct(any());
    }

    @Test
    @DisplayName("primary schema failure retries 8B once and then uses 3B")
    void correct_retriesPrimarySchemaFailureThenUses3b() {
        Fixture fixture = fixture(true);
        CorrectionPayload expected = payload("3b");
        when(fixture.self.correct(eq(fixture.command), eq("8b"), any(Duration.class)))
                .thenThrow(new InvalidOutputException("invalid"));
        when(fixture.self.correct(eq(fixture.command), eq("3b"), any(Duration.class))).thenReturn(expected);

        assertThat(fixture.client.correct(fixture.command)).isSameAs(expected);

        verify(fixture.self, org.mockito.Mockito.times(2))
                .correct(eq(fixture.command), eq("8b"), any(Duration.class));
        verify(fixture.self).correct(eq(fixture.command), eq("3b"), any(Duration.class));
    }

    @Test
    @DisplayName("primary timeout skips the second 8B attempt and moves to 3B")
    void correct_primaryTimeoutSkipsRetry() {
        Fixture fixture = fixture(true);
        CorrectionPayload expected = payload("3b");
        when(fixture.self.correct(eq(fixture.command), eq("8b"), any(Duration.class)))
                .thenThrow(new SelfLlmCallException("timeout", false));
        when(fixture.self.correct(eq(fixture.command), eq("3b"), any(Duration.class))).thenReturn(expected);

        assertThat(fixture.client.correct(fixture.command)).isSameAs(expected);

        verify(fixture.self).correct(eq(fixture.command), eq("8b"), any(Duration.class));
        verify(fixture.self).correct(eq(fixture.command), eq("3b"), any(Duration.class));
    }

    @Test
    @DisplayName("3B failure uses OpenAI as the final fallback")
    void correct_fallsBackToOpenAiAfter3bFailure() {
        Fixture fixture = fixture(true);
        CorrectionPayload expected = payload("openai");
        when(fixture.self.correct(eq(fixture.command), eq("8b"), any(Duration.class)))
                .thenThrow(new SelfLlmCallException("primary down", false));
        when(fixture.self.correct(eq(fixture.command), eq("3b"), any(Duration.class)))
                .thenThrow(new SelfLlmCallException("fallback down", false));
        when(fixture.openAi.correct(fixture.command)).thenReturn(expected);

        assertThat(fixture.client.correct(fixture.command)).isSameAs(expected);

        verify(fixture.openAi).correct(fixture.command);
    }

    @Test
    @DisplayName("disabled fallback propagates the primary failure")
    void correct_throwsWhenFallbackDisabled() {
        Fixture fixture = fixture(true);
        fixture.properties.setFallbackEnabled(false);
        when(fixture.self.correct(eq(fixture.command), eq("8b"), any(Duration.class)))
                .thenThrow(new SelfLlmCallException("boom", false));

        assertThatThrownBy(() -> fixture.client.correct(fixture.command))
                .isInstanceOf(SelfLlmCallException.class)
                .hasMessage("boom");

        verify(fixture.openAi, never()).correct(any());
    }

    private Fixture fixture(boolean selfEnabled) {
        CorrectionAiProperties properties = new CorrectionAiProperties();
        properties.getSelf().setModel("8b");
        properties.getSelf().setFallbackModel("3b");
        properties.getSelf().setRetryBackoff(Duration.ZERO);
        if (selfEnabled) {
            properties.setProvider("self");
            properties.getSelf().setBaseUrl("http://localhost:11434");
        }
        OpenAiCorrectionProvider openAi = mock(OpenAiCorrectionProvider.class);
        SelfLlmCorrectionProvider self = mock(SelfLlmCorrectionProvider.class);
        CorrectionCommand command = command();
        return new Fixture(properties, openAi, self, new CorrectionAiClient(properties, openAi, self), command);
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
            CorrectionAiClient client,
            CorrectionCommand command
    ) {
    }
}
