package com.careertuner.support.dto;

/**
 * GitHub README 불러오기 응답 — HTTP 는 항상 200(ApiResponse.ok), 성공 여부는 ok 필드로 구분한다.
 * errorCode: NOT_FOUND(저장소·README 없음) | RATE_LIMITED(요청 한도 초과) | FETCH_FAILED(그 외 실패).
 */
public record GithubReadmeResponse(boolean ok, String text, String errorCode) {

    public static GithubReadmeResponse success(String text) {
        return new GithubReadmeResponse(true, text, null);
    }

    public static GithubReadmeResponse failure(String errorCode) {
        return new GithubReadmeResponse(false, null, errorCode);
    }
}
