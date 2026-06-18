package com.careertuner.admin.interview.dto;

import java.util.List;

/** 관리자 면접 세션 목록 페이지(번호 기반 페이징). */
public record AdminInterviewSessionPage(
        List<AdminInterviewSessionRow> items,
        long total,
        int page,
        int size) {
}
