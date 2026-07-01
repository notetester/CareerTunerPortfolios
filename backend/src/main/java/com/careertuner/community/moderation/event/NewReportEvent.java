package com.careertuner.community.moderation.event;

/**
 * "게시글이 신고 접수되었으니 관리자에게 알려야 한다"는 이벤트.
 * <p>{@link ReportClassifyRequiredEvent}와 동일하게 신고 저장 트랜잭션 안에서 발행하되,
 * 리스너는 AFTER_COMMIT 에서 실행되어 커밋 후에만 관리자 알림을 팬아웃한다.
 */
public record NewReportEvent(Long postId) {}
