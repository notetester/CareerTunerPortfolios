package com.careertuner.interview.service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

/**
 * 면접 도메인 백그라운드 작업 실행기.
 *
 * <p>HTTP 응답을 막을 필요가 없는 후처리(예: 모범답안 일괄 생성)를 별도 스레드에서 돌린다.
 * 작업은 반드시 <b>트랜잭션 커밋 이후</b>에만 제출한다 — 커밋 전에 제출하면 백그라운드 스레드의
 * 다른 DB 커넥션이 미커밋 데이터를 보지 못해 갱신이 누락될 수 있다.
 * (전용 타입이라 주입 시 {@code @Qualifier} 없이 모호성 없이 잡힌다.)
 */
@Component
public class InterviewBackgroundExecutor {

    private final ExecutorService pool = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "interview-bg");
        thread.setDaemon(true);
        return thread;
    });

    public void run(Runnable task) {
        pool.execute(task);
    }

    @PreDestroy
    void shutdown() {
        pool.shutdown();
    }
}
