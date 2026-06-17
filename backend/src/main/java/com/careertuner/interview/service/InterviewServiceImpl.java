package com.careertuner.interview.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.applicationcase.service.ApplicationCaseAccessService;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.interview.domain.InterviewAnswer;
import com.careertuner.interview.domain.InterviewQuestion;
import com.careertuner.interview.domain.InterviewSession;
import com.careertuner.interview.dto.CreateInterviewSessionRequest;
import com.careertuner.interview.dto.GenerateFollowUpsRequest;
import com.careertuner.interview.dto.GenerateQuestionsRequest;
import com.careertuner.interview.dto.InterviewAgentStepResponse;
import com.careertuner.interview.dto.InterviewAnswerResponse;
import com.careertuner.interview.dto.InterviewProgressResponse;
import com.careertuner.interview.dto.InterviewQuestionResponse;
import com.careertuner.interview.dto.InterviewReportResponse;
import com.careertuner.interview.dto.InterviewSessionResponse;
import com.careertuner.interview.dto.ModelAnswerResponse;
import com.careertuner.interview.dto.SubmitAnswerRequest;
import com.careertuner.interview.mapper.InterviewMapper;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class InterviewServiceImpl implements InterviewService {

    private static final int DEFAULT_QUESTION_COUNT = 6;
    private static final int MAX_QUESTION_COUNT = 15;
    private static final int DEFAULT_FOLLOWUP_COUNT = 2;
    private static final int MAX_FOLLOWUP_COUNT = 5;

    private static final String FEATURE_QUESTION = "INTERVIEW_QUESTION_GEN";
    private static final String FEATURE_FOLLOWUP = "INTERVIEW_FOLLOWUP_GEN";
    private static final String FEATURE_REPORT = "INTERVIEW_REPORT";
    private static final String FEATURE_MODEL_ANSWER = "INTERVIEW_MODEL_ANSWER";

    private static final Map<String, String> MODE_LABELS = Map.of(
            "BASIC", "기본 면접",
            "JOB", "직무 면접",
            "PERSONALITY", "인성 면접",
            "PRESSURE", "압박 면접",
            "RESUME", "자소서 기반 면접",
            "COMPANY", "기업 맞춤 면접");

    private final InterviewMapper interviewMapper;
    private final ApplicationCaseAccessService accessService;
    private final InterviewOpenAiClient aiClient;
    private final InterviewAiUsageLogService aiUsageLogService;
    private final InterviewAgentOrchestrator orchestrator;
    private final ObjectMapper objectMapper;
    private final InterviewBackgroundExecutor backgroundExecutor;

    @Override
    @Transactional
    public InterviewSessionResponse createSession(Long userId, CreateInterviewSessionRequest request) {
        accessService.requireOwned(userId, request.applicationCaseId());
        String mode = normalizeMode(request.mode());

        InterviewSession session = InterviewSession.builder()
                .applicationCaseId(request.applicationCaseId())
                .mode(mode)
                .startedAt(LocalDateTime.now())
                .build();
        interviewMapper.insertSession(session);
        return InterviewSessionResponse.from(session);
    }

    @Override
    public List<InterviewSessionResponse> listSessions(Long userId) {
        return interviewMapper.findSessionsByUserId(userId).stream()
                .map(InterviewSessionResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public List<InterviewQuestionResponse> generateQuestions(Long userId, Long sessionId,
                                                             GenerateQuestionsRequest request) {
        InterviewSession session = requireSession(userId, sessionId);
        ApplicationCase applicationCase = accessService.requireOwned(userId, session.getApplicationCaseId());
        String postingText = accessService.sourceText(session.getApplicationCaseId());
        int count = resolveCount(request.count());
        String modeLabel = MODE_LABELS.getOrDefault(session.getMode(), session.getMode());

        InterviewOpenAiClient.GeneratedQuestions generated;
        try {
            generated = aiClient.generateQuestions(applicationCase, postingText, modeLabel, count);
        } catch (BusinessException ex) {
            aiUsageLogService.recordFailure(userId, session.getApplicationCaseId(), FEATURE_QUESTION, ex.getMessage());
            throw ex;
        }
        aiUsageLogService.recordSuccess(userId, session.getApplicationCaseId(), FEATURE_QUESTION, generated.usage());

        interviewMapper.deleteQuestionsBySessionId(sessionId);
        int order = 0;
        List<InterviewQuestion> inserted = new java.util.ArrayList<>();
        for (InterviewOpenAiClient.GeneratedQuestion q : generated.questions()) {
            InterviewQuestion entity = InterviewQuestion.builder()
                    .interviewSessionId(sessionId)
                    .question(q.question())
                    .questionType(q.type())
                    .sortOrder(order++)
                    .build();
            interviewMapper.insertQuestion(entity); // useGeneratedKeys 로 id 채워짐
            inserted.add(entity);
        }

        // 모범답안은 채점 기준 답안지이지만, 6개 일괄 생성이 느려 질문 표시를 막을 이유는 없다.
        // → 트랜잭션 커밋 이후 백그라운드에서 생성·저장한다. 유저는 질문을 바로 받아 본다.
        // 평가 시점까지 보통 완료되며, 미완 상태로 평가가 들어오면 submitAnswer 에서 단건 즉시 생성으로 기준을 보장한다.
        // (afterCommit 에 거는 이유: 백그라운드 스레드가 방금 INSERT 한 질문을 볼 수 있어야 갱신이 누락되지 않는다.)
        final Long bgUserId = userId;
        final InterviewSession bgSession = session;
        final ApplicationCase bgCase = applicationCase;
        final String bgModeLabel = modeLabel;
        final List<InterviewQuestion> bgQuestions = inserted;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                backgroundExecutor.run(() -> storeModelAnswers(bgUserId, bgSession, bgCase, bgModeLabel, bgQuestions));
            }
        });

        return listQuestions(userId, sessionId);
    }

    @Override
    public List<InterviewQuestionResponse> listQuestions(Long userId, Long sessionId) {
        requireSession(userId, sessionId);
        return interviewMapper.findQuestionsBySessionId(sessionId).stream()
                .map(InterviewQuestionResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public List<InterviewQuestionResponse> generateFollowUps(Long userId, Long questionId,
                                                             GenerateFollowUpsRequest request) {
        InterviewQuestion question = interviewMapper.findQuestionByIdAndUserId(questionId, userId);
        if (question == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "면접 질문을 찾을 수 없습니다.");
        }
        InterviewSession session = requireSession(userId, question.getInterviewSessionId());
        ApplicationCase applicationCase = accessService.requireOwned(userId, session.getApplicationCaseId());

        InterviewAnswer answer = interviewMapper.findLatestAnswerByQuestionId(questionId);
        if (answer == null || answer.getAnswerText() == null || answer.getAnswerText().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "꼬리 질문은 답변을 먼저 제출한 뒤 생성할 수 있습니다.");
        }
        int count = resolveFollowUpCount(request == null ? null : request.count());

        InterviewOpenAiClient.GeneratedQuestions generated;
        try {
            generated = aiClient.generateFollowUps(question.getQuestion(), answer.getAnswerText(),
                    applicationCase, count);
        } catch (BusinessException ex) {
            aiUsageLogService.recordFailure(userId, session.getApplicationCaseId(), FEATURE_FOLLOWUP, ex.getMessage());
            throw ex;
        }
        aiUsageLogService.recordSuccess(userId, session.getApplicationCaseId(), FEATURE_FOLLOWUP, generated.usage());

        Integer maxOrder = interviewMapper.findMaxSortOrder(question.getInterviewSessionId());
        int order = maxOrder == null ? 0 : maxOrder;
        for (InterviewOpenAiClient.GeneratedQuestion q : generated.questions()) {
            interviewMapper.insertQuestion(InterviewQuestion.builder()
                    .interviewSessionId(question.getInterviewSessionId())
                    .parentQuestionId(question.getId())
                    .question(q.question())
                    .questionType("FOLLOW_UP")
                    .sortOrder(++order)
                    .build());
        }
        return listQuestions(userId, question.getInterviewSessionId());
    }

    @Override
    @Transactional
    public InterviewAnswerResponse submitAnswer(Long userId, Long questionId, SubmitAnswerRequest request) {
        InterviewQuestion question = interviewMapper.findQuestionByIdAndUserId(questionId, userId);
        if (question == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "면접 질문을 찾을 수 없습니다.");
        }
        InterviewSession session = requireSession(userId, question.getInterviewSessionId());
        ApplicationCase applicationCase = accessService.requireOwned(userId, session.getApplicationCaseId());

        // 멀티에이전트: Evaluator → Critic(적대적 검증) 으로 최종 점수를 산출하고 단계를 trace 에 남긴다.
        // 만점 기준 답안지(모범답안)는 프론트가 보낸 값 > 질문에 저장된 값 순으로 사용한다.
        // 저장된 값을 쓰므로 블라인드인 복습 테스트도 모범답안 기준으로 채점된다.
        String referenceModelAnswer = blankToNull(request.modelAnswer());
        if (referenceModelAnswer == null) {
            referenceModelAnswer = blankToNull(question.getModelAnswer());
        }
        if (referenceModelAnswer == null) {
            // 백그라운드 모범답안 생성이 아직이면(빠른 평가) 채점 기준 보장을 위해 단건 즉시 생성한다.
            referenceModelAnswer = generateModelAnswerForGrading(userId, session, applicationCase, question);
        }
        InterviewAgentOrchestrator.OrchestratedEvaluation evaluation =
                orchestrator.evaluateAnswer(userId, session, applicationCase, question, request.answerText(),
                        referenceModelAnswer);

        InterviewAnswer answer = InterviewAnswer.builder()
                .questionId(questionId)
                .answerText(request.answerText())
                .audioUrl(blankToNull(request.audioUrl()))
                .videoUrl(blankToNull(request.videoUrl()))
                .score(evaluation.score())
                .feedback(evaluation.feedback())
                .improvedAnswer(evaluation.improvedAnswer())
                .build();
        interviewMapper.insertAnswer(answer);
        return InterviewAnswerResponse.from(answer);
    }

    @Override
    public InterviewProgressResponse getProgress(Long userId, Long sessionId) {
        requireSession(userId, sessionId);
        List<InterviewQuestion> questions = interviewMapper.findQuestionsBySessionId(sessionId);
        Set<Long> answeredQuestionIds = interviewMapper.findAnswersBySessionId(sessionId).stream()
                .map(InterviewAnswer::getQuestionId)
                .collect(java.util.stream.Collectors.toSet());

        InterviewQuestion next = null;
        int answered = 0;
        for (InterviewQuestion q : questions) {
            if (answeredQuestionIds.contains(q.getId())) {
                answered++;
            } else if (next == null) {
                next = q;
            }
        }
        boolean finished = !questions.isEmpty() && next == null;
        return new InterviewProgressResponse(
                sessionId,
                questions.size(),
                answered,
                finished,
                next == null ? null : InterviewQuestionResponse.from(next));
    }

    @Override
    public List<InterviewAgentStepResponse> getAgentSteps(Long userId, Long sessionId) {
        requireSession(userId, sessionId);
        return interviewMapper.findAgentStepsBySessionId(sessionId).stream()
                .map(InterviewAgentStepResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public InterviewReportResponse getReport(Long userId, Long sessionId) {
        InterviewSession session = requireSession(userId, sessionId);

        // 이미 생성된 리포트가 있으면 그대로 반환한다.
        if (session.getReport() != null && !session.getReport().isBlank()) {
            InterviewReportResponse cached = readReport(session.getReport());
            if (cached != null) {
                return cached;
            }
        }

        List<InterviewQuestion> questions = interviewMapper.findQuestionsBySessionId(sessionId);
        List<InterviewAnswer> answers = interviewMapper.findAnswersBySessionId(sessionId);
        if (answers.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "평가된 면접 답변이 없습니다. 먼저 답변을 진행해 주세요.");
        }

        String transcript = buildTranscript(questions, answers);

        InterviewOpenAiClient.ReportPayload payload;
        try {
            payload = aiClient.generateReport(transcript);
        } catch (BusinessException ex) {
            aiUsageLogService.recordFailure(userId, session.getApplicationCaseId(), FEATURE_REPORT, ex.getMessage());
            throw ex;
        }
        aiUsageLogService.recordSuccess(userId, session.getApplicationCaseId(), FEATURE_REPORT, payload.usage());

        Integer previousScore = interviewMapper.findLatestScoredSessionScore(session.getApplicationCaseId(), sessionId);
        List<InterviewReportResponse.Category> categories = payload.categories().stream()
                .map(c -> new InterviewReportResponse.Category(c.label(), c.score()))
                .toList();

        InterviewReportResponse response = new InterviewReportResponse(
                payload.totalScore(),
                previousScore,
                answers.size(),
                durationLabel(session.getStartedAt()),
                categories,
                payload.summaryFeedback());

        interviewMapper.updateSessionResult(sessionId, payload.totalScore(), writeReport(response), LocalDateTime.now());
        return response;
    }

    @Override
    public ModelAnswerResponse getModelAnswer(Long userId, Long questionId) {
        InterviewQuestion question = interviewMapper.findQuestionByIdAndUserId(questionId, userId);
        if (question == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "면접 질문을 찾을 수 없습니다.");
        }
        InterviewSession session = requireSession(userId, question.getInterviewSessionId());
        ApplicationCase applicationCase = accessService.requireOwned(userId, session.getApplicationCaseId());
        String modeLabel = MODE_LABELS.getOrDefault(session.getMode(), session.getMode());

        // 이미 저장된 모범답안이 있으면 재사용한다 — 화면 표시와 채점 기준이 같은 답안이도록 보장한다.
        if (question.getModelAnswer() != null && !question.getModelAnswer().isBlank()) {
            return new ModelAnswerResponse(question.getModelAnswer());
        }

        InterviewOpenAiClient.ModelAnswer generated;
        try {
            generated = aiClient.generateModelAnswer(question.getQuestion(), applicationCase, modeLabel);
        } catch (BusinessException ex) {
            aiUsageLogService.recordFailure(userId, session.getApplicationCaseId(), FEATURE_MODEL_ANSWER, ex.getMessage());
            throw ex;
        }
        aiUsageLogService.recordSuccess(userId, session.getApplicationCaseId(), FEATURE_MODEL_ANSWER, generated.usage());

        // 채점 기준으로 재사용하도록 저장한다. model_answer 컬럼 적용 전이면 조용히 건너뛴다(기능은 그대로 동작).
        try {
            interviewMapper.updateQuestionModelAnswer(questionId, generated.modelAnswer());
        } catch (RuntimeException ignored) {
            // 컬럼 미적용 등 저장 실패가 모범답안 표시를 막지 않는다.
        }
        return new ModelAnswerResponse(generated.modelAnswer());
    }

    // ───── 내부 헬퍼 ─────

    /**
     * 질문들의 모범답안을 한 번에 생성해 각 질문(model_answer)에 저장한다.
     * 채점 기준 답안지로 재사용된다. 실패(모델 호출/컬럼 미적용)는 조용히 넘겨 질문 생성을 막지 않는다.
     */
    private void storeModelAnswers(Long userId, InterviewSession session, ApplicationCase applicationCase,
                                   String modeLabel, List<InterviewQuestion> questions) {
        if (questions.isEmpty()) {
            return;
        }
        List<String> texts = questions.stream().map(InterviewQuestion::getQuestion).toList();
        InterviewOpenAiClient.GeneratedModelAnswers answers;
        try {
            answers = aiClient.generateModelAnswers(texts, applicationCase, modeLabel);
        } catch (BusinessException ex) {
            aiUsageLogService.recordFailure(userId, session.getApplicationCaseId(), FEATURE_MODEL_ANSWER, ex.getMessage());
            return; // 일괄 생성 실패 — 이후 개별 "모범답안 보기" 시 지연 생성으로 폴백한다.
        }
        aiUsageLogService.recordSuccess(userId, session.getApplicationCaseId(), FEATURE_MODEL_ANSWER, answers.usage());

        List<String> list = answers.modelAnswers();
        for (int i = 0; i < questions.size() && i < list.size(); i++) {
            String answer = list.get(i);
            if (answer == null || answer.isBlank()) {
                continue;
            }
            try {
                interviewMapper.updateQuestionModelAnswer(questions.get(i).getId(), answer);
            } catch (RuntimeException ignored) {
                // model_answer 컬럼 미적용 등 — 저장 실패가 질문 생성을 막지 않는다.
            }
        }
    }

    /**
     * 평가 직전 모범답안이 없으면(백그라운드 미완 등) 단건을 즉시 생성·저장해 채점 기준을 보장한다.
     * 실패하면 null 을 반환해 기준 없이 평가하도록 둔다(평가 자체를 막지 않는다).
     */
    private String generateModelAnswerForGrading(Long userId, InterviewSession session,
                                                 ApplicationCase applicationCase, InterviewQuestion question) {
        String modeLabel = MODE_LABELS.getOrDefault(session.getMode(), session.getMode());
        try {
            InterviewOpenAiClient.ModelAnswer generated =
                    aiClient.generateModelAnswer(question.getQuestion(), applicationCase, modeLabel);
            aiUsageLogService.recordSuccess(userId, session.getApplicationCaseId(), FEATURE_MODEL_ANSWER, generated.usage());
            try {
                interviewMapper.updateQuestionModelAnswer(question.getId(), generated.modelAnswer());
            } catch (RuntimeException ignored) {
                // 컬럼 미적용 등 저장 실패는 채점을 막지 않는다.
            }
            return generated.modelAnswer();
        } catch (BusinessException ex) {
            aiUsageLogService.recordFailure(userId, session.getApplicationCaseId(), FEATURE_MODEL_ANSWER, ex.getMessage());
            return null;
        }
    }

    private InterviewSession requireSession(Long userId, Long sessionId) {
        InterviewSession session = interviewMapper.findSessionByIdAndUserId(sessionId, userId);
        if (session == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "면접 세션을 찾을 수 없습니다.");
        }
        return session;
    }

    private String normalizeMode(String mode) {
        String upper = mode == null ? "" : mode.trim().toUpperCase();
        if (!MODE_LABELS.containsKey(upper)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 면접 모드입니다.");
        }
        return upper;
    }

    private int resolveCount(Integer count) {
        if (count == null || count <= 0) {
            return DEFAULT_QUESTION_COUNT;
        }
        return Math.min(count, MAX_QUESTION_COUNT);
    }

    private int resolveFollowUpCount(Integer count) {
        if (count == null || count <= 0) {
            return DEFAULT_FOLLOWUP_COUNT;
        }
        return Math.min(count, MAX_FOLLOWUP_COUNT);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String buildTranscript(List<InterviewQuestion> questions, List<InterviewAnswer> answers) {
        Map<Long, InterviewAnswer> answerByQuestion = new LinkedHashMap<>();
        for (InterviewAnswer a : answers) {
            answerByQuestion.put(a.getQuestionId(), a);
        }
        StringBuilder sb = new StringBuilder("아래는 모의면접 질문과 지원자 답변입니다.\n\n");
        int idx = 1;
        for (InterviewQuestion q : questions) {
            InterviewAnswer a = answerByQuestion.get(q.getId());
            if (a == null) {
                continue;
            }
            sb.append("Q").append(idx).append(". ").append(q.getQuestion()).append("\n");
            sb.append("A").append(idx).append(". ").append(a.getAnswerText() == null ? "" : a.getAnswerText()).append("\n");
            if (a.getScore() != null) {
                sb.append("(개별 점수: ").append(a.getScore()).append(")\n");
            }
            sb.append("\n");
            idx++;
        }
        return sb.toString();
    }

    private String durationLabel(LocalDateTime startedAt) {
        if (startedAt == null) {
            return null;
        }
        long minutes = Math.max(0, Duration.between(startedAt, LocalDateTime.now()).toMinutes());
        return minutes + "분";
    }

    private String writeReport(InterviewReportResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JacksonException ex) {
            return null;
        }
    }

    private InterviewReportResponse readReport(String json) {
        try {
            return objectMapper.readValue(json, InterviewReportResponse.class);
        } catch (JacksonException ex) {
            return null;
        }
    }
}
