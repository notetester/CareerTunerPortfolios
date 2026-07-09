package com.careertuner.admin.ads.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 광고 등록/수정 요청. placement/targetPlatform 화이트리스트는 서비스에서 검증한다.
 */
public record AdminAdRequest(
        @NotBlank(message = "제목은 필수입니다.")
        @Size(max = 200, message = "제목은 200자 이하여야 합니다.")
        String title,
        Long imageFileId,
        @Size(max = 1000, message = "링크 URL 은 1000자 이하여야 합니다.")
        String linkUrl,
        @NotBlank(message = "배치는 필수입니다.")
        String placement,
        String targetPlatform,
        LocalDateTime startAt,
        LocalDateTime endAt,
        Boolean active,
        Integer priority,
        Integer weight) {
}
