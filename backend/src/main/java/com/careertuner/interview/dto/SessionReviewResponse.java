package com.careertuner.interview.dto;

import java.util.List;

import com.careertuner.interview.domain.InterviewAnswer;
import com.careertuner.interview.domain.InterviewQuestion;
import com.careertuner.interview.domain.InterviewSession;

/**
 * 지난 면접 세션 복기(리뷰) — 질문 + 저장된 모범답안 + 내 최신 답변/점수를 한 번에 보여준다.
 *
 * <p>최근 면접 기록에서 들어가 복습하는 용도. 라이브/블라인드 흐름에서 쓰는 {@code listQuestions} 와 달리
 * <b>모범답안을 포함</b>하므로(노출 의도) 별도 read-only 응답으로 분리했다.
 */
public record SessionReviewResponse(Long sessionId, String mode, List<Item> items) {

    public record Item(
            Long questionId,
            String question,
            String questionType,
            String modelAnswer,
            Long answerId,
            String answerText,
            String audioUrl,
            String videoUrl,
            Integer score,
            String feedback,
            String improvedAnswer,
            Integer voiceScore,
            Integer visualScore) {

        public static Item of(InterviewQuestion q, InterviewAnswer a,
                              Integer voiceScore, Integer visualScore) {
            return new Item(
                    q.getId(),
                    q.getQuestion(),
                    q.getQuestionType(),
                    q.getModelAnswer(),
                    a == null ? null : a.getId(),
                    a == null ? null : a.getAnswerText(),
                    a == null ? null : a.getAudioUrl(),
                    a == null ? null : a.getVideoUrl(),
                    a == null ? null : a.getScore(),
                    a == null ? null : a.getFeedback(),
                    a == null ? null : a.getImprovedAnswer(),
                    voiceScore,
                    visualScore);
        }
    }

    public static SessionReviewResponse of(InterviewSession session, List<Item> items) {
        return new SessionReviewResponse(session.getId(), session.getMode(), items);
    }
}
