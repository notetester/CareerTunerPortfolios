package com.careertuner.company.event;

/** 기업 신청 접수 이벤트 — 커밋 후 관리자 팬아웃(NEW_COMPANY_APPLICATION)에 쓰인다. */
public record CompanyApplicationSubmittedEvent(Long applicationId, Long userId, String companyName) {
}
