package com.careertuner.admin.settings.dto;

/** 관리자 공고 업로드 크기 한도 변경 요청. maxBytes 는 새 실효 한도(바이트). */
public record AdminJobPostingUploadLimitSettingRequest(
        Long maxBytes
) {
}
