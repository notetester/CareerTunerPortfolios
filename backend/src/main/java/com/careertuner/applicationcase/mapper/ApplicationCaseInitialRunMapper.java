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

    /**
     * 재추출(retry) 시 FAILED 프로필을 PENDING 으로 되살려 재추출 성공 시 초기 파이프라인이 다시 claim 할 수 있게 한다.
     * FAILED 만 되살린다(DONE 은 초기 실행 완료라 재개 금지) — 프로필이 없거나 FAILED 아니면 0행, 무해.
     */
    int reopenForRetry(@Param("applicationCaseId") Long applicationCaseId);
}
