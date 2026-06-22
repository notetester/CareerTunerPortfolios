package com.careertuner.interview.media;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.interview.domain.InterviewMediaAnalysis;
import com.careertuner.interview.domain.InterviewSession;
import com.careertuner.interview.mapper.InterviewMapper;
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
 * 원본 음성·영상은 받지 않는다 — 온디바이스 분석 결과(트랜스크립트 + 지표 + 점수 JSON)만 저장 (ADR-002).
 */
@Service
public class InterviewMediaService {

    private static final Set<String> KINDS = Set.of("VOICE", "AVATAR");

    private final InterviewMediaMapper mediaMapper;
    private final InterviewMapper interviewMapper;
    private final InterviewNonverbalClient nonverbalClient;
    private final ObjectMapper objectMapper;

    public InterviewMediaService(InterviewMediaMapper mediaMapper,
                                 InterviewMapper interviewMapper,
                                 InterviewNonverbalClient nonverbalClient,
                                 ObjectMapper objectMapper) {
        this.mediaMapper = mediaMapper;
        this.interviewMapper = interviewMapper;
        this.nonverbalClient = nonverbalClient;
        this.objectMapper = objectMapper;
    }

    /** 자체 추론 서버(serve)로 음성 점수 산출 (ADR-006). 원본 음성은 점수 산출 후 버려진다. */
    public VoiceScoreResponse scoreVoice(Long userId, Long sessionId, VoiceScoreRequest request) {
        requireOwnedSession(userId, sessionId);
        return nonverbalClient.scoreVoice(request.audioBase64(), request.audioFormat(),
                request.transcriptChars(), request.fillerCount(), request.latencySec());
    }

    /** 자체 추론 서버(serve)로 아바타 음성+영상 점수 산출 (late fusion, ADR-006/007). 원본 영상은 점수 산출 후 버려진다. */
    public AvatarScoreResponse scoreAvatar(Long userId, Long sessionId, AvatarScoreRequest request) {
        requireOwnedSession(userId, sessionId);
        return nonverbalClient.scoreAvatar(request.videoBase64(), request.videoFormat(),
                request.transcriptChars(), request.fillerCount(), request.latencySec());
    }

    /** 자체 STT(serve)로 음성 답변 전사 — B 베이직 면접 (OpenAI Whisper API 대체). 원본 음성은 전사 후 버려진다. */
    public TranscribeResponse transcribe(Long userId, Long sessionId, TranscribeRequest request) {
        requireOwnedSession(userId, sessionId);
        return nonverbalClient.transcribe(request.audioBase64(), request.audioFormat(), request.language());
    }

    /** capabilities 노출용 — 자체 추론 서버 사용 가능 여부. */
    public boolean nonverbalEnabled() {
        return nonverbalClient.enabled();
    }

    public MediaAnalysisResponse save(Long userId, Long sessionId, SaveMediaAnalysisRequest request) {
        requireOwnedSession(userId, sessionId);
        String kind = request.kind().toUpperCase();
        if (!KINDS.contains(kind)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "kind 는 VOICE 또는 AVATAR 여야 합니다.");
        }
        if (request.score() < 0 || request.score() > 100) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "score 는 0~100 이어야 합니다.");
        }
        InterviewMediaAnalysis analysis = InterviewMediaAnalysis.builder()
                .interviewSessionId(sessionId)
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

    /** 관리자/내부용 — 세션 소유권 체크 없이 미디어 분석 결과 조회 (admin 모니터링). */
    public List<MediaAnalysisResponse> listBySessionId(Long sessionId) {
        return mediaMapper.findBySessionId(sessionId).stream().map(this::toResponse).toList();
    }

    private void requireOwnedSession(Long userId, Long sessionId) {
        InterviewSession session = interviewMapper.findSessionByIdAndUserId(sessionId, userId);
        if (session == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "면접 세션을 찾을 수 없습니다.");
        }
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
                analysis.getKind(),
                parse(analysis.getTranscript()),
                parse(analysis.getMetrics()),
                analysis.getScore(),
                parse(analysis.getScoreDetail()),
                analysis.getCreatedAt());
    }
}
