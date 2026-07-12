package com.careertuner.correction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.interview.domain.InterviewAnswer;
import com.careertuner.interview.domain.InterviewQuestion;
import com.careertuner.interview.domain.InterviewSession;
import com.careertuner.interview.mapper.InterviewMapper;

class CorrectionSourceServiceTest {

    private final InterviewMapper mapper = mock(InterviewMapper.class);
    private final CorrectionSourceService service = new CorrectionSourceService(mapper);

    @Test
    void returnsOnlyOwnedActiveCompletedAnswerChain() {
        when(mapper.findAnswerByIdAndUserId(55L, 7L)).thenReturn(
                InterviewAnswer.builder().id(55L).questionId(44L).answerText("저장 답변")
                        .score(71).feedback("근거 보강").submissionStatus("COMPLETED").build());
        when(mapper.findQuestionByIdAndUserId(44L, 7L)).thenReturn(
                InterviewQuestion.builder().id(44L).interviewSessionId(33L).question("저장 질문").build());
        when(mapper.findSessionByIdAndUserId(33L, 7L)).thenReturn(
                InterviewSession.builder().id(33L).applicationCaseId(22L).build());

        var source = service.interviewAnswer(7L, 55L);

        assertThat(source.applicationCaseId()).isEqualTo(22L);
        assertThat(source.originalText()).isEqualTo("저장 답변");
        assertThat(source.questionText()).isEqualTo("저장 질문");
        assertThat(source.feedback()).isEqualTo("근거 보강");
    }

    @Test
    void missingOrForeignAnswerIsNotExposed() {
        assertThatThrownBy(() -> service.interviewAnswer(7L, 999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
    }
}
