package com.careertuner.profile.dto;

/**
 * 이미 업로드된 파일({@code fileId})을 프로필 텍스트 필드로 가져오기.
 * {@code target}: {@code RESUME_TEXT} | {@code SELF_INTRO}
 */
public record ProfileDocumentImportRequest(
        Long fileId,
        String target,
        /**
         * 문서 가져오기를 시작할 때 클라이언트가 읽은 user_profile.version_no.
         * 아직 입력이 하나도 없는 초기 프로필만 null을 허용한다.
         */
        Integer baseVersionNo
) {
}
