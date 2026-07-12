package com.careertuner.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.careertuner.applicationcase.service.ApplicationCaseAccessService;
import com.careertuner.file.service.FileService;
import com.careertuner.interview.domain.InterviewQuestion;
import com.careertuner.interview.domain.InterviewSession;
import com.careertuner.interview.dto.GenerateFollowUpsRequest;
import com.careertuner.interview.dto.GenerateQuestionsRequest;
import com.careertuner.interview.mapper.InterviewMapper;
import com.careertuner.interview.media.InterviewMediaMapper;
import com.careertuner.notification.service.NotificationService;

import tools.jackson.databind.ObjectMapper;

class InterviewAiOperationIdempotencyTest {

    private static final Path INTERVIEW_XML =
            Path.of("src/main/resources/mapper/interview/InterviewMapper.xml");
    private static final Path SCHEMA = Path.of("src/main/resources/db/schema.sql");
    private static final Path PATCH = Path.of(
            "src/main/resources/db/patches/20260712_interview_ai_operation_idempotency.sql");

    private final InterviewMapper mapper = mock(InterviewMapper.class);
    private final InterviewOpenAiClient aiClient = mock(InterviewOpenAiClient.class);
    private final InterviewAiUsageLogService usageLogService = mock(InterviewAiUsageLogService.class);
    private final InterviewServiceImpl service = new InterviewServiceImpl(
            mapper,
            mock(ApplicationCaseAccessService.class),
            aiClient,
            usageLogService,
            mock(InterviewAgentOrchestrator.class),
            new ObjectMapper(),
            mock(InterviewBackgroundExecutor.class),
            mock(NotificationService.class),
            mock(FileService.class),
            mock(InterviewMediaMapper.class));

    @Test
    void repeatedQuestionOperationReturnsCommittedResultWithoutModelOrUsage() {
        InterviewSession session = InterviewSession.builder()
                .id(11L).applicationCaseId(21L).mode("BASIC").build();
        InterviewQuestion question = InterviewQuestion.builder()
                .id(31L).interviewSessionId(11L).question("커밋된 질문")
                .questionType("EXPECTED").sortOrder(0).build();
        when(mapper.lockSessionByIdAndUserId(11L, 7L)).thenReturn(session);
        when(mapper.insertAiOperationReservation(7L, "INTERVIEW_QUESTION_GEN", 11L, "AI_USAGE:op-1"))
                .thenReturn(0);
        when(mapper.findQuestionsBySessionId(11L)).thenReturn(List.of(question));

        var result = service.generateQuestions(
                7L, 11L, new GenerateQuestionsRequest("BASIC", 6), "AI_USAGE:op-1");

        assertThat(result).extracting("id").containsExactly(31L);
        verify(aiClient, never()).generateQuestions(any(), anyString(), anyString(), anyInt());
        verify(usageLogService, never()).recordSuccess(any(), any(), anyString(), any());
        verify(mapper, never()).softDeleteQuestionsBySessionId(11L);
    }

    @Test
    void repeatedFollowUpOperationReturnsCommittedBatchWithoutModelOrUsage() {
        InterviewQuestion parent = InterviewQuestion.builder()
                .id(31L).interviewSessionId(11L).question("원 질문")
                .questionType("EXPECTED").sortOrder(0).build();
        InterviewQuestion child = InterviewQuestion.builder()
                .id(32L).interviewSessionId(11L).parentQuestionId(31L).question("커밋된 반박")
                .questionType("FOLLOW_UP").sortOrder(1).build();
        when(mapper.findQuestionByIdAndUserId(31L, 7L)).thenReturn(parent);
        when(mapper.lockSessionByIdAndUserId(11L, 7L)).thenReturn(
                InterviewSession.builder().id(11L).applicationCaseId(21L).mode("PRESSURE").build());
        when(mapper.lockQuestionByIdAndUserId(31L, 7L)).thenReturn(parent);
        when(mapper.insertAiOperationReservation(7L, "INTERVIEW_FOLLOWUP_GEN", 31L, "AI_USAGE:op-2"))
                .thenReturn(0);
        when(mapper.findQuestionsBySessionId(11L)).thenReturn(List.of(parent, child));

        var result = service.generateFollowUps(
                7L, 31L, new GenerateFollowUpsRequest(1), "AI_USAGE:op-2");

        assertThat(result).extracting("id").containsExactly(31L, 32L);
        verify(aiClient, never()).generateFollowUps(anyString(), anyString(), any(), anyInt(), anyBoolean());
        verify(usageLogService, never()).recordSuccess(any(), any(), anyString(), any());
        verify(mapper, never()).insertQuestion(any());
    }

    @Test
    void mapperSchemaAndPatchShareTheAtomicReservationContract() throws Exception {
        String xml = Files.readString(INTERVIEW_XML);
        String schema = Files.readString(SCHEMA);
        String patch = Files.readString(PATCH);

        assertThat(xml)
                .contains("<insert id=\"insertAiOperationReservation\">")
                .contains("INSERT IGNORE INTO interview_ai_operation")
                .contains("user_id, feature_type, target_id, operation_key");
        assertThat(schema)
                .contains("CREATE TABLE IF NOT EXISTS interview_ai_operation")
                .contains("UNIQUE KEY uk_interview_ai_operation (user_id, feature_type, target_id, operation_key)");
        assertThat(patch)
                .contains("CREATE TABLE IF NOT EXISTS interview_ai_operation")
                .contains("information_schema.COLUMNS")
                .contains("information_schema.STATISTICS")
                .contains("CHECK (guard_ok = 1)")
                .contains("'PASS', 'FAIL'");
    }
}
