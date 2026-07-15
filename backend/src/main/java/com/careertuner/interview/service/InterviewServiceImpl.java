package com.careertuner.interview.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.applicationcase.service.ApplicationCaseAccessService;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.file.domain.FileAsset;
import com.careertuner.file.service.FileService;
import com.careertuner.interview.domain.InterviewAnswer;
import com.careertuner.interview.domain.InterviewMediaAnalysis;
import com.careertuner.interview.domain.InterviewPreparationContextSource;
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
import com.careertuner.interview.dto.InterviewDispatchTarget;
import com.careertuner.interview.dto.ModelAnswerResponse;
import com.careertuner.interview.dto.SessionPageResponse;
import com.careertuner.interview.dto.SessionReviewResponse;
import com.careertuner.interview.dto.SubmitAnswerRequest;
import com.careertuner.interview.mapper.InterviewMapper;
import com.careertuner.interview.media.InterviewMediaMapper;
import com.careertuner.notification.domain.Notification;
import com.careertuner.notification.domain.NotificationDestinationPlatform;
import com.careertuner.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class InterviewServiceImpl implements InterviewService {

    private static final int DEFAULT_QUESTION_COUNT = 6;
    private static final int PRESSURE_QUESTION_COUNT = 3; // 압박: 본질문 3 + 답변마다 반박 1 = 총 6
    private static final int MAX_QUESTION_COUNT = 15;
    private static final String MODE_PRESSURE = "PRESSURE";
    private static final int DEFAULT_FOLLOWUP_COUNT = 2;
    private static final int MAX_FOLLOWUP_COUNT = 5;

    private static final String FEATURE_QUESTION = "INTERVIEW_QUESTION_GEN";
    private static final String FEATURE_FOLLOWUP = "INTERVIEW_FOLLOWUP_GEN";
    private static final String FEATURE_REPORT = "INTERVIEW_REPORT";
    private static final String FEATURE_MODEL_ANSWER = "INTERVIEW_MODEL_ANSWER";
    private static final String FEATURE_VOICE_SCORING = "INTERVIEW_VOICE_SCORING";

    private static final Map<String, String> MODE_LABELS = Map.of(
            "BASIC", "기본 면접",
            "JOB", "직무 면접",
            "PERSONALITY", "인성 면접",
            "PRESSURE", "압박 면접",
            "RESUME", "자소서 기반 면접",
            "PORTFOLIO", "포트폴리오 기반 면접",
            "REAL", "실전 종합 면접",
            "COMPANY", "기업 맞춤 면접");

    private final InterviewMapper interviewMapper;
    private final ApplicationCaseAccessService accessService;
    private final InterviewOpenAiClient aiClient;
    private final InterviewAiUsageLogService aiUsageLogService;
    private final InterviewAgentOrchestrator orchestrator;
    private final ObjectMapper objectMapper;
    private final InterviewBackgroundExecutor backgroundExecutor;
    private final NotificationService notificationService;
    private final FileService fileService;
    private final InterviewMediaMapper mediaMapper;

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
    public SessionPageResponse listSessions(Long userId, int page, int size) {
        int offset = page * size;
        List<InterviewSession> sessions = interviewMapper.findSessionsByUserId(userId, offset, size);
        int total = interviewMapper.countSessionsByUserId(userId);
        List<InterviewSessionResponse> responses = sessions.stream()
                .map(InterviewSessionResponse::from)
                .toList();
        return new SessionPageResponse(responses, total, page, size, offset + size < total);
    }

    @Override
    @Transactional
    public void deleteSession(Long userId, Long sessionId) {
        requireSession(userId, sessionId);
        for (InterviewAnswer answer : interviewMapper.findAnswersBySessionId(sessionId)) {
            for (FileAsset asset : fileService.findLinkedFiles("INTERVIEW_ANSWER", answer.getId())) {
                if (!userId.equals(asset.getOwnerUserId())
                        || !("AUDIO".equals(asset.getKind()) || "VIDEO".equals(asset.getKind()))) {
                    continue;
                }
                fileService.deleteOwnedLinked(
                        userId, asset.getId(), asset.getKind(), "INTERVIEW_ANSWER", answer.getId());
            }
        }
        interviewMapper.softDeleteTrainingSamplesBySessionId(sessionId);
        int updated = interviewMapper.softDeleteSession(sessionId, userId);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "삭제할 면접 기록을 찾을 수 없습니다.");
        }
    }

    @Override
    @Transactional
    public void markResumed(Long userId, Long sessionId) {
        interviewMapper.touchSessionResumed(sessionId, userId);
    }

    @Override
    @Transactional
    public void dispatchSession(Long userId, Long sessionId, InterviewDispatchTarget target) {
        InterviewSession session = requireSession(userId, sessionId);
        String modeLabel = MODE_LABELS.getOrDefault(session.getMode(), session.getMode());
        InterviewDispatchTarget resolvedTarget = target != null ? target : InterviewDispatchTarget.MOBILE;
        boolean toDesktop = InterviewDispatchTarget.DESKTOP == resolvedTarget;
        notificationService.notify(Notification.builder()
                .userId(userId)
                .type("INTERVIEW_DISPATCH")
                .targetType("INTERVIEW_SESSION")
                .targetId(sessionId)
                .destinationPlatform(toDesktop
                        ? NotificationDestinationPlatform.DESKTOP
                        : NotificationDestinationPlatform.MOBILE)
                .title(toDesktop
                        ? "모바일에서 면접 세션을 보냈어요"
                        : "데스크톱에서 면접 세션을 보냈어요")
                .message(modeLabel + (toDesktop
                        ? " 세션을 데스크톱에서 이어받을 수 있어요."
                        : " 세션을 폰에서 이어받을 수 있어요."))
                .link(toDesktop
                        ? "/interview/questions?session=" + sessionId
                        : "/m/session/" + sessionId)
                .build());
    }

    @Override
    @Transactional
    public List<InterviewQuestionResponse> generateQuestions(Long userId, Long sessionId,
                                                             GenerateQuestionsRequest request,
                                                             String operationKey) {
        InterviewSession session = lockOwnedSession(userId, sessionId);
        String normalizedOperationKey = normalizeAiOperationKey(operationKey);
        if (interviewMapper.insertAiOperationReservation(
                userId, FEATURE_QUESTION, sessionId, normalizedOperationKey) == 0) {
            return interviewMapper.findQuestionsBySessionId(sessionId).stream()
                    .map(InterviewQuestionResponse::from)
                    .toList();
        }
        if (interviewMapper.hasQuestionRegenerationBlockers(sessionId)) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "답변·원본·분석 결과가 있는 세션의 질문은 교체할 수 없습니다. 기존 기록을 보존하고 새 면접 세션을 만들어 주세요.");
        }
        ApplicationCase applicationCase = accessService.requireOwned(userId, session.getApplicationCaseId());
        String postingText = accessService.sourceText(session.getApplicationCaseId());
        PreparationContext preparation = capturePreparationContext(userId, session.getApplicationCaseId());
        // 압박 면접은 본질문 3개(이후 답변마다 반박 1개 자동 추가 → 총 6개), 그 외 모드는 기본 6개.
        int count = MODE_PRESSURE.equals(session.getMode()) ? PRESSURE_QUESTION_COUNT : resolveCount(request.count());
        String modeLabel = MODE_LABELS.getOrDefault(session.getMode(), session.getMode());

        InterviewOpenAiClient.GeneratedQuestions generated;
        try {
            generated = preparation.questionContext().isBlank()
                    ? aiClient.generateQuestions(applicationCase, postingText, modeLabel, count)
                    : aiClient.generateQuestions(
                            applicationCase, postingText, modeLabel, count, preparation.questionContext());
        } catch (BusinessException ex) {
            aiUsageLogService.recordFailure(userId, session.getApplicationCaseId(), FEATURE_QUESTION, ex.getMessage());
            throw ex;
        }
        aiUsageLogService.recordSuccess(userId, session.getApplicationCaseId(), FEATURE_QUESTION, generated.usage());

        interviewMapper.softDeleteQuestionsBySessionId(sessionId);
        if (preparation.sourceSnapshot() != null) {
            interviewMapper.updateSessionSourceSnapshot(sessionId, preparation.sourceSnapshot());
            session.setSourceSnapshot(preparation.sourceSnapshot());
        }
        interviewMapper.invalidateSessionResult(sessionId);
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
        Runnable modelAnswerJob = () ->
                backgroundExecutor.run(() -> storeModelAnswers(bgUserId, bgSession, bgCase, bgModeLabel, bgQuestions));
        // 트랜잭션 동기화가 활성일 때만 커밋 이후로 미룬다. autoprep 백그라운드 스레드처럼 활성 트랜잭션이
        // 없는 경로에서는 registerSynchronization 이 예외를 던지므로(질문은 이미 오토커밋됨) 즉시 실행한다.
        // (FileService·PendingFileCleanupWorker 와 동일한 isSynchronizationActive 가드 패턴.)
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    modelAnswerJob.run();
                }
            });
        } else {
            modelAnswerJob.run();
        }

        // 예상 질문 생성이 성공하면 사용자에게 완료 알림을 남긴다.
        notificationService.notify(Notification.builder()
                .userId(userId)
                .type("QUESTIONS_GENERATED")
                .targetType("INTERVIEW_SESSION")
                .targetId(sessionId)
                .title("면접 예상 질문이 준비되었습니다")
                .message("%s 예상 질문 %d개가 생성되었습니다.".formatted(modeLabel, inserted.size()))
                .link("/interview/questions?session=" + sessionId)
                .build());

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
    @Transactional(readOnly = true)
    public SessionReviewResponse getSessionReview(Long userId, Long sessionId) {
        InterviewSession session = requireSession(userId, sessionId);
        List<InterviewQuestion> questions = interviewMapper.findQuestionsBySessionId(sessionId);

        // 질문별 최신 답변(가장 큰 id)을 매핑한다. 재작성으로 답변이 여러 개일 수 있어 마지막 것만 본다.
        Map<Long, InterviewAnswer> latestByQuestion = latestAnswersByQuestion(sessionId);
        Map<Long, AnswerMediaScores> mediaScoresByAnswer = mediaScoresByAnswer(sessionId);

        List<SessionReviewResponse.Item> items = questions.stream()
                .map(q -> {
                    InterviewAnswer answer = latestByQuestion.get(q.getId());
                    AnswerMediaScores mediaScores = answer == null
                            ? AnswerMediaScores.EMPTY
                            : mediaScoresByAnswer.getOrDefault(answer.getId(), AnswerMediaScores.EMPTY);
                    return SessionReviewResponse.Item.of(
                            q, answer, mediaScores.voiceScore(), mediaScores.visualScore());
                })
                .toList();
        return SessionReviewResponse.of(session, items);
    }

    @Override
    @Transactional
    public int scoreVoiceTranscript(Long userId, Long sessionId, JsonNode transcript, Integer questionLimit) {
        InterviewSession session = lockOwnedSession(userId, sessionId);
        ApplicationCase applicationCase = accessService.requireOwned(userId, session.getApplicationCaseId());
        // 음성 면접은 준비된 본질문으로만 진행하므로 꼬리질문은 제외하고 채점 대상으로 삼는다.
        // 체험판(questionLimit=1)은 실제 진행한 질문만 넘긴다 — 전체를 넘기면 LLM 이 미진행 질문에도
        // 트랜스크립트 내용을 억지 매칭해 저장하는 문제가 있다.
        List<InterviewQuestion> questions = interviewMapper.findQuestionsBySessionId(sessionId).stream()
                .filter(q -> q.getParentQuestionId() == null)
                .limit(questionLimit == null ? Long.MAX_VALUE : questionLimit)
                .toList();
        if (questions.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "채점할 준비된 질문이 없습니다.");
        }
        String transcriptText = transcriptToText(transcript);
        if (transcriptText.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "채점할 대화 내용이 없습니다.");
        }
        List<String> questionTexts = questions.stream().map(InterviewQuestion::getQuestion).toList();
        // 텍스트 면접(evaluateAnswer)과 동일하게 저장된 모범답안을 만점 기준으로 넘긴다(§4.10 채점 레이어 통일).
        // 백그라운드 생성으로 아직 비어 있으면 그 질문만 일반 채점으로 폴백(빈 문자열).
        List<String> modelAnswers = questions.stream()
                .map(q -> q.getModelAnswer() == null ? "" : q.getModelAnswer())
                .toList();

        InterviewOpenAiClient.VoiceScoringResult scored;
        try {
            String fitContext = fitContextFromSnapshot(session.getSourceSnapshot());
            scored = fitContext.isBlank()
                    ? aiClient.scoreVoiceTranscript(questionTexts, modelAnswers, transcriptText,
                            applicationCase.getCompanyName(), applicationCase.getJobTitle())
                    : aiClient.scoreVoiceTranscript(questionTexts, modelAnswers, transcriptText,
                            applicationCase.getCompanyName(), applicationCase.getJobTitle(), fitContext);
        } catch (BusinessException ex) {
            aiUsageLogService.recordFailure(userId, session.getApplicationCaseId(), FEATURE_VOICE_SCORING, ex.getMessage());
            throw ex;
        }
        aiUsageLogService.recordSuccess(userId, session.getApplicationCaseId(), FEATURE_VOICE_SCORING, scored.usage());

        int count = 0;
        for (InterviewOpenAiClient.VoiceScoredItem item : scored.items()) {
            if (item.number() < 1 || item.number() > questions.size() || item.answerText().isBlank()) {
                continue;
            }
            InterviewQuestion q = questions.get(item.number() - 1);
            interviewMapper.insertAnswer(InterviewAnswer.builder()
                    .questionId(q.getId())
                    .answerText(item.answerText())
                    .score(item.score())
                    .feedback(item.feedback())
                    .build());
            count++;
        }
        if (count > 0) {
            interviewMapper.invalidateSessionResult(sessionId);
        }
        return count;
    }

    /** 트랜스크립트 JSON([{role,text}])을 "면접관/지원자: ..." 대화 텍스트로 변환한다. */
    private String transcriptToText(JsonNode transcript) {
        StringBuilder sb = new StringBuilder();
        if (transcript != null && transcript.isArray()) {
            for (JsonNode line : transcript) {
                String text = line.path("text").asText("").trim();
                if (text.isEmpty()) {
                    continue;
                }
                sb.append("ai".equals(line.path("role").asText("")) ? "면접관" : "지원자")
                        .append(": ").append(text).append("\n");
            }
        }
        return sb.toString();
    }

    @Override
    @Transactional
    public List<InterviewQuestionResponse> generateFollowUps(Long userId, Long questionId,
                                                             GenerateFollowUpsRequest request,
                                                             String operationKey) {
        InterviewQuestion question = lockOwnedQuestion(userId, questionId);
        if (question == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "면접 질문을 찾을 수 없습니다.");
        }
        String normalizedOperationKey = normalizeAiOperationKey(operationKey);
        if (interviewMapper.insertAiOperationReservation(
                userId, FEATURE_FOLLOWUP, questionId, normalizedOperationKey) == 0) {
            return interviewMapper.findQuestionsBySessionId(question.getInterviewSessionId()).stream()
                    .map(InterviewQuestionResponse::from)
                    .toList();
        }
        InterviewSession session = requireSession(userId, question.getInterviewSessionId());

        // 반박(꼬리) 질문은 압박 면접 전용. 다른 모드는 본질문 6개로 끝낸다(자체 LLM PROBE 태스크를 압박에 집중).
        if (!MODE_PRESSURE.equals(session.getMode())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "반박(꼬리) 질문은 압박 면접에서만 생성됩니다.");
        }

        ApplicationCase applicationCase = accessService.requireOwned(userId, session.getApplicationCaseId());
        InterviewAnswer answer = interviewMapper.findLatestAnswerByQuestionId(questionId);
        if (answer == null || answer.getAnswerText() == null || answer.getAnswerText().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "꼬리 질문은 답변을 먼저 제출한 뒤 생성할 수 있습니다.");
        }
        // 압박 면접: 답변 직후 반박 1개. 그 외(수동): 기존 기본 개수.
        boolean pressure = MODE_PRESSURE.equals(session.getMode());
        int count = pressure ? 1 : resolveFollowUpCount(request == null ? null : request.count());

        InterviewOpenAiClient.GeneratedQuestions generated;
        try {
            String fitContext = fitContextFromSnapshot(session.getSourceSnapshot());
            generated = fitContext.isBlank()
                    ? aiClient.generateFollowUps(question.getQuestion(), answer.getAnswerText(),
                            applicationCase, count, pressure)
                    : aiClient.generateFollowUps(question.getQuestion(), answer.getAnswerText(),
                            applicationCase, count, pressure, fitContext);
        } catch (BusinessException ex) {
            aiUsageLogService.recordFailure(userId, session.getApplicationCaseId(), FEATURE_FOLLOWUP, ex.getMessage());
            throw ex;
        }
        aiUsageLogService.recordSuccess(userId, session.getApplicationCaseId(), FEATURE_FOLLOWUP, generated.usage());

        Integer maxOrder = interviewMapper.findMaxSortOrder(question.getInterviewSessionId());
        int order = maxOrder == null ? 0 : maxOrder;
        boolean inserted = false;
        for (InterviewOpenAiClient.GeneratedQuestion q : generated.questions()) {
            interviewMapper.insertQuestion(InterviewQuestion.builder()
                    .interviewSessionId(question.getInterviewSessionId())
                    .parentQuestionId(question.getId())
                    .question(q.question())
                    .questionType("FOLLOW_UP")
                    .sortOrder(++order)
                    .build());
            inserted = true;
        }
        if (inserted) {
            interviewMapper.invalidateSessionResult(question.getInterviewSessionId());
        }
        return listQuestions(userId, question.getInterviewSessionId());
    }

    @Override
    @Transactional
    public InterviewAnswerResponse submitAnswer(Long userId, Long questionId, SubmitAnswerRequest request) {
        InterviewQuestion question = lockOwnedQuestion(userId, questionId);
        if (question == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "면접 질문을 찾을 수 없습니다.");
        }
        InterviewSession session = requireSession(userId, question.getInterviewSessionId());
        ApplicationCase applicationCase = accessService.requireOwned(userId, session.getApplicationCaseId());

        String clientSubmissionId = normalizeClientSubmissionId(request.clientSubmissionId());
        InterviewAnswer answer = null;
        if (clientSubmissionId != null) {
            // 평가/모범답안 생성보다 먼저 유니크 예약을 선점한다. 동일 키의 경쟁 요청은
            // INSERT IGNORE에서 선행 트랜잭션 종료를 기다린 뒤 완료 결과를 재사용한다.
            answer = InterviewAnswer.builder()
                    .questionId(questionId)
                    .clientSubmissionId(clientSubmissionId)
                    .submissionStatus("PENDING")
                    .build();
            int acquired;
            try {
                acquired = interviewMapper.insertAnswerReservation(answer);
            } catch (CannotAcquireLockException | QueryTimeoutException e) {
                throw new BusinessException(
                        ErrorCode.CONFLICT,
                        "같은 면접 답변 요청이 처리 중입니다. 잠시 후 다시 확인해 주세요.");
            }
            if (acquired == 0) {
                InterviewAnswer existing = interviewMapper
                        .findAnswerByQuestionIdAndClientSubmissionId(questionId, clientSubmissionId);
                if (existing != null && "COMPLETED".equals(existing.getSubmissionStatus())) {
                    return InterviewAnswerResponse.from(existing);
                }
                // 정상 경로에서는 PENDING이 커밋되지 않는다. 서버/DB 운영 중 수동으로 남은
                // 비정상 예약을 보았다면 AI를 중복 실행하지 않고 클라이언트가 재조회하게 한다.
                throw new BusinessException(
                        ErrorCode.CONFLICT,
                        "같은 면접 답변 요청이 처리 중입니다. 잠시 후 다시 확인해 주세요.");
            }
        }

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
        String fitContext = fitContextFromSnapshot(session.getSourceSnapshot());
        InterviewAgentOrchestrator.OrchestratedEvaluation evaluation = fitContext.isBlank()
                ? orchestrator.evaluateAnswer(userId, session, applicationCase, question, request.answerText(),
                        referenceModelAnswer)
                : orchestrator.evaluateAnswer(userId, session, applicationCase, question, request.answerText(),
                        referenceModelAnswer, fitContext);

        boolean reserved = answer != null;
        if (!reserved) {
            answer = InterviewAnswer.builder()
                    .questionId(questionId)
                    .submissionStatus("COMPLETED")
                    .build();
        }
        answer.setAnswerText(request.answerText());
        answer.setAudioUrl(blankToNull(request.audioUrl()));
        answer.setVideoUrl(blankToNull(request.videoUrl()));
        answer.setScore(evaluation.score());
        answer.setFeedback(evaluation.feedback());
        answer.setImprovedAnswer(evaluation.improvedAnswer());
        if (!reserved) {
            interviewMapper.insertAnswer(answer);
        }

        // 원본 파일은 답변 id가 발급된 뒤 같은 트랜잭션에서만 연결한다.
        // 소유자·kind·업로드 용도·ref_id IS NULL을 조건부 UPDATE로 검증하며,
        // 클라이언트가 보낸 URL 대신 서버 소유 content URL로 정규화한다.
        String audioUrl = request.audioFileId() == null
                ? blankToNull(request.audioUrl())
                : claimAnswerMedia(userId, request.audioFileId(), "AUDIO", answer.getId());
        String videoUrl = request.videoFileId() == null
                ? blankToNull(request.videoUrl())
                : claimAnswerMedia(userId, request.videoFileId(), "VIDEO", answer.getId());
        boolean mediaUrlsChanged = !java.util.Objects.equals(audioUrl, answer.getAudioUrl())
                || !java.util.Objects.equals(videoUrl, answer.getVideoUrl());
        answer.setAudioUrl(audioUrl);
        answer.setVideoUrl(videoUrl);
        if (reserved) {
            if (interviewMapper.completeAnswerReservation(answer) != 1) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "면접 답변 저장을 완료하지 못했습니다.");
            }
            answer.setSubmissionStatus("COMPLETED");
        } else if (mediaUrlsChanged) {
            interviewMapper.updateAnswerMediaUrls(answer.getId(), audioUrl, videoUrl);
        }
        upsertAnswerMediaScores(question, answer, request);
        interviewMapper.invalidateSessionResult(question.getInterviewSessionId());
        return InterviewAnswerResponse.from(answer);
    }

    /** 모바일 캡처 파생 점수는 답변/원본 저장과 같은 트랜잭션에서만 확정한다. */
    private void upsertAnswerMediaScores(InterviewQuestion question, InterviewAnswer answer,
                                         SubmitAnswerRequest request) {
        if (request.voiceScore() == null && request.visualScore() == null) {
            return;
        }
        String kind = request.visualScore() != null || answer.getVideoUrl() != null ? "AVATAR" : "VOICE";
        int score;
        if (request.voiceScore() != null && request.visualScore() != null) {
            score = Math.round((request.voiceScore() + request.visualScore()) / 2.0f);
        } else {
            score = request.voiceScore() != null ? request.voiceScore() : request.visualScore();
        }
        StringBuilder detail = new StringBuilder("{");
        if (request.voiceScore() != null) {
            detail.append("\"voiceScore\":").append(request.voiceScore());
        }
        if (request.visualScore() != null) {
            if (detail.length() > 1) {
                detail.append(',');
            }
            detail.append("\"visualScore\":").append(request.visualScore());
        }
        detail.append('}');
        mediaMapper.insertMediaAnalysis(InterviewMediaAnalysis.builder()
                .interviewSessionId(question.getInterviewSessionId())
                .questionId(question.getId())
                .answerId(answer.getId())
                .kind(kind)
                .score(score)
                .scoreDetail(detail.toString())
                .build());
    }

    @Override
    @Transactional
    public void deleteAnswerMedia(Long userId, Long answerId, String kind) {
        InterviewAnswer answer = interviewMapper.findAnswerByIdAndUserId(answerId, userId);
        if (answer == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "면접 답변을 찾을 수 없습니다.");
        }
        String normalizedKind = kind == null ? "" : kind.trim().toUpperCase(java.util.Locale.ROOT);
        if (!("AUDIO".equals(normalizedKind) || "VIDEO".equals(normalizedKind))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "삭제할 원본 종류는 AUDIO 또는 VIDEO여야 합니다.");
        }

        for (FileAsset asset : fileService.findLinkedFiles("INTERVIEW_ANSWER", answerId)) {
            if (normalizedKind.equals(asset.getKind())) {
                fileService.deleteOwnedLinked(
                        userId, asset.getId(), normalizedKind, "INTERVIEW_ANSWER", answerId);
            }
        }
        if ("AUDIO".equals(normalizedKind)) {
            interviewMapper.clearAnswerAudioUrl(answerId);
        } else {
            interviewMapper.clearAnswerVideoUrl(answerId);
        }
    }

    private String claimAnswerMedia(Long userId, Long fileId, String kind, Long answerId) {
        FileAsset linked = fileService.claimOwnedPendingFile(
                userId, fileId, kind, "INTERVIEW_ANSWER", answerId);
        return "/api/file/" + linked.getId() + "/content";
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

    /** 세션의 질문별 '최신' 답변(같은 질문 재작성이 여러 개면 가장 큰 id)을 맵으로 만든다. */
    private Map<Long, InterviewAnswer> latestAnswersByQuestion(Long sessionId) {
        Map<Long, InterviewAnswer> latestByQuestion = new HashMap<>();
        for (InterviewAnswer answer : interviewMapper.findAnswersBySessionId(sessionId)) {
            InterviewAnswer current = latestByQuestion.get(answer.getQuestionId());
            if (current == null || (answer.getId() != null && current.getId() != null
                    && answer.getId() > current.getId())) {
                latestByQuestion.put(answer.getQuestionId(), answer);
            }
        }
        return latestByQuestion;
    }

    /** 최신 답변 단위 분석에서 모바일 전달력/비언어 점수를 복원한다. 세션 단위 기존 행은 건너뛴다. */
    private Map<Long, AnswerMediaScores> mediaScoresByAnswer(Long sessionId) {
        Map<Long, AnswerMediaScores> scores = new HashMap<>();
        for (InterviewMediaAnalysis analysis : mediaMapper.findBySessionId(sessionId)) {
            if (analysis.getAnswerId() == null) {
                continue;
            }
            AnswerMediaScores current = scores.getOrDefault(analysis.getAnswerId(), AnswerMediaScores.EMPTY);
            JsonNode detail = parseMediaScoreDetail(analysis.getScoreDetail());
            Integer voiceScore = readScore(detail, "voiceScore");
            Integer visualScore = readScore(detail, "visualScore");
            if (voiceScore == null && "VOICE".equals(analysis.getKind())) {
                voiceScore = analysis.getScore();
            }
            scores.put(analysis.getAnswerId(), new AnswerMediaScores(
                    current.voiceScore() != null ? current.voiceScore() : voiceScore,
                    current.visualScore() != null ? current.visualScore() : visualScore));
        }
        return scores;
    }

    private JsonNode parseMediaScoreDetail(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private Integer readScore(JsonNode detail, String field) {
        if (detail == null) {
            return null;
        }
        JsonNode value = detail.get(field);
        return value != null && value.isNumber() ? value.asInt() : null;
    }

    /**
     * 리포트에 실을 질문별 채점 목록을 만든다. 본질문(꼬리질문 제외) 순서대로 최신 답변의 점수/피드백을 붙인다.
     * 음성/영상 면접도 텍스트와 동일하게 질문 단위 채점을 리포트 화면에서 볼 수 있게 하기 위함.
     */
    private List<InterviewReportResponse.QuestionScore> buildQuestionScores(Long sessionId) {
        Map<Long, InterviewAnswer> latestByQuestion = latestAnswersByQuestion(sessionId);
        Map<Long, AnswerMediaScores> mediaScoresByAnswer = mediaScoresByAnswer(sessionId);
        List<InterviewReportResponse.QuestionScore> scores = new ArrayList<>();
        int order = 1;
        for (InterviewQuestion q : interviewMapper.findQuestionsBySessionId(sessionId)) {
            if (q.getParentQuestionId() != null) {
                continue; // 꼬리질문은 본질문 채점 목록에서 제외
            }
            InterviewAnswer a = latestByQuestion.get(q.getId());
            AnswerMediaScores mediaScores = a == null
                    ? AnswerMediaScores.EMPTY
                    : mediaScoresByAnswer.getOrDefault(a.getId(), AnswerMediaScores.EMPTY);
            scores.add(new InterviewReportResponse.QuestionScore(
                    q.getId(), order++, q.getQuestion(),
                    a == null ? null : a.getScore(),
                    a == null ? null : a.getFeedback(),
                    mediaScores.voiceScore(),
                    mediaScores.visualScore()));
        }
        return scores;
    }

    @Override
    @Transactional
    public InterviewReportResponse getReport(Long userId, Long sessionId) {
        InterviewSession session = lockOwnedSession(userId, sessionId);

        // 이미 생성된 리포트가 있으면 그대로 반환한다. 질문별 채점은 캐시 스냅샷에 없을 수 있으므로
        // 항상 현재 답변 기준으로 새로 계산해 덧입힌다(텍스트/음성/영상 모두 같은 화면에서 질문 단위 점수 노출).
        if (session.getReport() != null && !session.getReport().isBlank()) {
            InterviewReportResponse cached = readReport(session.getReport());
            if (cached != null) {
                return cached.withQuestionScores(buildQuestionScores(sessionId));
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
            String fitContext = fitContextFromSnapshot(session.getSourceSnapshot());
            payload = fitContext.isBlank()
                    ? aiClient.generateReport(transcript)
                    : aiClient.generateReport(transcript, fitContext);
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
                payload.summaryFeedback(),
                buildQuestionScores(sessionId));

        int updated = interviewMapper.updateSessionResult(
                sessionId, payload.totalScore(), writeReport(response), LocalDateTime.now());
        if (updated == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "면접 세션을 찾을 수 없습니다.");
        }

        // 리포트가 새로 생성된 경우에만 완료 알림을 남긴다(캐시 반환 시에는 발행하지 않는다).
        String modeLabel = MODE_LABELS.getOrDefault(session.getMode(), session.getMode());
        notificationService.notify(Notification.builder()
                .userId(userId)
                .type("INTERVIEW_REPORT_READY")
                .targetType("INTERVIEW_SESSION")
                .targetId(sessionId)
                .title("면접 리포트가 준비되었습니다")
                .message("%s 리포트 · 종합 %d점".formatted(modeLabel, payload.totalScore()))
                .link("/interview/reports?session=" + sessionId)
                .build());

        return response;
    }

    @Override
    @Transactional
    public ModelAnswerResponse getModelAnswer(Long userId, Long questionId) {
        // 동일 문항의 모바일·웹·데스크톱 중복 클릭을 직렬화한다. 잠금 대기 뒤
        // 저장값을 다시 확인하므로 실제 모델 호출과 과금은 최초 요청 한 번만 수행된다.
        // 질문 행만 잠근다(세션 락 제외) — 같은 세션/케이스의 다른 질문 요청과 경합하지 않도록.
        InterviewQuestion question = lockOwnedQuestionRowOnly(userId, questionId);
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

        // 채점 기준으로 재사용하도록 저장한다(first-writer-wins). model_answer 컬럼 적용 전이면 조용히 건너뛴다.
        try {
            interviewMapper.updateQuestionModelAnswer(questionId, generated.modelAnswer());
        } catch (RuntimeException ignored) {
            // 컬럼 미적용 등 저장 실패가 모범답안 표시를 막지 않는다.
        }
        // 백그라운드 생성과 경쟁할 수 있으므로, 방금 만든 값이 아니라 확정 저장된 값을 돌려준다(표시 = 채점 기준 일치).
        return new ModelAnswerResponse(resolveStoredModelAnswer(questionId, userId, generated.modelAnswer()));
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
            // 백그라운드 일괄 생성과 경쟁할 수 있으므로, 확정 저장된 모범답안을 채점 기준으로 쓴다(표시값과 동일 보장).
            return resolveStoredModelAnswer(question.getId(), userId, generated.modelAnswer());
        } catch (BusinessException ex) {
            aiUsageLogService.recordFailure(userId, session.getApplicationCaseId(), FEATURE_MODEL_ANSWER, ex.getMessage());
            return null;
        }
    }

    /**
     * 저장된(확정) 모범답안을 다시 읽어 반환한다.
     * updateQuestionModelAnswer 가 first-writer-wins 라, 백그라운드 일괄 생성과 경쟁해도
     * 여기서 읽은 값(=실제 저장값)이 표시·복기와 동일하다. 저장값이 없으면 방금 생성한 값으로 폴백한다.
     */
    private String resolveStoredModelAnswer(Long questionId, Long userId, String fallback) {
        InterviewQuestion fresh = interviewMapper.findQuestionByIdAndUserId(questionId, userId);
        String stored = fresh == null ? null : fresh.getModelAnswer();
        return (stored != null && !stored.isBlank()) ? stored : fallback;
    }

    private InterviewSession requireSession(Long userId, Long sessionId) {
        InterviewSession session = interviewMapper.findSessionByIdAndUserId(sessionId, userId);
        if (session == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "면접 세션을 찾을 수 없습니다.");
        }
        return session;
    }

    private InterviewSession lockOwnedSession(Long userId, Long sessionId) {
        InterviewSession session = interviewMapper.lockSessionByIdAndUserId(sessionId, userId);
        if (session == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "면접 세션을 찾을 수 없습니다.");
        }
        return session;
    }

    private InterviewQuestion lockOwnedQuestion(Long userId, Long questionId) {
        InterviewQuestion snapshot = interviewMapper.findQuestionByIdAndUserId(questionId, userId);
        if (snapshot == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "면접 질문을 찾을 수 없습니다.");
        }
        // 모든 입력 변경이 session -> question 순서로 잠그게 해 재생성(delete)과 답변 저장의 역순 deadlock을 피한다.
        lockOwnedSession(userId, snapshot.getInterviewSessionId());
        InterviewQuestion locked = interviewMapper.lockQuestionByIdAndUserId(questionId, userId);
        if (locked == null || !snapshot.getInterviewSessionId().equals(locked.getInterviewSessionId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "면접 질문을 찾을 수 없습니다.");
        }
        return locked;
    }

    /**
     * 모범답안 조회 전용: 질문 행만 잠근다(세션 락을 잡지 않는다).
     * 모범답안 생성은 세션 구조를 바꾸지 않고 질문의 model_answer 만 갱신하며, 저장은 first-writer-wins
     * (updateQuestionModelAnswer 가 model_answer IS NULL 일 때만 기록)라 세션 락이 없어도 표시·채점 기준이 일치한다.
     * lockOwnedQuestion 처럼 세션 행까지 잠그면 같은 세션·케이스의 다른 질문을 동시에 요청할 때
     * 뒤 요청이 공통 세션/케이스 행 락을 AI 생성(수십초) 내내 기다리다 statement-timeout(30s)으로 500 난다.
     * 질문 행만 잠그므로 동일 문항 중복 클릭만 직렬화되고(AI 1회), 세션 락을 아예 잡지 않아 락 순서 정책과도 무관하다.
     */
    private InterviewQuestion lockOwnedQuestionRowOnly(Long userId, Long questionId) {
        InterviewQuestion locked = interviewMapper.lockQuestionByIdAndUserId(questionId, userId);
        if (locked == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "면접 질문을 찾을 수 없습니다.");
        }
        return locked;
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

    private String normalizeAiOperationKey(String value) {
        String key = value == null ? "" : value.trim();
        if (key.isBlank() || key.length() > 120 || !key.matches("[A-Za-z0-9:_-]+")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "AI 작업 멱등키가 올바르지 않습니다.");
        }
        return key;
    }

    private String normalizeClientSubmissionId(String value) {
        if (value == null) {
            return null;
        }
        try {
            String normalized = UUID.fromString(value).toString();
            if (!normalized.equalsIgnoreCase(value)) {
                throw new IllegalArgumentException("non-canonical UUID");
            }
            return normalized;
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "clientSubmissionId는 UUID 형식이어야 합니다.");
        }
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

    /**
     * 질문 생성 순간 A/B/C 정본을 읽어 프롬프트 컨텍스트와 provenance 스냅샷을 함께 만든다.
     * C 분석이 아직 없더라도 A/B 입력만으로 질문 생성은 계속되며, 평가·리포트는 C 컨텍스트 없이 폴백한다.
     */
    private PreparationContext capturePreparationContext(Long userId, Long applicationCaseId) {
        InterviewPreparationContextSource source = interviewMapper.findPreparationContext(userId, applicationCaseId);
        if (source == null) {
            return PreparationContext.EMPTY;
        }

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("schemaVersion", 1);
        snapshot.put("capturedAt", LocalDateTime.now());
        snapshot.put("profileVersionId", source.getProfileVersionId());
        snapshot.put("profileVersionNo", source.getProfileVersionNo());
        snapshot.put("jobAnalysisId", source.getJobAnalysisId());
        snapshot.put("companyAnalysisId", source.getCompanyAnalysisId());
        snapshot.put("fitAnalysisId", source.getFitAnalysisId());
        snapshot.put("fitScore", source.getFitScore());
        snapshot.put("matchedSkills", jsonValue(source.getMatchedSkills()));
        snapshot.put("missingSkills", jsonValue(source.getMissingSkills()));
        snapshot.put("gapRecommendations", jsonValue(source.getGapRecommendations()));
        snapshot.put("strategyActions", jsonValue(source.getStrategyActions()));
        snapshot.put("fitModel", source.getFitModel());
        snapshot.put("fitPromptVersion", source.getFitPromptVersion());
        snapshot.put("fitCreatedAt", source.getFitCreatedAt());

        String snapshotJson;
        try {
            snapshotJson = objectMapper.writeValueAsString(snapshot);
        } catch (JacksonException ex) {
            snapshotJson = null;
        }

        String questionContext = """
                [면접 준비 정본 컨텍스트]
                희망 직무: %s
                학력: %s
                경력: %s
                프로젝트: %s
                보유 역량: %s
                자격증: %s
                포트폴리오: %s
                이력서/자기소개 근거: %s
                공고 필수 역량: %s
                공고 우대 역량: %s
                담당 업무: %s
                지원 조건: %s
                공고 분석 요약: %s
                기업 요약/최근 이슈/면접 포인트: %s / %s / %s
                최신 적합도 분석(ID %s): %s점
                확인된 강점: %s
                부족 역량: %s
                보완 전략: %s
                다음 실행: %s

                위 내용은 질문 맞춤화 근거이며, 적합도 점수는 합격 확률로 해석하지 않는다.
                """.formatted(
                promptValue(source.getDesiredJob(), 300),
                promptValue(source.getEducation(), 1200),
                promptValue(source.getCareer(), 2200),
                promptValue(source.getProjects(), 2200),
                promptValue(source.getSkills(), 1000),
                promptValue(source.getCertificates(), 1000),
                promptValue(source.getPortfolioLinks(), 1000),
                promptValue(joinNonBlank(source.getResumeText(), source.getSelfIntro()), 3500),
                promptValue(source.getRequiredSkills(), 1200),
                promptValue(source.getPreferredSkills(), 1200),
                promptValue(source.getDuties(), 1800),
                promptValue(source.getQualifications(), 1800),
                promptValue(source.getJobSummary(), 1600),
                promptValue(source.getCompanySummary(), 1400),
                promptValue(source.getRecentIssues(), 1200),
                promptValue(source.getInterviewPoints(), 1400),
                source.getFitAnalysisId() == null ? "없음" : source.getFitAnalysisId(),
                source.getFitScore() == null ? "미분석" : source.getFitScore(),
                promptValue(source.getMatchedSkills(), 1000),
                promptValue(source.getMissingSkills(), 1000),
                promptValue(source.getGapRecommendations(), 1600),
                promptValue(source.getStrategyActions(), 1600));
        return new PreparationContext(snapshotJson, questionContext);
    }

    /** 세션 생성 당시 고정한 C 적합도 핵심 결과만 평가·음성 채점·리포트에 재사용한다. */
    private String fitContextFromSnapshot(String snapshot) {
        if (snapshot == null || snapshot.isBlank()) {
            return "";
        }
        try {
            JsonNode root = objectMapper.readTree(snapshot);
            if (root == null || root.path("fitAnalysisId").isMissingNode() || root.path("fitAnalysisId").isNull()) {
                return "";
            }
            return """
                    [질문 생성 시점 적합도 분석 스냅샷]
                    분석 ID: %s, 점수: %s (합격 확률이 아닌 준비도 참고값)
                    확인된 강점: %s
                    부족 역량: %s
                    보완 전략: %s
                    다음 실행: %s
                    답변의 직무 연결성과 구체성을 판단할 때만 참고하고, 없는 경험을 있다고 추정하지 않는다.
                    """.formatted(
                    root.path("fitAnalysisId").asText(""), root.path("fitScore").asText("미분석"),
                    root.path("matchedSkills"), root.path("missingSkills"),
                    root.path("gapRecommendations"), root.path("strategyActions"));
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private Object jsonValue(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readTree(value);
        } catch (RuntimeException ex) {
            return value;
        }
    }

    private String promptValue(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "(없음)";
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength) + "…";
    }

    private String joinNonBlank(String first, String second) {
        String left = first == null ? "" : first.trim();
        String right = second == null ? "" : second.trim();
        if (left.isEmpty()) {
            return right;
        }
        if (right.isEmpty()) {
            return left;
        }
        return left + "\n" + right;
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

    private record AnswerMediaScores(Integer voiceScore, Integer visualScore) {
        private static final AnswerMediaScores EMPTY = new AnswerMediaScores(null, null);
    }

    private record PreparationContext(String sourceSnapshot, String questionContext) {
        private static final PreparationContext EMPTY = new PreparationContext(null, "");
    }
}
