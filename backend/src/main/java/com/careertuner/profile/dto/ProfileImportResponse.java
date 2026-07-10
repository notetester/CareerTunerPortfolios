package com.careertuner.profile.dto;

/** 문서 import 결과. truncated=true 이면 MAX_IMPORT_CHARS 로 잘린 뒤 저장된 것. */
public record ProfileImportResponse(
        UserProfileResponse profile,
        boolean truncated
) {
}
