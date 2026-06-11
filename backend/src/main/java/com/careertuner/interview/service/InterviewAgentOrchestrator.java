package com.careertuner.interview.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.interview.domain.InterviewAgentStep;
import com.careertuner.interview.domain.InterviewQuestion;
import com.careertuner.interview.domain.InterviewSession;
import com.careertuner.interview.domain.InterviewTrainingSample;
import com.careertuner.interview.mapper.InterviewMapper;
import com.careertuner.interview.rag.InterviewKnowledgeService;
import com.careertuner.interview.training.InterviewTrainingMapper;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * 자율 면접 에이전트 오케스트레이터.
 *
 * <p>정책({@link AgentPolicy})이 현재 상태를 보고 다음 액션을 결정하는 루프로 답변을 평가한다.
 * 고정 순서(Retriever→Evaluator→Critic)는 분기 없는 한 경로로 그대로 포함되며, 상황에 따라
 * 재평가(REEVALUATE)나 추가 탐색 권장(PROBE) 으로 분기한다. 각 단계는 trace 에 기록한다.
 *
 * <p>기본 정책은 규칙 기반({@link RulePolicy}) 이고, 시연용 LLM Planner 는 {@code planner=llm}
 * 설정으로 교체한다(Phase 3). 어떤 액션이 실패해도 면접 흐름을 끊지 않고 원 결과로 폴백한다.
 */
@Service
public class InterviewAgentOrchestrator {

    private static final String FEATURE_EVAL = "INTERVIEW_ANSWER_EVAL";
    private static final String FEATURE_CRITIC = "INTERVIEW_CRITIC";

    /** 무한 루프 방지 상한. (설정화는 Phase 3) */
    private static final int MAX_TURNS = 6;
    /** Critic 의 점수 조정폭이 이 값 이상이면 이견으로 보고 1회 재평가한다. */
    private static final int REEVAL_THRESHOLD = 20;
    /** 답변이 이 길이 미만이면 추가 탐색(PROBE) 을 권장한다. */
    private static final int WEAK_ANSWER_LEN = 40;
    /** 최종 점수가 이 값 미만이면 추가 탐색(PROBE) 을 권장한다. */
    private static final int WEAK_SCORE = 50;

    private static final String DONE = "DONE";
    private static final String FAILED = "FAILED";

    private final InterviewOpenAiClient aiClient;
    private final InterviewAiUsageLogService usageLog;
    private final InterviewMapper interviewMapper;
    private final InterviewKnowledgeService knowledgeService;
    private final InterviewTrainingMapper trainingMapper;
    private final ObjectMapper objectMapper;
    private final AgentPolicy policy;

    public InterviewAgentOrchestrator(InterviewOpenAiClient aiClient,
                                      InterviewAiUsageLogService usageLog,
                                      InterviewMapper interviewMapper,
                                      InterviewKnowledgeService knowledgeService,
                                      InterviewTrainingMapper trainingMapper,
                                      ObjectMapper objectMapper) {
        this.aiClient = aiClient;
        this.usageLog = usageLog;
        this.interviewMapper = interviewMapper;
        this.knowledgeService = knowledgeService;
        this.trainingMapper = trainingMapper;
        this.objectMapper = objectMapper;
        this.policy = new RulePolicy();
    }

    /** 면접 답변 한 건을 자율 루프로 평가한다. */
    public OrchestratedEvaluation evaluateAnswer(Long userId, InterviewSession session,
                                                 ApplicationCase applicationCase,
                                                 InterviewQuestion question, String answerText) {
        AgentContext ctx = new AgentContext(userId, session, applicationCase, question, answerText);

        // ── 자율 루프: 정책이 다음 액션을 고르고, 더 할 일이 없으면 FINISH ──
        while (ctx.turn < MAX_TURNS) {
            AgentAction action = policy.decideNext(ctx);
            if (action == AgentAction.FINISH) {
                break;
            }
            runAction(action, ctx);
            ctx.turn++;
        }

        // ── 학습 데이터 적재 (파인튜닝/평가 하니스 원천, best-effort) ──
        if (ctx.eval != null) {
            try {
                trainingMapper.insert(InterviewTrainingSample.builder()
                        .interviewSessionId(session.getId())
                        .questionId(question.getId())
                        .question(question.getQuestion())
                        .answerText(answerText)
                        .score(ctx.finalScore)
                        .feedback(ctx.eval.feedback())
                        .ragUsed(ctx.hasRag)
                        .model(ctx.eval.usage() == null ? null : ctx.eval.usage().model())
                        .build());
            } catch (RuntimeException ignored) {
                // 학습 데이터 적재 실패가 면접 평가를 막지 않는다.
            }
        }

        String feedback = ctx.eval == null ? "" : ctx.eval.feedback();
        String improved = ctx.eval == null ? "" : ctx.eval.improvedAnswer();
        return new OrchestratedEvaluation(ctx.finalScore, feedback, improved, ctx.criticVerdict, ctx.criticReason);
    }

