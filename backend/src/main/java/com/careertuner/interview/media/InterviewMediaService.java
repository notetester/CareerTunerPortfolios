package com.careertuner.interview.media;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.interview.domain.InterviewMediaAnalysis;
import com.careertuner.interview.domain.InterviewSession;
import com.careertuner.interview.mapper.InterviewMapper;
import com.careertuner.interview.service.InterviewAiUsageLogService;
import com.careertuner.interview.service.InterviewOpenAiClient;
import com.careertuner.interview.media.dto.AvatarScoreRequest;
import com.careertuner.interview.media.dto.AvatarScoreResponse;
import com.careertuner.interview.media.dto.MediaAnalysisResponse;
import com.careertuner.interview.media.dto.SaveMediaAnalysisRequest;
import com.careertuner.interview.media.dto.TranscribeRequest;
import com.careertuner.interview.media.dto.TranscribeResponse;
import com.careertuner.interview.media.dto.VoiceScoreRequest;
import com.careertuner.interview.media.dto.VoiceScoreResponse;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 음성/영상 면접 분석 결과 저장·조회.
 * 분석 요청으로 받은 base64 사본은 보관하지 않고 트랜스크립트·지표·점수만 저장한다.
 * 사용자가 보관에 동의한 답변 원본은 file_asset + 표준 interview_answer URL 계약에서 별도 관리한다.
 */
@Service
public class InterviewMediaService {

    private static final Set<String> KINDS = Set.of("VOICE", "AVATAR");

    private final InterviewMediaMapper mediaMapper;
    private final InterviewMapper interviewMapper;
    private final InterviewNonverbalClient nonverbalClient;
    private final ObjectMapper objectMapper;
    private final InterviewAiUsageLogService aiUsageLogService;

    public InterviewMediaService(InterviewMediaMapper mediaMapper,
                                 InterviewMapper interviewMapper,
                                 InterviewNonverbalClient nonverbalClient,
                                 ObjectMapper objectMapper,
                                 InterviewAiUsageLogService aiUsageLogService) {
        this.mediaMapper = mediaMapper;
        this.interviewMapper = interviewMapper;
        this.nonverbalClient = nonverbalClient;
        this.objectMapper = objectMapper;
        this.aiUsageLogService = aiUsageLogService;
    }

    /** 자체 추론 서버(serve)로 음성 점수 산출. 분석 요청 사본은 점수 산출 후 보관하지 않는다. */
    public VoiceScoreResponse scoreVoice(Long userId, Long sessionId, VoiceScoreRequest request) {
        InterviewSession session = requireOwnedSession(userId, sessionId);
        VoiceScoreResponse response = nonverbalClient.scoreVoice(request.audioBase64(), request.audioFormat(),
                request.transcriptChars(), request.fillerCount(), request.latencySec());
        aiUsageLogService.recordSuccess(userId, session.getApplicationCaseId(), "INTERVIEW_VOICE_SCORING",
                new InterviewOpenAiClient.Usage("careertuner-nonverbal", 0, 0, 0));
        return response;
    }

    /** 자체 추론 서버(serve)로 아바타 음성+영상 점수 산출. 분석 요청 사본은 산출 후 보관하지 않는다. */
    public AvatarScoreResponse scoreAvatar(Long userId, Long sessionId, AvatarScoreRequest request) {
        InterviewSession session = requireOwnedSession(userId, sessionId);
        AvatarScoreResponse response = nonverbalClient.scoreAvatar(request.videoBase64(), request.videoFormat(),
                request.transcriptChars(), request.fillerCount(), request.latencySec());
        aiUsageLogService.recordSuccess(userId, session.getApplicationCaseId(), "INTERVIEW_VIDEO_ANALYSIS",
                new InterviewOpenAiClient.Usage("careertuner-nonverbal", 0, 0, 0));
        return response;
    }

    /** 자체 STT(serve)로 음성 답변 전사. 분석 요청 사본은 전사 후 보관하지 않는다. */
    public TranscribeResponse transcribe(Long userId, Long sessionId, TranscribeRequest request) {
        requireOwnedSession(userId, sessionId);
        return nonverbalClient.transcribe(request.audioBase64(), request.audioFormat(), request.language());
    }

    /** capabilities 노출용 — 자체 추론 서버 사용 가능 여부. */
    public boolean nonverbalEnabled() {
        return nonverbalClient.enabled();
    }

    @Transactional
    public MediaAnalysisResponse save(Long userId, Long sessionId, SaveMediaAnalysisRequest request) {
        lockOwnedSession(userId, sessionId);
        String kind = request.kind().toUpperCase();
        if (!KINDS.contains(kind)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "kind 는 VOICE 또는 AVATAR 여야 합니다.");
        }
        if (request.score() < 0 || request.score() > 100) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "score 는 0~100 이어야 합니다.");
        }
        validateAnswerLink(userId, sessionId, request.questionId(), request.answerId());
        InterviewMediaAnalysis analysis = InterviewMediaAnalysis.builder()
                .interviewSessionId(sessionId)
                .questionId(request.questionId())
                .answerId(request.answerId())
                .kind(kind)
                .transcript(toJsonString(request.transcript()))
                .metrics(toJsonString(request.metrics()))
                .score(request.score())
                .scoreDetail(toJsonString(request.scoreDetail()))
                .build();
        mediaMapper.insertMediaAnalysis(analysis);
        return toResponse(analysis);
    }

    public List<MediaAnalysisResponse> list(Long userId, Long sessionId) {
        requireOwnedSession(userId, sessionId);
        return mediaMapper.findBySessionId(sessionId).stream().map(this::toResponse).toList();
    }

    private void validateAnswerLink(Long userId, Long sessionId, Long questionId, Long answerId) {
        if (questionId == null && answerId == null) {
            return; // 기존 음성/아바타 세션 단위 분석 계약
        }
        if (questionId == null || answerId == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT,
                    "답변 단위 분석은 questionId와 answerId를 함께 보내야 합니다.");
        }
        var answer = interviewMapper.findAnswerByIdAndUserId(answerId, userId);
        var question = interviewMapper.findQuestionByIdAndUserId(questionId, userId);
        if (answer == null || question == null
                || !questionId.equals(answer.getQuestionId())
                || !sessionId.equals(question.getInterviewSessionId())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "현재 세션의 면접 답변을 확인할 수 없습니다.");
        }
    }

    /** 관리자/내부용 — 세션 소유권 체크 없이 미디어 분석 결과 조회 (admin 모니터링). */
    public List<MediaAnalysisResponse> listBySessionId(Long sessionId) {
        return mediaMapper.findBySessionId(sessionId).stream().map(this::toResponse).toList();
    }

    private InterviewSession requireOwnedSession(Long userId, Long sessionId) {
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

    private String toJsonString(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        return objectMapper.writeValueAsString(node);
    }

    private JsonNode parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private MediaAnalysisResponse toResponse(InterviewMediaAnalysis analysis) {
        return new MediaAnalysisResponse(
                analysis.getId(),
                analysis.getInterviewSessionId(),
                analysis.getQuestionId(),
                analysis.getAnswerId(),
                analysis.getKind(),
                parse(analysis.getTranscript()),
                parse(analysis.getMetrics()),
                analysis.getScore(),
                parse(analysis.getScoreDetail()),
                analysis.getCreatedAt());
    }
}
