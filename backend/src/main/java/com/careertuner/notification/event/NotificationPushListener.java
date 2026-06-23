package com.careertuner.notification.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.careertuner.notification.push.PushDispatcher;

/**
 * 알림 insert 트랜잭션 커밋 후, 비동기로 푸시를 발송하는 리스너.
 *
 * @Async("notificationExecutor"): NotificationAsyncConfig의 전용 스레드 풀에서 실행
 *   → 호출자(결제 등) 트랜잭션·웹 스레드가 외부 HTTP 발송에 묶이지 않는다.
 * @TransactionalEventListener(AFTER_COMMIT): 트랜잭션 커밋 후에만 실행
 *   → 롤백된 알림은 푸시하지 않는다(유령 푸시 방지).
 * fallbackExecution = true: 트랜잭션 없이 notify()가 호출된 경로에서도 실행
 *   → AFTER_COMMIT 미발화로 인한 푸시 유실 방지.
 */
@Component
public class NotificationPushListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationPushListener.class);

    private final PushDispatcher pushDispatcher;

    public NotificationPushListener(PushDispatcher pushDispatcher) {
        this.pushDispatcher = pushDispatcher;
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(NotificationPushEvent event) {
        try {
            pushDispatcher.dispatch(event.notification());
        } catch (Exception ex) {
            // 푸시는 보조 채널 — 실패해도 in-app 알림에는 영향 주지 않는다.
            Long id = event.notification() == null ? null : event.notification().getId();
            log.error("푸시 발송 리스너 실패 notificationId={}", id, ex);
        }
    }
}