    // ───── 액션 실행 ─────

    private void runAction(AgentAction action, AgentContext ctx) {
        switch (action) {
            case RETRIEVE -> runRetrieve(ctx);
            case EVALUATE -> runEvaluate(ctx);
            case CRITIC -> runCritic(ctx);
            case REEVALUATE -> runReevaluate(ctx);
            case PROBE -> runProbe(ctx);
            case FINISH -> { /* 루프에서 처리 */ }
        }
    }

    /** RAG 근거를 가져온다(best-effort). 근거가 없으면 단계 기록을 남기지 않고 다음으로 넘어간다. */
    private void runRetrieve(AgentContext ctx) {
        long start = System.currentTimeMillis();
        ctx.ragAttempted = true;
        String context = knowledgeService.retrieveContext(ctx.question.getQuestion() + "\n" + ctx.answerText);
        ctx.ragContext = context;
        if (context != null && !context.isBlank()) {
            ctx.hasRag = true;
            logStep(ctx, "RETRIEVER", "retrieve", DONE, "지식베이스 근거 주입", null, elapsed(start));
        }
    }

    /** Evaluator: 답변을 채점한다. */
    private void runEvaluate(AgentContext ctx) {
        long start = System.currentTimeMillis();
        try {
            InterviewOpenAiClient.AnswerEvaluation eval = aiClient.evaluateAnswer(
                    ctx.question.getQuestion(), ctx.answerText, ctx.applicationCase, ctx.ragContext);
            usageLog.recordSuccess(ctx.userId, ctx.caseId(), FEATURE_EVAL, eval.usage());
            ctx.eval = eval;
            ctx.evaluated = true;
            ctx.finalScore = eval.score();
            logStep(ctx, "EVALUATOR", "evaluate", DONE, "원 채점 " + eval.score() + "점",
                    detail(Map.of("score", eval.score(), "feedback", nullSafe(eval.feedback()))), elapsed(start));
        } catch (BusinessException ex) {
            usageLog.recordFailure(ctx.userId, ctx.caseId(), FEATURE_EVAL, ex.getMessage());
            // Evaluator 실패는 평가 자체가 불가능하므로 흐름을 끊는다(상위에서 처리).
            logStep(ctx, "EVALUATOR", "evaluate", FAILED, "채점 실패", null, elapsed(start));
            throw ex;
        }
    }

    /** Critic: 채점을 적대적으로 검증하고 점수를 조정한다(실패 시 원 점수 유지). */
    private void runCritic(AgentContext ctx) {
        long start = System.currentTimeMillis();
        ctx.critiqued = true;
        try {
            InterviewOpenAiClient.CritiqueResult crit = aiClient.critiqueEvaluation(
                    ctx.question.getQuestion(), ctx.answerText, ctx.eval.score(), ctx.eval.feedback());
            usageLog.recordSuccess(ctx.userId, ctx.caseId(), FEATURE_CRITIC, crit.usage());
            ctx.criticAdjusted = crit.adjustedScore();
            ctx.criticVerdict = crit.verdict();
            ctx.criticReason = crit.reason();
            ctx.finalScore = crit.adjustedScore();
            logStep(ctx, "CRITIC", "verify", DONE, "검증: " + crit.verdict() + " → " + crit.adjustedScore() + "점",
                    detail(Map.of("adjustedScore", crit.adjustedScore(),
                            "verdict", nullSafe(crit.verdict()), "reason", nullSafe(crit.reason()))), elapsed(start));
        } catch (BusinessException ex) {
            usageLog.recordFailure(ctx.userId, ctx.caseId(), FEATURE_CRITIC, ex.getMessage());
            logStep(ctx, "CRITIC", "verify", FAILED, "검증 실패 — 원 점수 유지", null, elapsed(start));
        }
    }

