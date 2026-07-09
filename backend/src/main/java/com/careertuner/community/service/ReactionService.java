package com.careertuner.community.service;

import java.util.List;

import com.careertuner.community.dto.PostReactorResponse;
import com.careertuner.community.dto.ToggleReactionRequest;
import com.careertuner.community.dto.ToggleReactionResponse;

public interface ReactionService {

    /**
     * 리액션 토글 — 같은 축(추천/비추천, 좋아요/싫어요)에서 반대 리액션 클릭 시 교체,
     * 같은 것 재클릭 시 취소. 응답은 토글 후 카운트 전체(응답 기반 UI 갱신).
     */
    ToggleReactionResponse toggleReaction(ToggleReactionRequest request, Long userId);

    /** 게시글 반응자 목록 — 익명 리액션은 본인 것만 포함(타인 시점 제외, 집계에는 포함). */
    List<PostReactorResponse> getPostReactors(Long postId, Long viewerId);
}
