package com.careertuner.admin.legal.dto;

/**
 * 게시 결과. is_adverse 변경인데 리드타임이 부족하면 warning 에 안내가 담긴다(차단 아님).
 */
public record PublishLegalResponse(
        AdminLegalVersionDetail version,
        String warning
) {
}
