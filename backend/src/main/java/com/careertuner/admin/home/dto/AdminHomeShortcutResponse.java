package com.careertuner.admin.home.dto;

/**
 * 관리자 홈 바로가기 항목(C 담당). 운영자가 자주 진입하는 관리자 화면 경로를 안내한다.
 */
public record AdminHomeShortcutResponse(
        String label,
        String path,
        String description
) {
}
