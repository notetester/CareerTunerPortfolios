package com.careertuner.ai.autoprep.handler;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.careertuner.ai.autoprep.PrepProgress;
import com.careertuner.ai.autoprep.PrepStepContext;
import com.careertuner.ai.autoprep.PrepStepHandler;
import com.careertuner.ai.autoprep.PrepStepResult;
import com.careertuner.interview.dto.CreateInterviewSessionRequest;
import com.careertuner.interview.dto.GenerateQuestionsRequest;
import com.careertuner.interview.dto.InterviewQuestionResponse;
import com.careertuner.interview.dto.InterviewSessionResponse;
import com.careertuner.interview.service.InterviewService;

import lombok.RequiredArgsConstructor;

/**
 * ⑤ 가상 면접 (D). 지원 건 기반으로 면접 세션을 만들고 AI 예상 질문을 생성한다(자체 LLM + 폴백).
 * 지원 건이 없으면 건너뛴다. mode 는 두뇌가 정한 슬롯(BASIC/JOB/PRESSURE…)을 따른다.
 * 세션 생성 → 질문 생성이 실제 2단계라 서브스텝도 진짜로 나뉜다.
 */
@Component
@RequiredArgsConstructor
public class InterviewPrepHandler implements PrepStepHandler {

    private final InterviewService interviewService;

    @Override
    public String key() {
        return "INTERVIEW";
    }

    @Override
    public PrepStepResult handle(PrepStepContext context, PrepProgress progress) {
        if (context.applicationCaseId() == null) {
            return PrepStepResult.skipped("INTERVIEW", "지원 건이 없어 건너뜀 — 공고를 먼저 등록하세요.");
        }
        long start = System.nanoTime();
        String mode = (context.slots().mode() == null || context.slots().mode().isBlank())
                ? "BASIC" : context.slots().mode();

        progress.substep("세션 준비", "면접 모드 " + mode + " 세션 생성");
        InterviewSessionResponse session = interviewService.createSession(
                context.userId(), new CreateInterviewSessionRequest(context.applicationCaseId(), mode));

        progress.substep("질문 생성", "지식베이스 근거 + 예상 질문 생성");
        List<InterviewQuestionResponse> questions = interviewService.generateQuestions(
                context.userId(), session.id(), new GenerateQuestionsRequest(null, null));

        long ms = (System.nanoTime() - start) / 1_000_000;
        return PrepStepResult.done("INTERVIEW",
                "면접 세션 생성 + 예상 질문 " + questions.size() + "개",
                Map.of("session", session, "questions", questions), ms);
    }
}
