package com.careertuner.admin.company.dto;

/** 반려 요청 — 사유 필수(서비스에서 검증). 기업 신청/공고 검토 공용. */
public record AdminRejectRequest(String reason) {
}
