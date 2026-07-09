package com.careertuner.privacy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 콘텐츠 id 기반 계정 차단 생성 — 익명 글/댓글용 (docs/PERSONAL_BLOCK_POLICY.md §4-3 차단 진입점).
 * 클라이언트는 작성자 id 를 모르고 서버가 콘텐츠에서 작성자를 찾는다.
 * 익명 콘텐츠면 차단 목록에 실명 대신 masked_label 이 표시돼 익명성이 유지된다.
 */
public record UserBlockByContentRequest(
        @NotBlank @Pattern(regexp = "POST|COMMENT", message = "contentType 은 POST/COMMENT 만 가능합니다.") String contentType,
        @NotNull Long contentId,
        Boolean blockIp,
        @Size(max = 200) String memo
) {}
