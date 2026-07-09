package com.careertuner.admin.legal.dto;

import jakarta.validation.constraints.Size;

/**
 * 새 초안 생성 요청.
 * versionLabel 미지정 시 서비스가 기본값을 생성한다.
 * cloneFromCurrent=true 면 현행 시행본(live) 조항을 복제한다.
 */
public record CreateLegalDraftRequest(
        @Size(max = 20) String versionLabel,
        Boolean cloneFromCurrent
) {
}
