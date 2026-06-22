package com.careertuner.billing.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.careertuner.billing.domain.AiFeatureBenefitPolicy;
import com.careertuner.billing.domain.BillingPolicyChange;
import com.careertuner.billing.domain.SubscriptionBenefitPolicy;
import com.careertuner.billing.domain.SubscriptionPlan;
import com.careertuner.billing.domain.UserSubscription;
import com.careertuner.billing.mapper.BillingMapper;
import com.careertuner.billing.mapper.BillingPolicyChangeMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.credit.domain.CreditProduct;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class BillingPolicyService {

    public static final String TARGET_SUBSCRIPTION_PLAN = "SUBSCRIPTION_PLAN";
    public static final String TARGET_CREDIT_PRODUCT = "CREDIT_PRODUCT";
    public static final String TARGET_SUBSCRIPTION_BENEFIT_POLICY = "SUBSCRIPTION_BENEFIT_POLICY";
    public static final String TARGET_AI_FEATURE_BENEFIT_POLICY = "AI_FEATURE_BENEFIT_POLICY";

    private final BillingMapper billingMapper;
    private final BillingPolicyChangeMapper policyChangeMapper;
    private final ObjectMapper objectMapper;

    public List<SubscriptionPlan> activePlans() {
        return billingMapper.findActivePlans().stream()
                .map(this::applyEffectivePlanChange)
                .filter(SubscriptionPlan::isActive)
                .sorted(Comparator.comparing(plan -> valueOrMax(plan.getSortOrder())))
                .toList();
    }

    public SubscriptionPlan activePlanByCode(String planCode) {
        SubscriptionPlan plan = billingMapper.findActivePlanByCode(planCode);
        if (plan == null) {
            return null;
        }
        SubscriptionPlan effective = applyEffectivePlanChange(plan);
        return effective.isActive() ? effective : null;
    }

    public List<CreditProduct> enabledCreditProducts() {
        return billingMapper.findEnabledCreditProducts().stream()
                .map(this::applyEffectiveCreditProductChange)
                .filter(CreditProduct::isEnabled)
                .sorted(Comparator.comparingInt(CreditProduct::getSortOrder))
                .toList();
    }

    public CreditProduct enabledCreditProductByCode(String productCode) {
        CreditProduct product = billingMapper.findCreditProductByCode(productCode);
        if (product == null) {
            return null;
        }
        CreditProduct effective = applyEffectiveCreditProductChange(product);
        return effective.isEnabled() ? effective : null;
    }

    public List<SubscriptionBenefitPolicy> activeBenefitPolicies() {
        return billingMapper.findActiveBenefitPolicies().stream()
                .map(this::applyEffectiveBenefitPolicyChange)
                .filter(SubscriptionBenefitPolicy::isActive)
                .sorted(Comparator
                        .comparing(SubscriptionBenefitPolicy::getPlanCode)
                        .thenComparingInt(SubscriptionBenefitPolicy::getSortOrder))
                .toList();
    }

    public List<SubscriptionBenefitPolicy> activeBenefitPoliciesByPlan(String planCode, String policySnapshotJson) {
        List<SubscriptionBenefitPolicy> snapshotPolicies = benefitPoliciesFromSnapshot(policySnapshotJson, planCode);
        if (!snapshotPolicies.isEmpty()) {
            return snapshotPolicies;
        }
        return baseActiveBenefitPoliciesByPlan(planCode).stream()
                .map(this::applyEffectiveBenefitPolicyChange)
                .filter(SubscriptionBenefitPolicy::isActive)
                .toList();
    }

    public List<SubscriptionBenefitPolicy> activeBenefitPoliciesForSubscriptionPeriod(String planCode,
                                                                                      String policySnapshotJson) {
        List<SubscriptionBenefitPolicy> snapshotPolicies = benefitPoliciesFromSnapshot(policySnapshotJson, planCode);
        return snapshotPolicies.isEmpty() ? baseActiveBenefitPoliciesByPlan(planCode) : snapshotPolicies;
    }

    public SubscriptionBenefitPolicy activeBenefitPolicy(Long userId, String benefitCode) {
        UserSubscription subscription = billingMapper.findActiveSubscription(userId, LocalDateTime.now());
        if (subscription != null) {
            return activeBenefitPolicyForSubscriptionPeriod(
                    subscription.getPlanCode(),
                    benefitCode,
                    subscription.getPolicySnapshotJson());
        }
        String planCode = normalizePlanCode(billingMapper.findUserPlanCode(userId));
        return activeBenefitPolicy(planCode, benefitCode, null);
    }

    public SubscriptionBenefitPolicy activeBenefitPolicyForSubscriptionPeriod(String planCode,
                                                                              String benefitCode,
                                                                              String policySnapshotJson) {
        String code = normalizePlanCode(planCode);
        for (SubscriptionBenefitPolicy policy : activeBenefitPoliciesForSubscriptionPeriod(code, policySnapshotJson)) {
            if (benefitCode.equals(policy.getBenefitCode())) {
                return policy;
            }
        }
        if (!"FREE".equals(code)) {
            for (SubscriptionBenefitPolicy policy : activeBenefitPoliciesForSubscriptionPeriod("FREE", policySnapshotJson)) {
                if (benefitCode.equals(policy.getBenefitCode())) {
                    return policy;
                }
            }
        }
        return null;
    }

    public SubscriptionBenefitPolicy activeBenefitPolicy(String planCode, String benefitCode, String policySnapshotJson) {
        for (SubscriptionBenefitPolicy policy : activeBenefitPoliciesByPlan(planCode, policySnapshotJson)) {
            if (benefitCode.equals(policy.getBenefitCode())) {
                return policy;
            }
        }
        if (!"FREE".equals(planCode)) {
            for (SubscriptionBenefitPolicy policy : activeBenefitPoliciesByPlan("FREE", null)) {
                if (benefitCode.equals(policy.getBenefitCode())) {
                    return policy;
                }
            }
        }
        return null;
    }

    public List<AiFeatureBenefitPolicy> activeFeatureBenefitPolicies() {
        return billingMapper.findActiveFeatureBenefitPolicies().stream()
                .map(this::applyEffectiveFeaturePolicyChange)
                .filter(AiFeatureBenefitPolicy::isActive)
                .sorted(Comparator.comparing(AiFeatureBenefitPolicy::getFeatureType))
                .toList();
    }

    public AiFeatureBenefitPolicy activeFeatureBenefitPolicy(Long userId, String featureType) {
        UserSubscription subscription = billingMapper.findActiveSubscription(userId, LocalDateTime.now());
        if (subscription != null) {
            AiFeatureBenefitPolicy snapshotPolicy = featurePolicyFromSnapshot(subscription.getPolicySnapshotJson(), featureType);
            if (snapshotPolicy != null) {
                return snapshotPolicy;
            }
            return baseActiveFeatureBenefitPolicy(featureType);
        }
        AiFeatureBenefitPolicy policy = billingMapper.findActiveFeatureBenefitPolicy(featureType);
        if (policy == null) {
            return null;
        }
        AiFeatureBenefitPolicy effective = applyEffectiveFeaturePolicyChange(policy);
        return effective.isActive() ? effective : null;
    }

    public String subscriptionSnapshotJson(String planCode) {
        SubscriptionPlan plan = activePlanByCode(planCode);
        if (plan == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "구매 가능한 구독 플랜을 찾을 수 없습니다.");
        }
        List<SubscriptionBenefitPolicy> benefitPolicies = new ArrayList<>(activeBenefitPoliciesByPlan(planCode, null));
        if (!"FREE".equals(normalizePlanCode(planCode))) {
            benefitPolicies.addAll(activeBenefitPoliciesByPlan("FREE", null));
        }
        return writeJson(Map.of(
                "plan", snapshot(plan),
                "benefitPolicies", benefitPolicies.stream().map(this::snapshot).toList(),
                "featureBenefitPolicies", activeFeatureBenefitPolicies().stream().map(this::snapshot).toList()));
    }

    public String baseSubscriptionSnapshotJson(String planCode) {
        SubscriptionPlan plan = billingMapper.findActivePlanByCode(planCode);
        if (plan == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "구매 가능한 구독 플랜을 찾을 수 없습니다.");
        }
        List<SubscriptionBenefitPolicy> benefitPolicies = new ArrayList<>(baseActiveBenefitPoliciesByPlan(planCode));
        if (!"FREE".equals(normalizePlanCode(planCode))) {
            benefitPolicies.addAll(baseActiveBenefitPoliciesByPlan("FREE"));
        }
        return writeJson(Map.of(
                "plan", snapshot(plan),
                "benefitPolicies", benefitPolicies.stream().map(this::snapshot).toList(),
                "featureBenefitPolicies", baseActiveFeatureBenefitPolicies().stream().map(this::snapshot).toList()));
    }

    public String creditProductSnapshotJson(CreditProduct product) {
        return writeJson(snapshot(product));
    }

    public String currentSnapshotJson(String targetType, String targetCode) {
        return writeJson(currentSnapshot(targetType, targetCode));
    }

    public Map<String, Object> currentSnapshot(String targetType, String targetCode) {
        return switch (targetType) {
            case TARGET_SUBSCRIPTION_PLAN -> {
                SubscriptionPlan plan = activePlanByCode(targetCode);
                if (plan == null) {
                    throw notFound();
                }
                yield snapshot(plan);
            }
            case TARGET_CREDIT_PRODUCT -> {
                CreditProduct product = enabledCreditProductByCode(targetCode);
                if (product == null) {
                    throw notFound();
                }
                yield snapshot(product);
            }
            case TARGET_SUBSCRIPTION_BENEFIT_POLICY -> {
                SubscriptionBenefitPolicy policy = benefitPolicyByTargetCode(targetCode);
                if (policy == null) {
                    throw notFound();
                }
                yield snapshot(policy);
            }
            case TARGET_AI_FEATURE_BENEFIT_POLICY -> {
                AiFeatureBenefitPolicy policy = billingMapper.findActiveFeatureBenefitPolicy(targetCode);
                if (policy == null) {
                    throw notFound();
                }
                yield snapshot(applyEffectiveFeaturePolicyChange(policy));
            }
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 정책 대상입니다.");
        };
    }

    public String normalizeTargetCode(String targetType, Map<String, Object> snapshot) {
        return switch (targetType) {
            case TARGET_SUBSCRIPTION_PLAN -> requiredText(snapshot, "code");
            case TARGET_CREDIT_PRODUCT -> requiredText(snapshot, "code");
            case TARGET_SUBSCRIPTION_BENEFIT_POLICY ->
                    requiredText(snapshot, "planCode") + ":" + requiredText(snapshot, "benefitCode");
            case TARGET_AI_FEATURE_BENEFIT_POLICY -> requiredText(snapshot, "featureType");
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 정책 대상입니다.");
        };
    }

    private SubscriptionPlan applyEffectivePlanChange(SubscriptionPlan plan) {
        BillingPolicyChange change = latestEffective(TARGET_SUBSCRIPTION_PLAN, plan.getCode());
        return change == null ? plan : read(change.getNextSnapshotJson(), SubscriptionPlan.class);
    }

    private CreditProduct applyEffectiveCreditProductChange(CreditProduct product) {
        BillingPolicyChange change = latestEffective(TARGET_CREDIT_PRODUCT, product.getCode());
        return change == null ? product : read(change.getNextSnapshotJson(), CreditProduct.class);
    }

    private SubscriptionBenefitPolicy applyEffectiveBenefitPolicyChange(SubscriptionBenefitPolicy policy) {
        BillingPolicyChange change = latestEffective(TARGET_SUBSCRIPTION_BENEFIT_POLICY, targetCode(policy));
        return change == null ? policy : read(change.getNextSnapshotJson(), SubscriptionBenefitPolicy.class);
    }

    private AiFeatureBenefitPolicy applyEffectiveFeaturePolicyChange(AiFeatureBenefitPolicy policy) {
        BillingPolicyChange change = latestEffective(TARGET_AI_FEATURE_BENEFIT_POLICY, policy.getFeatureType());
        return change == null ? policy : read(change.getNextSnapshotJson(), AiFeatureBenefitPolicy.class);
    }

    private BillingPolicyChange latestEffective(String targetType, String targetCode) {
        return policyChangeMapper.findLatestEffective(targetType, targetCode, LocalDateTime.now());
    }

    private List<SubscriptionBenefitPolicy> baseActiveBenefitPoliciesByPlan(String planCode) {
        return billingMapper.findActiveBenefitPoliciesByPlan(planCode).stream()
                .filter(SubscriptionBenefitPolicy::isActive)
                .sorted(Comparator.comparingInt(SubscriptionBenefitPolicy::getSortOrder))
                .toList();
    }

    private List<AiFeatureBenefitPolicy> baseActiveFeatureBenefitPolicies() {
        return billingMapper.findActiveFeatureBenefitPolicies().stream()
                .filter(AiFeatureBenefitPolicy::isActive)
                .sorted(Comparator.comparing(AiFeatureBenefitPolicy::getFeatureType))
                .toList();
    }

    private AiFeatureBenefitPolicy baseActiveFeatureBenefitPolicy(String featureType) {
        AiFeatureBenefitPolicy policy = billingMapper.findActiveFeatureBenefitPolicy(featureType);
        return policy != null && policy.isActive() ? policy : null;
    }

    private SubscriptionBenefitPolicy benefitPolicyByTargetCode(String targetCode) {
        String[] parts = splitTargetCode(targetCode);
        SubscriptionBenefitPolicy policy = billingMapper.findActiveBenefitPolicy(parts[0], parts[1]);
        return policy == null ? null : applyEffectiveBenefitPolicyChange(policy);
    }

    private List<SubscriptionBenefitPolicy> benefitPoliciesFromSnapshot(String snapshotJson, String planCode) {
        if (isBlank(snapshotJson)) {
            return List.of();
        }
        JsonNode policies = readTree(snapshotJson).path("benefitPolicies");
        if (!policies.isArray()) {
            return List.of();
        }
        List<SubscriptionBenefitPolicy> results = new ArrayList<>();
        for (JsonNode node : policies) {
            SubscriptionBenefitPolicy policy = read(node.toString(), SubscriptionBenefitPolicy.class);
            if (planCode.equals(policy.getPlanCode()) && policy.isActive()) {
                results.add(policy);
            }
        }
        results.sort(Comparator.comparingInt(SubscriptionBenefitPolicy::getSortOrder));
        return results;
    }

    private AiFeatureBenefitPolicy featurePolicyFromSnapshot(String snapshotJson, String featureType) {
        if (isBlank(snapshotJson)) {
            return null;
        }
        JsonNode policies = readTree(snapshotJson).path("featureBenefitPolicies");
        if (!policies.isArray()) {
            return null;
        }
        for (JsonNode node : policies) {
            AiFeatureBenefitPolicy policy = read(node.toString(), AiFeatureBenefitPolicy.class);
            if (featureType.equals(policy.getFeatureType()) && policy.isActive()) {
                return policy;
            }
        }
        return null;
    }

    private Map<String, Object> snapshot(SubscriptionPlan plan) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("code", plan.getCode());
        data.put("name", plan.getName());
        data.put("monthlyPrice", plan.getMonthlyPrice());
        data.put("yearlyPrice", plan.getYearlyPrice());
        data.put("description", plan.getDescription());
        data.put("active", plan.isActive());
        data.put("sortOrder", plan.getSortOrder());
        return data;
    }

    private Map<String, Object> snapshot(CreditProduct product) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("code", product.getCode());
        data.put("name", product.getName());
        data.put("price", product.getPrice());
        data.put("creditAmount", product.getCreditAmount());
        data.put("description", product.getDescription());
        data.put("badge", product.getBadge());
        data.put("enabled", product.isEnabled());
        data.put("sortOrder", product.getSortOrder());
        return data;
    }

    private Map<String, Object> snapshot(SubscriptionBenefitPolicy policy) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("planCode", policy.getPlanCode());
        data.put("benefitCode", policy.getBenefitCode());
        data.put("benefitName", policy.getBenefitName());
        data.put("benefitType", policy.getBenefitType());
        data.put("quantity", policy.getQuantity());
        data.put("resetCycle", policy.getResetCycle());
        data.put("overagePolicy", policy.getOveragePolicy());
        data.put("creditCost", policy.getCreditCost());
        data.put("active", policy.isActive());
        data.put("sortOrder", policy.getSortOrder());
        return data;
    }

    private Map<String, Object> snapshot(AiFeatureBenefitPolicy policy) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("featureType", policy.getFeatureType());
        data.put("benefitCode", policy.getBenefitCode());
        data.put("chargeUnit", policy.getChargeUnit());
        data.put("includedInTicket", policy.isIncludedInTicket());
        data.put("defaultCreditCost", policy.getDefaultCreditCost());
        data.put("active", policy.isActive());
        return data;
    }

    private String targetCode(SubscriptionBenefitPolicy policy) {
        return policy.getPlanCode() + ":" + policy.getBenefitCode();
    }

    private String[] splitTargetCode(String targetCode) {
        String[] parts = targetCode == null ? new String[0] : targetCode.split(":", 2);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "사용권 정책 대상 코드가 올바르지 않습니다.");
        }
        return parts;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "정책 스냅샷을 저장할 수 없습니다.");
        }
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JacksonException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "정책 스냅샷을 읽을 수 없습니다.");
        }
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JacksonException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "정책 스냅샷 형식이 올바르지 않습니다.");
        }
    }

    private String requiredText(Map<String, Object> data, String key) {
        Object value = data == null ? null : data.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "정책 대상 코드가 누락되었습니다.");
        }
        return value.toString().trim().toUpperCase();
    }

    private int valueOrMax(Integer value) {
        return value == null ? Integer.MAX_VALUE : value;
    }

    private String normalizePlanCode(String planCode) {
        return planCode == null || planCode.isBlank() ? "FREE" : planCode.trim().toUpperCase();
    }

    private BusinessException notFound() {
        return new BusinessException(ErrorCode.NOT_FOUND, "정책 대상을 찾을 수 없습니다.");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
