package com.careertuner.companyanalysis.websearch;

/** 네이버 검색 API 카테고리(235 §11 확정 엔드포인트 4종). */
public enum NaverSearchCategory {

    NEWS("news.json"),
    ENCYC("encyc.json"),
    WEBKR("webkr.json"),
    BLOG("blog.json");

    private final String endpoint;

    NaverSearchCategory(String endpoint) {
        this.endpoint = endpoint;
    }

    public String endpoint() {
        return endpoint;
    }
}
