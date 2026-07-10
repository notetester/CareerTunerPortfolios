package com.careertuner.profile.dto;

/**
 * 이미 업로드된 파일({@code fileId})을 프로필 텍스트 필드로 가져오기.
 * {@code target}: {@code RESUME_TEXT} | {@code SELF_INTRO}
 */
public record ProfileDocumentImportRequest(
        Long fileId,
        String target
) {
}
