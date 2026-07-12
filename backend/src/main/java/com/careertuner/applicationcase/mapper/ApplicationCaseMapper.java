package com.careertuner.applicationcase.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.applicationcase.domain.AiUsageLog;
import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.applicationcase.domain.FitAnalysis;
import com.careertuner.applicationcase.dto.AiUsageFailureResponse;

@Mapper
public interface ApplicationCaseMapper {

    void insertApplicationCase(ApplicationCase applicationCase);

    List<ApplicationCase> findApplicationCasesByUserId(@Param("userId") Long userId,
                                                       @Param("view") String view,
                                                       @Param("includeArchived") boolean includeArchived);

    ApplicationCase findApplicationCaseByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * 케이스 행을 {@code FOR UPDATE} 로 잠가 읽는다 — 수동 분석 획득 TX 와 strict 재추출 획득 TX 가
     * 같은 행 잠금으로 직렬화되어, 스냅샷 검사 사이의 TOCTOU(동시 재추출·분석 시작)를 없앤다.
     * 짧은 획득 트랜잭션 안에서만 호출한다(원격 OCR/LLM 호출은 잠금 밖).
     */
    ApplicationCase lockApplicationCaseById(@Param("id") Long id);

    int updateApplicationCase(ApplicationCase applicationCase);

    int updateApplicationCaseSourceType(@Param("id") Long id,
                                        @Param("userId") Long userId,
                                        @Param("sourceType") String sourceType);

    int markAnalysisStarted(@Param("id") Long id,
                            @Param("userId") Long userId,
                            @Param("previousStatus") String previousStatus);

    int markReadyAfterAnalysis(@Param("id") Long id,
                               @Param("userId") Long userId,
                               @Param("previousStatus") String previousStatus);

    int restoreAnalysisStatus(@Param("id") Long id,
                              @Param("userId") Long userId,
                              @Param("previousStatus") String previousStatus);

    int softDeleteApplicationCase(@Param("id") Long id, @Param("userId") Long userId);

    int restoreDeletedApplicationCase(@Param("id") Long id, @Param("userId") Long userId);

    int hideApplicationCaseFromTrash(@Param("id") Long id, @Param("userId") Long userId);

    void insertStatusHistory(@Param("applicationCaseId") Long applicationCaseId,
                             @Param("changedByUserId") Long changedByUserId,
                             @Param("previousStatus") String previousStatus,
                             @Param("newStatus") String newStatus,
                             @Param("memo") String memo);

    /** 상태 변경 타임라인(관리자 상세 노출용, 최신순). */
    java.util.List<com.careertuner.applicationcase.domain.ApplicationCaseStatusHistory> findStatusHistoryByCaseId(
            @Param("applicationCaseId") Long applicationCaseId);

    FitAnalysis findLatestFitAnalysisByCaseId(Long applicationCaseId);

    void insertAiUsageLog(AiUsageLog aiUsageLog);

    List<AiUsageFailureResponse> findBFailureLogsByCaseId(@Param("applicationCaseId") Long applicationCaseId,
                                                          @Param("limit") int limit);
}
