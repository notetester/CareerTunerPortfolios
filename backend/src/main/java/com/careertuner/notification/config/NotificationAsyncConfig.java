package com.careertuner.notification.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 알림 푸시 발송 전용 비동기 스레드 풀.
 *
 * 알림 insert 트랜잭션·호출자 웹 스레드가 외부 푸시 HTTP에 묶이지 않도록,
 * 푸시 발송은 이 풀에서 비동기로 실행된다.
 *
 * - core 2 / max 4: 동시 푸시 발송 처리
 * - queue 200: 스레드가 바쁘면 200건까지 대기
 * - 큐 포화 시: CallerRunsPolicy 로 폴백(푸시 유실 최소화)
 *
 * @EnableAsync 는 ModerationAsyncConfig 에 이미 선언돼 전역 활성이므로 여기서는 선언하지 않는다.
 * 검열 전용 moderationExecutor 와 성격이 달라 별도 풀로 분리한다.
 */
@Configuration
public class NotificationAsyncConfig {

    private static final Logger log = LoggerFactory.getLogger(NotificationAsyncConfig.class);

    @Bean("notificationExecutor")
    public ThreadPoolTaskExecutor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("notif-push-");
        // 큐 포화 시: 폐기 대신 호출 스레드에서 실행해 푸시 유실을 줄인다.
        executor.setRejectedExecutionHandler((task, exec) -> {
            log.warn("푸시 발송 큐 포화 — 호출 스레드에서 직접 실행(CallerRuns)");
            if (!exec.isShutdown()) {
                task.run();
            }
        });
        // 재배포/종료 시 진행 중인 발송을 끝내고 내려간다.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(35);
        return executor;
    }
}
