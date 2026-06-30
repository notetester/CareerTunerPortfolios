package com.careertuner.ai.intake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.careertuner.ai.autoprep.AutoPrepIntakeService;
import com.careertuner.ai.chat.MyBatisChatMemoryStore;
import com.careertuner.applicationcase.service.ApplicationCaseService;

import dev.langchain4j.data.message.ChatMessage;

/**
 * #3 ② fork 루프 가드(A′) 검증. boundCaseId 인메모리가 비어도(재시작/READY세션/대화단절) DB의
 * application_case_id 가 현재 caseId 와 같으면 재fork 하지 않는다. 다르면 정상 fork. maybeFork 는
 * private 이라 ReflectionTestUtils 로 직접 호출(ask 전체 경로의 Ollama 의존 회피).
 */
@ExtendWith(MockitoExtension.class)
class IntakeForkGuardTest {

    @Mock
    IntakeChatAgent agent;
    @Mock
    IntakeSlotTrace trace;
    @Mock
    AutoPrepIntakeService autoPrepIntakeService;
    @Mock
    MyBatisChatMemoryStore memoryStore;
    @Mock
    ChatbotIntakeSlotMapper slotMapper;
    @Mock
    ApplicationCaseService applicationCaseService;
    @InjectMocks
    IntakeAskService service;

    @DisplayName("boundCaseId null + DB값 일치 → fork 안 함(가드 부활)")
    @Test
    void boundNull_dbMatch_noFork() {
        when(trace.boundCaseId()).thenReturn(null);
        when(memoryStore.findApplicationCaseId(100L)).thenReturn(5L);

        Long result = ReflectionTestUtils.invokeMethod(
                service, "maybeForkOnCaseConfirmed", 100L, 1L, 5L);

        assertThat(result).isEqualTo(100L);                 // 원본 대화 유지
        verify(memoryStore, never()).createConversation(any());
    }

    @DisplayName("boundCaseId null + DB값 다름 → 정상 fork")
    @Test
    void boundNull_dbDiffer_fork() {
        when(trace.boundCaseId()).thenReturn(null);
        when(memoryStore.findApplicationCaseId(100L)).thenReturn(9L);   // 다른 건
        when(memoryStore.getMessages(100L)).thenReturn(List.<ChatMessage>of());
        when(trace.entryOffset()).thenReturn(0);
        when(memoryStore.createConversation(1L)).thenReturn(200L);
        when(trace.fetchedCases()).thenReturn(List.of());

        Long result = ReflectionTestUtils.invokeMethod(
                service, "maybeForkOnCaseConfirmed", 100L, 1L, 5L);

        assertThat(result).isEqualTo(200L);                 // 새 대화로 fork
        verify(memoryStore).createConversation(1L);
    }
}
