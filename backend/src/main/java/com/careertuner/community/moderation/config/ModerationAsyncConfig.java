package com.careertuner.community.moderation.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 게시글 검열 전용 비동기 스레드 풀.
 *
 * 글 작성 API 응답 시간에 영향을 주지 않기 위해,
 * 검열 작업은 이 풀에서 비동기로 실행된다.
 *
 * - core/max 2: 동시 검열 2건
 * - queue 100: 스레드가 바쁘면 100건까지 대기
 * - 큐 포화 시 로그 남기고 폐기 (재시도 배치에서 회수)
 */
@Configuration
@EnableAsync
public class ModerationAsyncConfig {

    private static final Logger log = LoggerFactory.getLogger(ModerationAsyncConfig.class);

    /** 종료 대기에 얹는 여유분(초) — LLM read timeout 이후 응답 처리·DB 기록에 필요한 시간. */
    private static final long SHUTDOWN_GRACE_SECONDS = 5;

    private final OllamaProperties ollamaProperties;

    public ModerationAsyncConfig(OllamaProperties ollamaProperties) {
        this.ollamaProperties = ollamaProperties;
    }

    @Bean("moderationExecutor")
    public ThreadPoolTaskExecutor moderationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("moderation-");
        // 큐 포화 시: 예외를 웹 스레드로 전파시키지 않고 로그만 남기고 폐기.
        // (전파되면 "글은 저장됐는데 사용자는 500"이라는 사고가 난다)
        executor.setRejectedExecutionHandler((task, exec) ->
                log.warn("검열 큐 포화 — 작업 폐기됨. 미검열 글은 재시도 배치에서 회수"));
        // 재배포/종료 시 진행 중인 검열을 끝내고 내려간다 (PENDING 좀비 방지)
        executor.setWaitForTasksToCompleteOnShutdown(true);
        // ai.ollama.read-timeout 에서 파생 — 하드코딩하면 yaml 이 값을 덮을 때 조용히 stale 해진다
        // (실제로 그랬다: 클래스 기본값 30s 기준 35초였는데 application.yaml 이 60s 로 덮어씀).
        // 재시도·다중 이미지까지 모두 끝나길 기다리진 않는다 — 남은 PENDING 은 재시도 배치가 회수한다.
        executor.setAwaitTerminationSeconds(
                (int) (ollamaProperties.getReadTimeout().toSeconds() + SHUTDOWN_GRACE_SECONDS));
        return executor;
    }
}
