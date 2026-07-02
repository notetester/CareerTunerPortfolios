package com.careertuner.support.event;

/**
 * "새 문의(티켓)가 접수되었으니 관리자에게 알려야 한다"는 이벤트.
 * <p>티켓 저장 트랜잭션 안에서 발행하고, 리스너는 AFTER_COMMIT 에서 팬아웃한다.
 */
public record NewTicketEvent(Long ticketId, String subject) {}
