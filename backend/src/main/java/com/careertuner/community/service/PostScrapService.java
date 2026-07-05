package com.careertuner.community.service;

import java.util.Map;

import com.careertuner.community.dto.ScrapResponse;

public interface PostScrapService {

    /** 스크랩 토글 — 등록 시 스냅샷 저장. 응답 {active, scrapCount}. */
    Map<String, Object> toggleScrap(Long postId, boolean anonymous, Long userId);

    ScrapResponse.Page getMyScraps(Long userId, int page, int size);

    /** 스크랩 상세 열람 — 스냅샷 기반(원본 삭제/수정과 무관). 본인 것만. */
    ScrapResponse getScrapDetail(Long scrapId, Long userId);

    /** 스크랩 삭제(원본 소실로 토글이 불가능한 행 정리용). 본인 것만. */
    void deleteScrap(Long scrapId, Long userId);
}
