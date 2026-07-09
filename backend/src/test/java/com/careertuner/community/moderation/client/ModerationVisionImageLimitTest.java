package com.careertuner.community.moderation.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.careertuner.applicationcase.service.OpenAiProperties;
import com.careertuner.community.moderation.config.OllamaProperties;
import com.careertuner.community.moderation.dto.ModerationImage;
import com.careertuner.interview.service.AnthropicProperties;

import tools.jackson.databind.ObjectMapper;

/**
 * Anthropic 이미지당 상한(base64 10MB) 가드 회귀 테스트.
 *
 * <p>{@code PostImageModerationService.MAX_IMAGE_BYTES} 는 원본 8MB 까지 통과시키는데, base64 는 약 4/3 배라
 * 원본 7.5MB 를 넘으면 Anthropic 이 400 으로 거부한다. 그 구간에서 Claude tier 를 호출하지 않고 건너뛰는지 고정한다.
 */
class ModerationVisionImageLimitTest {

    private static final int LIMIT = 10 * 1024 * 1024;

    private static ModerationImage imageOfBase64Length(int length) {
        return new ModerationImage("a".repeat(length), "image/png");
    }

    private static ModerationAnthropicClient realAnthropicClient() {
        return new ModerationAnthropicClient(new AnthropicProperties(), new ObjectMapper());
    }

    @Test
    @DisplayName("base64 가 정확히 10MB 면 상한 안 — 경계 포함")
    void withinLimitAtExactBoundary() {
        assertThat(realAnthropicClient().visionPayloadWithinLimit(List.of(imageOfBase64Length(LIMIT))))
                .isTrue();
    }

    @Test
    @DisplayName("base64 가 10MB + 1바이트면 상한 초과")
    void exceedsLimitJustOverBoundary() {
        assertThat(realAnthropicClient().visionPayloadWithinLimit(List.of(imageOfBase64Length(LIMIT + 1))))
                .isFalse();
    }

    @Test
    @DisplayName("여러 장 중 한 장만 초과해도 초과로 판정")
    void exceedsLimitWhenAnySingleImageIsTooLarge() {
        List<ModerationImage> images = List.of(imageOfBase64Length(16), imageOfBase64Length(LIMIT + 1));
        assertThat(realAnthropicClient().visionPayloadWithinLimit(images)).isFalse();
    }

    @Test
    @DisplayName("상한 초과 이미지는 Claude vision 을 호출하지 않고 OpenAI tier 로 넘어간다")
    void gatewaySkipsClaudeTierWhenImageExceedsLimit() {
        OllamaClient ollama = mock(OllamaClient.class);
        ModerationAnthropicClient anthropic = mock(ModerationAnthropicClient.class);
        ModerationOpenAiClient openAi = mock(ModerationOpenAiClient.class);
        MockModerationClient mockClient = mock(MockModerationClient.class);

        OllamaProperties ollamaProperties = new OllamaProperties();
        AnthropicProperties anthropicProperties = new AnthropicProperties();
        OpenAiProperties openAiProperties = new OpenAiProperties();

        List<ModerationImage> images = List.of(imageOfBase64Length(LIMIT + 1));

        // 1차 Ollama 는 실패 → 2차 Claude 로 내려온다.
        when(ollama.chatVision(anyString(), anyString(), anyList(), any()))
                .thenThrow(new IllegalStateException("ollama down"));
        when(anthropic.available()).thenReturn(true);
        when(anthropic.visionPayloadWithinLimit(images)).thenReturn(false);
        when(openAi.available()).thenReturn(true);
        when(openAi.chatVision(anyString(), anyString(), anyList(), any())).thenReturn("{\"category\":\"normal\"}");

        ModerationLlmGateway gateway = new ModerationLlmGateway(
                ollama, anthropic, openAi, mockClient, ollamaProperties, anthropicProperties, openAiProperties);

        ModerationLlmGateway.LlmReply reply =
                gateway.chatVision("system", "user", images, Map.of());

        // Claude 는 호출조차 되지 않아야 한다(400 + 재시도 3회 낭비 방지).
        verify(anthropic, never()).chatVision(anyString(), anyString(), anyList(), any());
        verify(openAi).chatVision(anyString(), anyString(), anyList(), any());
        assertThat(reply.mock()).isFalse();
        assertThat(reply.json()).contains("normal");
    }

    @Test
    @DisplayName("상한 이내 이미지는 Claude vision 을 정상 호출한다")
    void gatewayCallsClaudeTierWhenImageWithinLimit() {
        OllamaClient ollama = mock(OllamaClient.class);
        ModerationAnthropicClient anthropic = mock(ModerationAnthropicClient.class);
        ModerationOpenAiClient openAi = mock(ModerationOpenAiClient.class);
        MockModerationClient mockClient = mock(MockModerationClient.class);

        List<ModerationImage> images = List.of(imageOfBase64Length(1024));

        when(ollama.chatVision(anyString(), anyString(), anyList(), any()))
                .thenThrow(new IllegalStateException("ollama down"));
        when(anthropic.available()).thenReturn(true);
        when(anthropic.visionPayloadWithinLimit(images)).thenReturn(true);
        when(anthropic.chatVision(anyString(), anyString(), anyList(), any()))
                .thenReturn("{\"category\":\"ad\"}");

        ModerationLlmGateway gateway = new ModerationLlmGateway(
                ollama, anthropic, openAi, mockClient,
                new OllamaProperties(), new AnthropicProperties(), new OpenAiProperties());

        ModerationLlmGateway.LlmReply reply = gateway.chatVision("system", "user", images, Map.of());

        verify(anthropic).chatVision(anyString(), anyString(), anyList(), any());
        verify(openAi, never()).chatVision(anyString(), anyString(), anyList(), any());
        assertThat(reply.json()).contains("ad");
    }
}
