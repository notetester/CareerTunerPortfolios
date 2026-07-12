package com.careertuner.jobposting.dto;

/**
 * 사용자 화면(공고 업로드)에서 쓰는 현재 실효 업로드 한도. 관리자 설정값(또는 기본값)을 반영한다.
 * 프런트가 이 값으로 클라이언트 사전 검증·표기를 맞춘다(서버 측 실제 강제는 {@code JobPostingFileStorage}가 담당).
 */
public record JobPostingUploadLimitResponse(long maxBytes) {
}
