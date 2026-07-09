package com.careertuner.community.search;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * 커뮤니티 2단계 검색 설정. ChatbotProperties(ai.chatbot.*) 패턴 복제.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "ai.community-search")
public class CommunitySearchProperties {

    /** 코사인 유사도 임계값 (미만이면 결과에서 제외) */
    private double similarityThreshold = 0.5;

    /** 1회 재검색용 완화 임계값. 1차에서 통과 결과가 0이면 이 값으로 한 번만 재선별(루프 아님). */
    private double retryThreshold = 0.35;

    /** 최종 반환 상위 K개 */
    private int topK = 3;

    /** 1단계 SQL 후보 상한 (글 수가 늘어도 코사인 비용을 이 선에서 캡) */
    private int candidateLimit = 300;

    /** 1단계 후보가 이 수 미만이면 필터를 완화해 재조회 (재현율 보호) */
    private int minCandidates = 20;
}
