package com.careertuner.community.service;

import java.util.Map;

public interface ReactionSettingService {

    /** 리액션 종류별 유지/해지 설정 조회(키: like/dislike/recommend/disrecommend/bookmark, 값: keep|release). */
    Map<String, String> getRetentionSettings(Long userId);

    /** 리액션 유지/해지 설정 저장 — 알 수 없는 키/값은 거부. 부분 갱신(보낸 키만 반영). */
    Map<String, String> updateRetentionSettings(Long userId, Map<String, String> settings);
}
