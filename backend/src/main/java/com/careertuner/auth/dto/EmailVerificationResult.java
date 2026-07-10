package com.careertuner.auth.dto;

/** 이메일 인증 처리 결과와 요청이 시작된 프런트엔드 클라이언트. */
public record EmailVerificationResult(boolean success, String frontendClient) {
}
