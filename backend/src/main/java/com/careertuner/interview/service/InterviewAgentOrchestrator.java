package com.careertuner.interview.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.interview.domain.InterviewAgentStep;
import com.careertuner.interview.domain.InterviewQuestion;
import com.careertuner.interview.domain.InterviewSession;
import com.careertuner.interview.mapper.InterviewMapper;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * 멀티에이전트 답변 평가 오케스트레이터.
 * Evaluator(채점) → Critic(적대적 검증·점수 조정) 순으로 실행하고, 각 단계를 trace 에 기록한다.
 * Critic 이 실패해도 Evaluator 결과로 폴백한다(면접 흐름을 끊지 않는다).
 */
@Service
public class InterviewAgentOrchestrator {

    private static final String FEATURE_EVAL = "INTERVIEW_ANSWER_EVAL";
    private static final String FEATURE_CRITIC = "INTERVIEW_CRITIC";

    private final InterviewOpenAiClient aiClient;
    private final InterviewAiUsageLogService usageLog;
    private final InterviewMapper interviewMapper;
    private final ObjectMapper objectMapper;

    public InterviewAgentOrchestrator(InterviewOpenAiClient aiClient,
                                      InterviewAiUsageLogService usageLog,
                                      InterviewMapper interviewMapper,
                                      ObjectMapper objectMapper) {
        this.aiClient = aiClient;
        this.usageLog = usageLog;
        this.interviewMapper = interviewMapper;
        this.objectMapper = objectMapper;
    }

    public OrchestratedEvaluation evaluateAnswer(Long userId, InterviewSession session,
                                                 ApplicationCase applicationCase,
                                                 InterviewQuestion question, String answerText) {
        Long caseId = session.getApplicationCaseId();
        Long sid = session.getId();
        Long qid = question.getId();

        // ── 1단계: Evaluator ──
        InterviewOpenAiClient.AnswerEvaluation eval;
        try {
            eval = aiClient.evaluateAnswer(question.getQuestion(), answerText, applicationCase);
        } catch (BusinessException ex) {
            usageLog.recordFailure(userId, caseId, FEATURE_EVAL, ex.getMessage());
            throw ex;
        }
        usageLog.recordSuccess(userId, caseId, FEATURE_EVAL, eval.usage());
        logStep(sid, qid, 1, "EVALUATOR", "evaluate",
                "원 채점 " + eval.score() + "점",
                detail(Map.of("score", eval.score(), "feedback", nullSafe(eval.feedback()))));

        // ── 2단계: Critic (실패 시 원 점수 유지) ──
        int finalScore = eval.score();
        String verdict = "검증생략";
        String reason = "";
        try {
            InterviewOpenAiClient.CritiqueResult crit =
                    aiClient.critiqueEvaluation(question.getQuestion(), answerText, eval.score(), eval.feedback());
            usageLog.recordSuccess(userId, caseId, FEATURE_CRITIC, crit.usage());
            finalScore = crit.adjustedScore();
            verdict = crit.verdict();
            reason = crit.reason();
            logStep(sid, qid, 2, "CRITIC", "verify",
                    "검증: " + verdict + " → 최종 " + finalScore + "점",
                    detail(Map.of("adjustedScore", finalScore, "verdict", nullSafe(verdict), "reason", nullSafe(reason))));
        } catch (BusinessException ex) {
            usageLog.recordFailure(userId, caseId, FEATURE_CRITIC, ex.getMessage());
            logStep(sid, qid, 2, "CRITIC", "verify", "검증 실패 — 원 점수 유지", null);
        }

        return new OrchestratedEvaluation(finalScore, eval.feedback(), eval.improvedAnswer(), verdict, reason);
    }

    private void logStep(Long sessionId, Long questionId, int stepNo, String agent,
                         String action, String summary, String detailJson) {
        interviewMapper.insertAgentStep(InterviewAgentStep.builder()
                .interviewSessionId(sessionId)
                .questionId(questionId)
                .stepNo(stepNo)
                .agent(agent)
                .action(action)
                .summary(summary)
                .detail(detailJson)
                .build());
    }

    private String detail(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(new LinkedHashMap<>(map));
        } catch (JacksonException ex) {
            return null;
        }
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    /** 오케스트레이션 최종 결과(최종 점수 + Critic 판정). */
    public record OrchestratedEvaluation(int score, String feedback, String improvedAnswer,
                                         String criticVerdict, String criticReason) {
    }
}
