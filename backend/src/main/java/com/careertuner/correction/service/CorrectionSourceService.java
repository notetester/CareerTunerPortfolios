package com.careertuner.correction.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.correction.dto.CorrectionInterviewSourceResponse;
import com.careertuner.interview.domain.InterviewAnswer;
import com.careertuner.interview.domain.InterviewQuestion;
import com.careertuner.interview.domain.InterviewSession;
import com.careertuner.interview.mapper.InterviewMapper;

import lombok.RequiredArgsConstructor;

/** D가 소유한 면접 원본을 수정하지 않고 E 첨삭 입력 스냅샷으로만 읽는다. */
@Service
@RequiredArgsConstructor
public class CorrectionSourceService {

    private final InterviewMapper interviewMapper;

    @Transactional(readOnly = true)
    public CorrectionInterviewSourceResponse interviewAnswer(Long userId, Long answerId) {
        if (answerId == null || answerId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "면접 답변 ID가 올바르지 않습니다.");
        }
        InterviewAnswer answer = interviewMapper.findAnswerByIdAndUserId(answerId, userId);
        if (answer == null || answer.getAnswerText() == null || answer.getAnswerText().isBlank()) {
            throw notFound();
        }
        InterviewQuestion question = interviewMapper.findQuestionByIdAndUserId(answer.getQuestionId(), userId);
        if (question == null || !answer.getQuestionId().equals(question.getId())) {
            throw notFound();
        }
        InterviewSession session = interviewMapper.findSessionByIdAndUserId(question.getInterviewSessionId(), userId);
        if (session == null) {
            throw notFound();
        }
        return new CorrectionInterviewSourceResponse(
                answer.getId(),
                session.getApplicationCaseId(),
                session.getId(),
                question.getId(),
                question.getQuestion(),
                answer.getAnswerText(),
                answer.getScore(),
                answer.getFeedback(),
                answer.getCreatedAt());
    }

    private static BusinessException notFound() {
        return new BusinessException(ErrorCode.NOT_FOUND, "현재 계정의 활성 면접 답변을 찾을 수 없습니다.");
    }
}
