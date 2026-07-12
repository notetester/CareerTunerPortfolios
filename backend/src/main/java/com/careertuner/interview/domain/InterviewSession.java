package com.careertuner.interview.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewSession {

    private Long id;
    private Long applicationCaseId;
    private String mode;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private Integer totalScore;
    /** interview_session.report (JSON 컬럼) 원문 문자열. */
    private String report;
    private LocalDateTime createdAt;
    /** 복원(=복습) 마지막 시각. NULL이면 복습 안 함. */
    private LocalDateTime lastResumedAt;

    /** 비영속(목록 조회 계산값): 이 세션 답변 점수 평균. 리포트 미생성(total_score=null) 시 카드 점수 폴백용. */
    private Integer avgAnswerScore;

    /** 비영속(목록 조회 계산값): 이 세션 음성 면접 점수 평균(interview_media_analysis kind=VOICE). */
    private Integer avgVoiceScore;

    /** 비영속(목록 조회 계산값): 생성된 질문 수. */
    private Integer totalQuestions;

    /** 비영속(목록 조회 계산값): 답변이 하나 이상 존재하는 질문 수. */
    private Integer answeredQuestions;

    /** 비영속(목록 조회 계산값): 질문이 있고 모든 질문에 답변했는지 여부. */
    private Boolean finished;
}
