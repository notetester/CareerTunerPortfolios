package com.careertuner.interview.media;

import java.util.List;
import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;
import com.careertuner.interview.media.dto.AvatarScoreRequest;
import com.careertuner.interview.media.dto.AvatarScoreResponse;
import com.careertuner.interview.media.dto.AvatarSessionResponse;
import com.careertuner.interview.media.dto.MediaAnalysisResponse;
import com.careertuner.interview.media.dto.SaveMediaAnalysisRequest;
import com.careertuner.interview.media.dto.TranscribeRequest;
import com.careertuner.interview.media.dto.TranscribeResponse;
import com.careertuner.interview.media.dto.VoiceAnalysisRequest;
import com.careertuner.interview.media.dto.VoiceAnalysisResponse;
import com.careertuner.interview.media.dto.VoiceScoreRequest;
import com.careertuner.interview.media.dto.VoiceScoreResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 음성 모의면접/아바타 화상 면접 — 외부 키 프록시와 분석 결과 저장.
 * 원본 음성·영상은 서버로 올라오지 않는다. 분석은 온디바이스, 저장은 점수(JSON)만 (ADR-002).
 */
@RestController
@RequestMapping("/api/interview")
@RequiredArgsConstructor
public class InterviewMediaController {

    private final InterviewMediaService mediaService;
    private final InterviewAvatarService avatarService;
    private final InterviewVoiceService voiceService;
    private final InterviewAvatarProperties avatarProperties;

    /** 키 보유 여부 — 프런트가 기능 활성/비활성을 미리 판단한다 (키 노출 없음). */
    @GetMapping("/media/capabilities")
    public ApiResponse<Map<String, Boolean>> capabilities() {
        return ApiResponse.ok(Map.of(
                "voiceProfiling", voiceService.enabled(),
                "nonverbal", mediaService.nonverbalEnabled(),
                "avatar", avatarProperties.configured(),
                "avatarSandbox", avatarProperties.isSandbox()));
    }

    /** 음성 감정 분석 (Inworld voice profiling). 오디오는 분석 후 버려진다. */
    @PostMapping("/sessions/{sessionId}/voice-analysis")
    public ApiResponse<VoiceAnalysisResponse> analyzeVoice(@AuthenticationPrincipal AuthUser authUser,
                                                           @PathVariable Long sessionId,
                                                           @Valid @RequestBody VoiceAnalysisRequest request) {
        return ApiResponse.ok(mediaService.analyzeVoice(authUser.id(), sessionId, request));
    }

    /** 음성 답변 → 자체 추론 서버 점수 (ADR-006, Inworld 대체). 원본 음성은 점수 산출 후 버려진다. */
    @PostMapping("/sessions/{sessionId}/voice-score")
    public ApiResponse<VoiceScoreResponse> scoreVoice(@AuthenticationPrincipal AuthUser authUser,
                                                      @PathVariable Long sessionId,
                                                      @Valid @RequestBody VoiceScoreRequest request) {
        return ApiResponse.ok(mediaService.scoreVoice(authUser.id(), sessionId, request));
    }

    /** 아바타 화상면접 → 자체 추론 서버 음성+영상 점수 (late fusion, ADR-006/007). 원본 영상은 점수 산출 후 버려진다. */
    @PostMapping("/sessions/{sessionId}/avatar-score")
    public ApiResponse<AvatarScoreResponse> scoreAvatar(@AuthenticationPrincipal AuthUser authUser,
                                                        @PathVariable Long sessionId,
                                                        @Valid @RequestBody AvatarScoreRequest request) {
        return ApiResponse.ok(mediaService.scoreAvatar(authUser.id(), sessionId, request));
    }

    /** 음성 답변 → 자체 STT 전사 (B 베이직, faster-whisper). 원본 음성은 전사 후 버려진다. */
    @PostMapping("/sessions/{sessionId}/voice-transcribe")
    public ApiResponse<TranscribeResponse> transcribe(@AuthenticationPrincipal AuthUser authUser,
                                                      @PathVariable Long sessionId,
                                                      @Valid @RequestBody TranscribeRequest request) {
        return ApiResponse.ok(mediaService.transcribe(authUser.id(), sessionId, request));
    }

    /** 아바타 화상 면접 세션 토큰 발급 (LiveAvatar, API 키는 서버측 보관). */
    @PostMapping("/sessions/{sessionId}/avatar-token")
    public ApiResponse<AvatarSessionResponse> createAvatarSession(@AuthenticationPrincipal AuthUser authUser,
                                                                  @PathVariable Long sessionId) {
        return ApiResponse.ok(avatarService.createSession(authUser.id(), sessionId));
    }

    /** 음성/영상 면접 분석 결과(트랜스크립트 + 지표 + 점수) 저장. */
    @PostMapping("/sessions/{sessionId}/media-results")
    public ApiResponse<MediaAnalysisResponse> saveMediaResult(@AuthenticationPrincipal AuthUser authUser,
                                                              @PathVariable Long sessionId,
                                                              @Valid @RequestBody SaveMediaAnalysisRequest request) {
        return ApiResponse.ok(mediaService.save(authUser.id(), sessionId, request));
    }

    /** 세션의 저장된 분석 결과 목록 (최신순). */
    @GetMapping("/sessions/{sessionId}/media-results")
    public ApiResponse<List<MediaAnalysisResponse>> listMediaResults(@AuthenticationPrincipal AuthUser authUser,
                                                                     @PathVariable Long sessionId) {
        return ApiResponse.ok(mediaService.list(authUser.id(), sessionId));
    }
}
