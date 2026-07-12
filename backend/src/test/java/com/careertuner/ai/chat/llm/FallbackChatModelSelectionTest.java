package com.careertuner.ai.chat.llm;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import com.careertuner.ai.common.model.RequestedAiModel;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.ollama.OllamaChatModel;

/** 챗봇 모델 선택 라우팅(요청 스코프 ThreadLocal → FallbackChatModel tier pin) + 폴백/안전망 검증. */
class FallbackChatModelSelectionTest {

    private final OllamaChatModel ollama = mock(OllamaChatModel.class);
    private final ChatModel anthropic = mock(ChatModel.class);
    private final ChatModel openAi = mock(ChatModel.class);
    private final ChatModel mockModel = mock(ChatModel.class);
    private final ChatModelSelectionTrace trace = new ChatModelSelectionTrace();
    private final ChatRequest req = mock(ChatRequest.class);
    private final ChatResponse resp = mock(ChatResponse.class);

    @SuppressWarnings("unchecked")
    private ObjectProvider<ChatModel> provider(ChatModel model) {
        ObjectProvider<ChatModel> p = mock(ObjectProvider.class);
        lenient().when(p.getIfAvailable()).thenReturn(model);
        return p;
    }

    private FallbackChatModel model(ChatModel anth, ChatModel oa) {
        return new FallbackChatModel(ollama, provider(anth), provider(oa), mockModel, trace);
    }

    @BeforeEach
    void setUp() {
        lenient().when(ollama.chat(any(ChatRequest.class))).thenReturn(resp);
        lenient().when(anthropic.chat(any(ChatRequest.class))).thenReturn(resp);
        lenient().when(openAi.chat(any(ChatRequest.class))).thenReturn(resp);
        lenient().when(mockModel.chat(any(ChatRequest.class))).thenReturn(resp);
    }

    @AfterEach
    void tearDown() {
        trace.clear();
    }

    @Test
    void autoPrefersOllama() {
        // 요청 스코프 미설정 = AUTO = 현행(자체 Ollama 우선).
        model(anthropic, openAi).chat(req);
        verify(ollama).chat(any(ChatRequest.class));
        verify(anthropic, never()).chat(any(ChatRequest.class));
        verify(openAi, never()).chat(any(ChatRequest.class));
    }

    @Test
    void claudeChoiceSkipsOllama() {
        trace.set(RequestedAiModel.CLAUDE);
        model(anthropic, openAi).chat(req);
        verify(anthropic).chat(any(ChatRequest.class));
        verify(ollama, never()).chat(any(ChatRequest.class));
    }

    @Test
    void openAiChoiceIsolatesToOpenAi() {
        trace.set(RequestedAiModel.OPENAI);
        model(anthropic, openAi).chat(req);
        verify(openAi).chat(any(ChatRequest.class));
        verify(ollama, never()).chat(any(ChatRequest.class));
        verify(anthropic, never()).chat(any(ChatRequest.class));
    }

    @Test
    void ollamaFailureFallsToClaudeOnAuto() {
        when(ollama.chat(any(ChatRequest.class))).thenThrow(new RuntimeException("ollama down"));
        model(anthropic, openAi).chat(req);
        verify(anthropic).chat(any(ChatRequest.class));
    }

    @Test
    void allProvidersUnavailableFallsToMockSafetyNet() {
        // 자체 실패 + Claude/OpenAI 미설정(null) → Mock 안전망(화면 안 깨짐).
        when(ollama.chat(any(ChatRequest.class))).thenThrow(new RuntimeException("ollama down"));
        model(null, null).chat(req);
        verify(mockModel).chat(any(ChatRequest.class));
    }
}
