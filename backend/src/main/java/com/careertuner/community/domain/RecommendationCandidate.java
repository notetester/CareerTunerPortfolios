package com.careertuner.community.domain;

import lombok.Data;

/**
 * 추천 알림 후보 사용자 행 — user_profile 에서 매칭 재료(희망 직무·스킬)만 뽑아온다.
 * <p>SQL 은 "ACTIVE + desired_job 보유" 로 후보를 좁히기만 하고,
 * 실제 토큰 매칭은 Java({@code RecommendationTokenMatcher})에서 수행한다.
 */
@Data
public class RecommendationCandidate {

    private Long userId;
    /** 희망 직무 (예: "백엔드 개발자"). NULL/빈 값 후보는 SQL 에서 제외된다. */
    private String desiredJob;
    /** 보유 스킬 JSON 배열 문자열 (예: ["React","Spring"]). 없을 수 있다. */
    private String skillsJson;
}
