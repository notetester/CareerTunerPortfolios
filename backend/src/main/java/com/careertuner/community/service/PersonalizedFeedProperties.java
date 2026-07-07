package com.careertuner.community.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * 커뮤니티 개인화 피드(7:3 혼합) 설정.
 *
 * <p>{@code community.feed.personalized-ratio} 로 개인화:신선/인기 혼합 비율만 조정한다(기본 0.7 → 7:3).
 * 목/실 토글이 따로 없는 이유: 개인화 신호가 없는 신규·비로그인 사용자는 서비스가 자연히
 * 신선·인기 폴백으로 흐르므로, 별도 목 없이도 동작한다.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "community.feed")
public class PersonalizedFeedProperties {

    /**
     * 개인화 후보 비율(0.0~1.0). 기본 0.7 → 개인화 7 : 신선/인기 3.
     * 인터리브 시 이 비율로 두 풀에서 번갈아 뽑는다. 범위를 벗어나면 서비스에서 0.7 로 보정.
     */
    private double personalizedRatio = 0.7;

    /** 후보 조회 상한(개인화·신선 각각). 페이지 경계를 넉넉히 넘기려 넓게 잡는다. */
    private int candidateLimit = 300;
}
