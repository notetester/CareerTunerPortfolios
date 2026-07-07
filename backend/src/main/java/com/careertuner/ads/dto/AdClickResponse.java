package com.careertuner.ads.dto;

/**
 * 클릭 처리 응답. SPA 라 서버 302 대신 이동할 URL 을 반환하고 프런트가 이동시킨다.
 * click_count 는 이 응답 반환 전 +1 된다. linkUrl 이 없으면 이동하지 않는다.
 */
public record AdClickResponse(Long id, String linkUrl) {
}
