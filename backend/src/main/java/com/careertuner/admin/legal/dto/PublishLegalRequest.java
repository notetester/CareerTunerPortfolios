package com.careertuner.admin.legal.dto;

import java.time.LocalDateTime;

/**
 * 게시 요청. effectiveDate 미지정(null)이면 즉시(now) 게시.
 */
public record PublishLegalRequest(
        LocalDateTime effectiveDate
) {
}
