package com.careertuner.billing.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.billing.domain.RefundPolicy;

@Mapper
public interface RefundPolicyMapper {

    RefundPolicy findCurrent(@Param("policyCode") String policyCode,
                             @Param("asOf") LocalDateTime asOf);

    RefundPolicy findDraft(@Param("policyCode") String policyCode);

    RefundPolicy findById(@Param("id") Long id);

    List<RefundPolicy> findRecent(@Param("policyCode") String policyCode,
                                  @Param("limit") int limit);

    Integer findMaxVersion(@Param("policyCode") String policyCode);

    void insert(RefundPolicy policy);

    int updateDraft(RefundPolicy policy);

    int publishDraft(@Param("id") Long id,
                     @Param("noticeId") Long noticeId,
                     @Param("publishedAt") LocalDateTime publishedAt);

    int insertAcknowledgement(@Param("userId") Long userId,
                              @Param("policyId") Long policyId,
                              @Param("triggerType") String triggerType,
                              @Param("actionKey") String actionKey);

    List<String> findAcknowledgedTriggers(@Param("userId") Long userId,
                                          @Param("policyId") Long policyId);

    boolean existsAcknowledgement(@Param("userId") Long userId,
                                  @Param("policyId") Long policyId,
                                  @Param("triggerType") String triggerType,
                                  @Param("actionKey") String actionKey);
}