    /** REEVALUATE: Critic 과 원 채점이 크게 어긋나면 한 번 더 채점해 최종 점수를 재산정한다. */
    private void runReevaluate(AgentContext ctx) {
        long start = System.currentTimeMillis();
        ctx.reEvaluated = true;
        try {
            InterviewOpenAiClient.AnswerEvaluation re = aiClient.evaluateAnswer(
                    ctx.question.getQuestion(), ctx.answerText, ctx.applicationCase, ctx.ragContext);
            usageLog.recordSuccess(ctx.userId, ctx.caseId(), FEATURE_EVAL, re.usage());
            int before = ctx.finalScore;
            // 재평가와 Critic 조정값의 중간을 최종으로 채택해 한쪽으로 튀지 않게 한다.
            int reconciled = Math.round((re.score() + ctx.criticAdjusted) / 2.0f);
            ctx.finalScore = reconciled;
            // 피드백/개선답변은 더 최신인 재평가 결과로 갱신한다.
            ctx.eval = re;
            logStep(ctx, "EVALUATOR", "reevaluate", DONE,
                    "이견으로 재평가 " + before + " → " + reconciled + "점",
                    detail(Map.of("reScore", re.score(), "reconciled", reconciled)), elapsed(start));
        } catch (BusinessException ex) {
            usageLog.recordFailure(ctx.userId, ctx.caseId(), FEATURE_EVAL, ex.getMessage());
            logStep(ctx, "EVALUATOR", "reevaluate", FAILED, "재평가 실패 — 기존 점수 유지", null, elapsed(start));
        }
    }

    /** PROBE: 답변이 약하면 추가 탐색(꼬리질문) 을 권장하는 판단만 남긴다(실제 질문 생성은 사용자 트리거). */
    private void runProbe(AgentContext ctx) {
        long start = System.currentTimeMillis();
        ctx.probeFlagged = true;
        logStep(ctx, "PROBER", "probe", DONE, "답변이 약함 — 꼬리질문으로 추가 검증 권장",
                detail(Map.of("answerLength", ctx.answerText == null ? 0 : ctx.answerText.length(),
                        "score", ctx.finalScore)), elapsed(start));
    }

    // ───── 정책 ─────

    /** 다음 액션을 결정하는 정책. Phase 3 에서 LLM Planner 구현으로 교체 가능. */
    interface AgentPolicy {
        AgentAction decideNext(AgentContext ctx);
    }

    /** 규칙 기반 정책(운영 기본값). 상태를 보고 다음 액션을 결정한다. */
    static final class RulePolicy implements AgentPolicy {
        @Override
        public AgentAction decideNext(AgentContext ctx) {
            if (!ctx.ragAttempted) {
                return AgentAction.RETRIEVE;
            }
            if (!ctx.evaluated) {
                return AgentAction.EVALUATE;
            }
            if (!ctx.critiqued) {
                return AgentAction.CRITIC;
            }
            if (!ctx.reEvaluated && ctx.bigDisagreement()) {
                return AgentAction.REEVALUATE;
            }
            if (!ctx.probeFlagged && ctx.answerWeak()) {
                return AgentAction.PROBE;
            }
            return AgentAction.FINISH;
        }
    }

    // ───── 상태 / 액션 ─────

    /** 자율 루프가 한 답변을 평가하는 동안의 변경 가능한 상태. */
    static final class AgentContext {
        final Long userId;
        final InterviewSession session;
        final ApplicationCase applicationCase;
        final InterviewQuestion question;
        final String answerText;

        int turn = 0;
        int stepNo = 0;

        boolean ragAttempted = false;
        boolean hasRag = false;
        String ragContext = "";

        boolean evaluated = false;
        InterviewOpenAiClient.AnswerEvaluation eval;

        boolean critiqued = false;
        int criticAdjusted;
        String criticVerdict = "검증생략";
        String criticReason = "";

        boolean reEvaluated = false;
        boolean probeFlagged = false;

        int finalScore = 0;

        AgentContext(Long userId, InterviewSession session, ApplicationCase applicationCase,
                     InterviewQuestion question, String answerText) {
            this.userId = userId;
            this.session = session;
            this.applicationCase = applicationCase;
            this.question = question;
            this.answerText = answerText;
        }

        Long caseId() {
            return session.getApplicationCaseId();
        }

        boolean bigDisagreement() {
            return eval != null && Math.abs(eval.score() - criticAdjusted) >= REEVAL_THRESHOLD;
        }

        boolean answerWeak() {
            int len = answerText == null ? 0 : answerText.trim().length();
            return len < WEAK_ANSWER_LEN || finalScore < WEAK_SCORE;
        }
    }

    /** 자율 루프가 실행할 수 있는 액션. */
    enum AgentAction {
        RETRIEVE, EVALUATE, CRITIC, REEVALUATE, PROBE, FINISH
    }

    // ───── trace 기록 ─────

    private void logStep(AgentContext ctx, String agent, String action, String status,
                         String summary, String detailJson, Integer elapsedMs) {
        interviewMapper.insertAgentStep(InterviewAgentStep.builder()
                .interviewSessionId(ctx.session.getId())
                .questionId(ctx.question.getId())
                .stepNo(++ctx.stepNo)
                .agent(agent)
                .action(action)
                .status(status)
                .summary(summary)
                .detail(detailJson)
                .elapsedMs(elapsedMs)
                .build());
    }

    private int elapsed(long startMillis) {
        return (int) Math.min(Integer.MAX_VALUE, System.currentTimeMillis() - startMillis);
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
