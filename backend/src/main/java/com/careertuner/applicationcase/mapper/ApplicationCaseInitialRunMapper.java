package com.careertuner.applicationcase.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.applicationcase.domain.ApplicationCaseInitialRun;

@Mapper
public interface ApplicationCaseInitialRunMapper {

    /** 등록 시 PENDING 프로필 생성(케이스 1:1). job/company 선택값 스냅샷. */
    void insertPending(ApplicationCaseInitialRun run);

    ApplicationCaseInitialRun findByApplicationCaseId(@Param("applicationCaseId") Long applicationCaseId);

    /** PENDING → RUNNING 조건부 claim(중복 진입 방지). 성공 시 1, 이미 claim/완료됐으면 0. */
    int claimForRun(@Param("applicationCaseId") Long applicationCaseId,
                    @Param("executionToken") String executionToken);

    /**
     * PENDING → FAILED 조건부 단일 전이(원자적 종결). 재추출이 아직 시작 안 한 초기 실행 프로필을 RUNNING 을
     * 거치지 않고 한 번에 닫는다 — claim+markFailed 2단계 사이의 크래시로 RUNNING 고착되는 창을 없앤다.
     * PENDING 이 아니면(그 사이 파이프라인이 선점/완료) 0행 → 호출부가 경합으로 처리.
     */
    int closePendingAsFailed(@Param("applicationCaseId") Long applicationCaseId,
                             @Param("failureReason") String failureReason);

    /** RUNNING → DONE. execution_token 으로 fencing(늦은 완료가 다른 실행을 덮지 않게). */
    int markDone(@Param("applicationCaseId") Long applicationCaseId,
                 @Param("executionToken") String executionToken);

    /** RUNNING → FAILED. 정상 실패 경로와 stale-reaper 가 공유(둘 다 자신이 관측한 token 으로 fencing). */
    int markFailed(@Param("applicationCaseId") Long applicationCaseId,
                   @Param("executionToken") String executionToken,
                   @Param("failureReason") String failureReason);

    /** stale-reaper: RUNNING 이면서 started_at 이 임계 이전인 프로필(자동 재시도 없이 FAILED 처리 대상). */
    List<ApplicationCaseInitialRun> findStaleRunning(@Param("timeoutMinutes") long timeoutMinutes,
                                                     @Param("limit") int limit);
}
