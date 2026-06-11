package com.careertuner.interview.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
            "REAL", "실전 면접",
            "RESUME", "자소서 기반 면접",
            "PORTFOLIO", "포트폴리오 기반 면접",
            "COMPANY", "기업 맞춤 면접");

    private final InterviewMapper interviewMapper;
    private final ApplicationCaseAccessService accessService;
    private final InterviewOpenAiClient aiClient;
    private final InterviewAiUsageLogService aiUsageLogService;
    private final InterviewAgentOrchestrator orchestrator;
    private final ObjectMapper objectMapper;

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
        for (InterviewOpenAiClient.GeneratedQuestion q : generated.questions()) {
            interviewMapper.insertQuestion(InterviewQuestion.builder()
                    .interviewSessionId(sessionId)
                    .question(q.question())
                    .questionType(q.type())
                    .sortOrder(order++)
                    .build());
        }
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
        // 사용자가 모범답안을 본 뒤 답했다면 그 모범답안을 만점 기준 답안지로 함께 넘긴다.
        InterviewAgentOrchestrator.OrchestratedEvaluation evaluation =
                orchestrator.evaluateAnswer(userId, session, applicationCase, question, request.answerText(),
                        blankToNull(request.modelAnswer()));

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

        InterviewOpenAiClient.ModelAnswer generated;
        try {
            generated = aiClient.generateModelAnswer(question.getQuestion(), applicationCase, modeLabel);
        } catch (BusinessException ex) {
            aiUsageLogService.recordFailure(userId, session.getApplicationCaseId(), FEATURE_MODEL_ANSWER, ex.getMessage());
            throw ex;
        }
        aiUsageLogService.recordSuccess(userId, session.getApplicationCaseId(), FEATURE_MODEL_ANSWER, generated.usage());
        return new ModelAnswerResponse(generated.modelAnswer());
    }

    // ───── 내부 헬퍼 ─────

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
