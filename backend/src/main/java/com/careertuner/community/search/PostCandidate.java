package com.careertuner.community.search;

import lombok.Data;

/**
 * 2단계 검색 1단계(SQL 후보) 결과 행. embedding(JSON 문자열) 포함 — 2단계 코사인에서 파싱.
 */
@Data
public class PostCandidate {
    private Long id;
    private Long userId;      // 작성자 — 뷰어 개인 차단 필터 판정용
    private boolean anonymous; // is_anonymous AS anonymous (표면 키 분기용)
    private String title;
    private String content;
    private String embedding; // JSON 문자열 (bge-m3 1024차원)
}
