package com.careertuner.company.event;

/** 기업 신청 승인/반려 확정 이벤트 — 커밋 후 신청자에게 COMPANY_APPLY_RESULT 알림을 보낸다. */
public record CompanyApplicationReviewedEvent(Long applicationId,
                                              Long applicantUserId,
                                              String companyName,
                                              boolean approved,
                                              String rejectReason) {
}
