package com.careertuner.collaboration.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 방 메타/공개 정책 수정 요청 (일반 탭).
 * 모든 필드는 nullable — null 이면 해당 항목을 변경하지 않는다(부분 수정).
 * type 은 PUBLIC↔PRIVATE 전환에만 쓰고, password 관련은 passwordAction 으로 제어한다.
 */
public record ConversationSettingsUpdateRequest(
        @Size(max = 120) String title,
        @Size(max = 500) String description,
        @Size(max = 1000) String notice,
        /** 방 프로필 사진 file_asset id. 0 또는 음수면 이미지 제거. */
        Long imageFileId,
        /** 공개/비공개 전환 대상 유형(PUBLIC / PRIVATE). null 이면 유지. GROUP/DIRECT 는 전환 불가. */
        @Size(max = 20) String type,
        /** 비밀번호 처리: SET(새 비밀번호 설정/변경) / CLEAR(해제) / null(유지). */
        @Size(max = 10) String passwordAction,
        @Size(max = 120) String password,
        @Min(2) @Max(1000) Integer maxMembers,
        /** OWNER_ONLY / MANAGERS / SPECIFIC_MEMBERS / ALL_MEMBERS. */
        @Size(max = 20) String invitePolicy,
        Boolean allowAnonymous,
        Boolean anonymousOnly
) {
}
