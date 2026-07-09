package com.careertuner.ads.service;

import java.util.List;

import com.careertuner.ads.dto.AdClickResponse;
import com.careertuner.ads.dto.AdResponse;

/** 광고 공개 노출 서비스. */
public interface AdService {

    /**
     * 배치·플랫폼에 맞는 광고를 최대 {@code limit} 개 선택한다.
     * 유료플랜(users.plan != FREE) 사용자에게는 빈 목록을 반환한다(userId=null 이면 비로그인 → 노출).
     */
    List<AdResponse> serve(Long userId, String placement, String platform, int limit);

    /** 노출 집계 +1 (best-effort — 존재하지 않으면 무시). */
    void recordImpression(Long adId);

    /** 클릭 집계 +1 후 이동 URL 반환. 광고가 없으면 예외. */
    AdClickResponse recordClick(Long adId);
}
