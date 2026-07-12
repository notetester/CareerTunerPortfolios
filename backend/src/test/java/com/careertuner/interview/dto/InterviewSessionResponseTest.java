package com.careertuner.interview.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.careertuner.interview.domain.InterviewSession;

class InterviewSessionResponseTest {

    @Test
    void mapsCalculatedListProgress() {
        InterviewSession session = InterviewSession.builder()
                .id(10L)
                .applicationCaseId(20L)
                .mode("BASIC")
                .totalQuestions(6)
                .answeredQuestions(1)
                .finished(false)
                .build();

        InterviewSessionResponse response = InterviewSessionResponse.from(session);

        assertThat(response.totalQuestions()).isEqualTo(6);
        assertThat(response.answeredQuestions()).isEqualTo(1);
        assertThat(response.finished()).isFalse();
    }

    @Test
    void newSessionUsesEmptyUnfinishedProgress() {
        InterviewSessionResponse response = InterviewSessionResponse.from(
                InterviewSession.builder().id(10L).applicationCaseId(20L).mode("BASIC").build());

        assertThat(response.totalQuestions()).isZero();
        assertThat(response.answeredQuestions()).isZero();
        assertThat(response.finished()).isFalse();
    }
}
