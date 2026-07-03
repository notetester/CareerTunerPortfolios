package com.careertuner.admin.securityops.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.admin.securityops.dto.SecurityAppealPolicyRow;
import com.careertuner.admin.securityops.dto.SecurityAppealRow;
import com.careertuner.admin.securityops.dto.SecurityBlockRuleRow;
import com.careertuner.admin.securityops.dto.SecurityOpsSummaryResponse;
import com.careertuner.admin.securityops.dto.SecurityProviderConfigRow;
import com.careertuner.admin.securityops.dto.SecurityProviderHealthHistoryRow;
import com.careertuner.admin.securityops.dto.SecurityReviewRow;
import com.careertuner.admin.securityops.dto.WafSyncEventRow;

@Mapper
public interface AdminSecurityOpsMapper {

    SecurityOpsSummaryResponse summary();

    List<SecurityBlockRuleRow> findBlockRules(@Param("keyword") String keyword,
                                              @Param("ruleType") String ruleType,
                                              @Param("active") Boolean active,
                                              @Param("limit") int limit);

    SecurityBlockRuleRow findBlockRuleById(@Param("id") Long id);

    SecurityBlockRuleRow findLatestBlockRule(@Param("ruleType") String ruleType,
                                             @Param("ruleValue") String ruleValue);

    int insertBlockRule(@Param("ruleType") String ruleType,
                        @Param("ruleValue") String ruleValue,
                        @Param("scope") String scope,
                        @Param("actionType") String actionType,
                        @Param("category") String category,
                        @Param("reason") String reason,
                        @Param("memo") String memo,
                        @Param("active") boolean active,
                        @Param("wafSyncEnabled") boolean wafSyncEnabled,
                        @Param("wafSyncStatus") String wafSyncStatus,
                        @Param("expiresAt") LocalDateTime expiresAt,
                        @Param("actorId") Long actorId);

    int updateBlockRule(@Param("id") Long id,
                        @Param("ruleType") String ruleType,
                        @Param("ruleValue") String ruleValue,
                        @Param("scope") String scope,
                        @Param("actionType") String actionType,
                        @Param("category") String category,
                        @Param("reason") String reason,
                        @Param("memo") String memo,
                        @Param("active") boolean active,
                        @Param("wafSyncEnabled") boolean wafSyncEnabled,
                        @Param("wafSyncStatus") String wafSyncStatus,
                        @Param("expiresAt") LocalDateTime expiresAt,
                        @Param("actorId") Long actorId);

    int updateBlockRuleWafStatus(@Param("id") Long id,
                                 @Param("status") String status,
                                 @Param("wafRuleId") String wafRuleId,
                                 @Param("lastSyncedAt") LocalDateTime lastSyncedAt,
                                 @Param("actorId") Long actorId);

    int insertWafEvent(@Param("blockRuleId") Long blockRuleId,
                       @Param("providerCode") String providerCode,
                       @Param("operationType") String operationType,
                       @Param("status") String status,
                       @Param("requestPayloadJson") String requestPayloadJson,
                       @Param("responsePayloadJson") String responsePayloadJson,
                       @Param("errorMessage") String errorMessage,
                       @Param("actorId") Long actorId,
                       @Param("processedAt") LocalDateTime processedAt);

    List<WafSyncEventRow> findWafEvents(@Param("keyword") String keyword,
                                        @Param("status") String status,
                                        @Param("limit") int limit);

    List<SecurityProviderConfigRow> findProviders(@Param("keyword") String keyword,
                                                  @Param("providerType") String providerType);

    SecurityProviderConfigRow findProviderByCode(@Param("providerCode") String providerCode);

    int updateProvider(@Param("providerCode") String providerCode,
                       @Param("displayName") String displayName,
                       @Param("providerType") String providerType,
                       @Param("mode") String mode,
                       @Param("enabled") boolean enabled,
                       @Param("endpointUrl") String endpointUrl,
                       @Param("configJson") String configJson,
                       @Param("actorId") Long actorId);

    int updateProviderHealth(@Param("providerCode") String providerCode,
                             @Param("healthStatus") String healthStatus,
                             @Param("actorId") Long actorId);

    int insertProviderHealthHistory(@Param("providerConfigId") Long providerConfigId,
                                    @Param("providerCode") String providerCode,
                                    @Param("providerType") String providerType,
                                    @Param("checkSource") String checkSource,
                                    @Param("statusBefore") String statusBefore,
                                    @Param("statusAfter") String statusAfter,
                                    @Param("detailMessage") String detailMessage,
                                    @Param("actorId") Long actorId);

    List<SecurityProviderHealthHistoryRow> findProviderHealthHistory(@Param("keyword") String keyword,
                                                                     @Param("statusAfter") String statusAfter,
                                                                     @Param("limit") int limit);

    List<SecurityReviewRow> findReviews(@Param("keyword") String keyword,
                                        @Param("status") String status,
                                        @Param("reviewType") String reviewType,
                                        @Param("limit") int limit);

    SecurityReviewRow findReviewById(@Param("id") Long id);

    int insertReview(@Param("reviewType") String reviewType,
                     @Param("subjectType") String subjectType,
                     @Param("subjectValue") String subjectValue,
                     @Param("riskScore") int riskScore,
                     @Param("riskLevel") String riskLevel,
                     @Param("status") String status,
                     @Param("decisionAction") String decisionAction,
                     @Param("reason") String reason,
                     @Param("evidenceJson") String evidenceJson,
                     @Param("assignedTo") Long assignedTo,
                     @Param("actorId") Long actorId);

    int updateReview(@Param("id") Long id,
                     @Param("status") String status,
                     @Param("decisionAction") String decisionAction,
                     @Param("reason") String reason,
                     @Param("assignedTo") Long assignedTo,
                     @Param("actorId") Long actorId,
                     @Param("decidedAt") LocalDateTime decidedAt);

    SecurityAppealPolicyRow findAppealPolicy();

    int updateAppealPolicy(@Param("displayName") String displayName,
                           @Param("enabled") boolean enabled,
                           @Param("allowMultipleOpen") boolean allowMultipleOpen,
                           @Param("maxOpenPerSubject") int maxOpenPerSubject,
                           @Param("submitterDailyLimit") int submitterDailyLimit,
                           @Param("tokenTtlHours") int tokenTtlHours,
                           @Param("configJson") String configJson,
                           @Param("actorId") Long actorId);

    List<SecurityAppealRow> findAppeals(@Param("keyword") String keyword,
                                        @Param("status") String status,
                                        @Param("limit") int limit);

    SecurityAppealRow findAppealById(@Param("id") Long id);

    int updateAppeal(@Param("id") Long id,
                     @Param("status") String status,
                     @Param("decisionReason") String decisionReason,
                     @Param("actorId") Long actorId,
                     @Param("reviewedAt") LocalDateTime reviewedAt);
}
