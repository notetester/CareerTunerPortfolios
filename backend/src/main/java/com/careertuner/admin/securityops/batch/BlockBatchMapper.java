package com.careertuner.admin.securityops.batch;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** IP 정책 배치 + 배치 규칙 + 오퍼레이션 매퍼. */
@Mapper
public interface BlockBatchMapper {

    /** 배치 생성(useGeneratedKeys 로 batch.id 채움). */
    void insertBatch(IpBlockBatch batch);

    IpBlockBatchRow findBatchById(@Param("id") Long id);

    List<IpBlockBatchRow> findBatches(@Param("keyword") String keyword,
                                      @Param("active") Boolean active,
                                      @Param("limit") int limit);

    /** 배치에 귀속된 차단 규칙 1건 삽입(control_mode='BATCH'). */
    void insertBatchRule(@Param("ruleType") String ruleType,
                         @Param("ruleValue") String ruleValue,
                         @Param("actionType") String actionType,
                         @Param("category") String category,
                         @Param("reason") String reason,
                         @Param("priority") int priority,
                         @Param("batchId") Long batchId,
                         @Param("createdBy") Long createdBy);

    /** 같은 (타입,값) 규칙이 이미 있으면 count>0 → 중복 skip 판정용. */
    int countRuleByTypeValue(@Param("ruleType") String ruleType, @Param("ruleValue") String ruleValue);

    int countBatchRules(@Param("batchId") Long batchId);

    int countActiveBatchRules(@Param("batchId") Long batchId);

    void updateBatchCounts(@Param("batchId") Long batchId,
                           @Param("total") int total,
                           @Param("active") int active,
                           @Param("updatedBy") Long updatedBy);

    int setBatchActive(@Param("id") Long id, @Param("active") boolean active, @Param("updatedBy") Long updatedBy);

    /** cascade — 배치 하위 규칙 활성 일괄 변경(MANUAL_OVERRIDE 규칙은 제외). @return 영향 규칙 수 */
    int setRulesActiveByBatch(@Param("batchId") Long batchId, @Param("active") boolean active);

    void insertBatchOperation(@Param("batchId") Long batchId,
                              @Param("operationType") String operationType,
                              @Param("operationOption") String operationOption,
                              @Param("requested") int requested,
                              @Param("affected") int affected,
                              @Param("actorUserId") Long actorUserId,
                              @Param("memo") String memo);
}
