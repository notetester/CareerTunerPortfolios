package com.careertuner.correction.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.careertuner.correction.ai.CorrectionAiClient.CorrectionCommand;
import com.careertuner.correction.ai.CorrectionAiClient.CorrectionPayload;
import com.careertuner.correction.ai.CorrectionAiClient.Usage;

class CorrectionAiClientTest {

    @Test
    @DisplayName("self provider disabled by default, so OpenAI path is used")
    void correct_usesOpenAiByDefault() {
        CorrectionAiProperties properties = new CorrectionAiProperties();
        OpenAiCorrectionProvider openAi = mock(OpenAiCorrectionProvider.class);
        SelfLlmCorrectionProvider self = mock(SelfLlmCorrectionProvider.class);
        CorrectionAiClient client = new CorrectionAiClient(properties, openAi, self);
        CorrectionCommand command = command();
        CorrectionPayload expected = payload("openai");
        when(openAi.correct(command)).thenReturn(expected);

        CorrectionPayload actual = client.correct(command);

        assertThat(actual).isSameAs(expected);
        verify(openAi).correct(command);
    }

    @Test
    @DisplayName("self provider failure falls back to OpenAI when fallback is enabled")
    void correct_fallsBackToOpenAiWhenSelfFails() {
        CorrectionAiProperties properties = new CorrectionAiProperties();
        properties.setProvider("self");
        properties.getSelf().setBaseUrl("http://localhost:11434");
        OpenAiCorrectionProvider openAi = mock(OpenAiCorrectionProvider.class);
        SelfLlmCorrectionProvider self = mock(SelfLlmCorrectionProvider.class);
        CorrectionAiClient client = new CorrectionAiClient(properties, openAi, self);
        CorrectionCommand command = command();
        CorrectionPayload expected = payload("openai");
        when(self.correct(command)).thenThrow(new IllegalStateException("boom"));
        when(openAi.correct(command)).thenReturn(expected);

        CorrectionPayload actual = client.correct(command);

        assertThat(actual).isSameAs(expected);
        verify(self).correct(command);
        verify(openAi).correct(command);
    }

    @Test
    @DisplayName("self provider failure is propagated when fallback is disabled")
    void correct_throwsWhenFallbackDisabled() {
        CorrectionAiProperties properties = new CorrectionAiProperties();
        properties.setProvider("self");
        properties.setFallbackEnabled(false);
        properties.getSelf().setBaseUrl("http://localhost:11434");
        OpenAiCorrectionProvider openAi = mock(OpenAiCorrectionProvider.class);
        SelfLlmCorrectionProvider self = mock(SelfLlmCorrectionProvider.class);
        CorrectionAiClient client = new CorrectionAiClient(properties, openAi, self);
        CorrectionCommand command = command();
        when(self.correct(command)).thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> client.correct(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");
    }

    private CorrectionCommand command() {
        return new CorrectionCommand("SELF_INTRO", "DIRECT_INPUT", null, null, null, "original", null);
    }

    private CorrectionPayload payload(String model) {
        return new CorrectionPayload("improved", "summary", List.of(), List.of(), List.of(), new Usage(model, 1, 2, 3));
    }
}
