package com.careertuner.admin.settings.dto;

import com.careertuner.jobposting.service.JobPostingUploadLimitPolicy.UploadLimitSnapshot;

/**
 * 관리자 공고 업로드 크기 한도 설정 응답. maxBytes 는 현재 실효 한도,
 * minBytes/maxAllowedBytes 는 관리자가 조정 가능한 범위(servlet multipart cap 과 정합).
 */
public record AdminJobPostingUploadLimitSettingResponse(
        long maxBytes,
        long minBytes,
        long maxAllowedBytes,
        String source
) {
    public static AdminJobPostingUploadLimitSettingResponse from(UploadLimitSnapshot snapshot) {
        return new AdminJobPostingUploadLimitSettingResponse(
                snapshot.maxBytes(),
                snapshot.minBytes(),
                snapshot.maxAllowedBytes(),
                snapshot.source());
    }
}
