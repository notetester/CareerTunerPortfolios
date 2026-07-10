package com.careertuner.applicationcase.service;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.careertuner.applicationcase.domain.ApplicationCaseInitialRun;
import com.careertuner.applicationcase.mapper.ApplicationCaseInitialRunMapper;

/**
 * 초기 실행 프로필 stale-reaper.
 *
 * <p>배경: 초기 자동 파이프라인은 프로필을 PENDING→RUNNING 으로 claim 한 뒤 실행한다. 실행 스레드가
 * 프로세스 종료·JVM 크래시 등으로 정상 종료(DONE/FAILED)를 남기지 못하면 프로필이 RUNNING 에 갇혀,
 * 사용자의 수동 재분석이 CONFLICT 로 영구 차단된다(ApplicationCaseServiceImpl#guardInitialRunNotInProgress).
 *
 * <p>해결: started_at 이 임계 이전인 RUNNING 프로필을 주기적으로 회수해 FAILED 로 내린다. 자동 재시도는
 * 하지 않는다(사용자가 수동으로 재추출/재분석). markFailed 는 프로필이 관측된 순간의 execution_token 으로
 * fencing 하므로, 판정과 갱신 사이에 실제 실행이 DONE/FAILED 로 끝났다면(토큰 불일치·state 변경) 덮어쓰지 않는다.
 *
 * <p>@EnableScheduling 은 ApplicationCaseExtractionSchedulingConfig 에 이미 선언돼 있어 여기서 다시 선언하지 않는다.
 * 스케줄러는 외부 빈에서 호출되므로 프록시 self-invocation 문제가 없다.
 */
@Component
public class ApplicationCaseInitialRunReaper {

    private static final Logger log = LoggerFactory.getLogger(ApplicationCaseInitialRunReaper.class);

    private static final String FAILURE_REASON = "초기 실행이 임계 시간을 초과해 자동 회수되었습니다(stale-reaper).";

    private final ApplicationCaseInitialRunMapper initialRunMapper;

    /** RUNNING 이 이 시간(분)을 넘기면 stale 로 간주한다. 정상 파이프라인 소요를 넉넉히 넘는 값. */
    @Value("${careertuner.application-case.initial-run.stale-timeout-minutes:30}")
    private long staleTimeoutMinutes = 30;

    /** 한 주기에 회수할 최대 건수(폭주 방지). */
    @Value("${careertuner.application-case.initial-run.reaper-batch-limit:50}")
    private int batchLimit = 50;

    /** 직전 실행이 길어질 때 다음 실행이 겹치지 않도록 보호. */
    private final AtomicBoolean running = new AtomicBoolean(false);

    public ApplicationCaseInitialRunReaper(ApplicationCaseInitialRunMapper initialRunMapper) {
        this.initialRunMapper = initialRunMapper;
    }

    /**
     * 초기지연 2분, 고정지연 5분. stale RUNNING 프로필을 FAILED 로 회수한다.
     * 스케줄러 스레드가 죽지 않도록 self-contained(예외 흡수).
     */
    @Scheduled(
            initialDelayString = "${careertuner.application-case.initial-run.reaper-initial-delay-ms:120000}",
            fixedDelayString = "${careertuner.application-case.initial-run.reaper-fixed-delay-ms:300000}")
    public void reapStaleRuns() {
        if (!running.compareAndSet(false, true)) {
            log.info("초기 실행 회수 스케줄러: 직전 실행이 아직 진행 중 — 이번 주기 스킵");
            return;
        }
        try {
            List<ApplicationCaseInitialRun> stale = initialRunMapper.findStaleRunning(staleTimeoutMinutes, batchLimit);
            if (stale == null || stale.isEmpty()) {
                return;
            }
            int reaped = 0;
            for (ApplicationCaseInitialRun run : stale) {
                try {
                    // 관측된 execution_token 으로 fencing — 그 사이 실제 실행이 끝났으면(토큰/state 불일치) 0행이라 무시된다.
                    if (initialRunMapper.markFailed(run.getApplicationCaseId(), run.getExecutionToken(), FAILURE_REASON) == 1) {
                        reaped++;
                    }
                } catch (RuntimeException e) {
                    log.warn("초기 실행 회수 실패: applicationCaseId={}", run.getApplicationCaseId(), e);
                }
            }
            if (reaped > 0) {
                log.info("초기 실행 회수 스케줄러 완료: {}건 FAILED 처리(임계 {}분, 상한 {})",
                        reaped, staleTimeoutMinutes, batchLimit);
            }
        } catch (RuntimeException ex) {
            log.warn("초기 실행 회수 스케줄러 주기 스킵: {}", rootCauseMessage(ex));
        } finally {
            running.set(false);
        }
    }

    private static String rootCauseMessage(Throwable ex) {
        Throwable current = ex;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        if (message == null || message.isBlank()) {
            message = current.getClass().getSimpleName();
        }
        return message.replace('\n', ' ').replace('\r', ' ');
    }
}
