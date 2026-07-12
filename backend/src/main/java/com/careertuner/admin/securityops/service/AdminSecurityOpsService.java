package com.careertuner.admin.securityops.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.admin.common.AdminAccess;
import com.careertuner.admin.ops.service.AdminActionLogService;
import com.careertuner.admin.securityops.dto.SecurityAppealDecisionRequest;
import com.careertuner.admin.securityops.dto.SecurityAppealPolicyRequest;
import com.careertuner.admin.securityops.dto.SecurityAppealPolicyRow;
import com.careertuner.admin.securityops.dto.SecurityAppealRow;
import com.careertuner.admin.securityops.dto.SecurityBlockRuleRequest;
import com.careertuner.admin.securityops.dto.SecurityBlockRuleRow;
import com.careertuner.admin.securityops.dto.SecurityOpsSummaryResponse;
import com.careertuner.admin.securityops.dto.SecurityProviderConfigRequest;
import com.careertuner.admin.securityops.dto.SecurityProviderConfigRow;
import com.careertuner.admin.securityops.dto.SecurityProviderHealthHistoryRow;
import com.careertuner.admin.securityops.dto.SecurityReviewRequest;
import com.careertuner.admin.securityops.dto.SecurityReviewRow;
import com.careertuner.admin.securityops.dto.WafSyncEventRow;
import com.careertuner.admin.securityops.engine.BlockRuleCacheService;
import com.careertuner.admin.securityops.mapper.AdminSecurityOpsMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminSecurityOpsService {

    private static final Set<String> RULE_TYPES = Set.of("USER", "EMAIL", "EMAIL_DOMAIN", "IP", "CIDR", "IP_RANGE", "COUNTRY", "ASN");
    private static final Set<String> SCOPES = Set.of("GLOBAL", "LOGIN", "COMMUNITY", "AI", "SUPPORT");
    private static final Set<String> ACTION_TYPES = Set.of("BLOCK", "REVIEW", "CHALLENGE", "ALLOWLIST");
    private static final Set<String> CATEGORIES = Set.of("SPAM", "ABUSE", "BRUTE_FORCE", "GEO", "VPN", "SECURITY", "MANUAL");
    private static final Set<String> PROVIDER_TYPES = Set.of("WAF", "RISK", "EMAIL", "CAPTCHA");
    private static final Set<String> REVIEW_TYPES = Set.of("LOGIN_RISK", "EXTERNAL_RISK", "SECURITY_RISK", "GENERAL");
    private static final Set<String> SUBJECT_TYPES = Set.of("USER", "IP", "EMAIL", "POST", "COMMENT", "SESSION");
    private static final Set<String> RISK_LEVELS = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    private static final Set<String> REVIEW_STATUSES = Set.of("OPEN", "APPROVED", "REJECTED", "ESCALATED", "DISMISSED");
    private static final Set<String> APPEAL_STATUSES = Set.of("OPEN", "APPROVED", "REJECTED", "NEEDS_INFO", "CLOSED");

    private final AdminSecurityOpsMapper mapper;
    private final AdminActionLogService actionLogService;
    private final BlockRuleCacheService blockRuleCacheService;
    private final com.careertuner.admin.securityops.waf.WafSyncScheduler wafSyncScheduler;

    /** WAF 동기화 큐를 즉시 1배치 처리(수동 드레인). @return 처리 건수 */
    @Transactional
    public int processWafSyncNow(AuthUser authUser) {
        AdminAccess.requireAdmin(authUser);
        int processed = wafSyncScheduler.drainOnce();
        actionLogService.record(authUser, null, "SECURITY_WAF_SYNC_PROCESSED", "SECURITY_BLOCK_RULE",
                null, jsonObject("processed", String.valueOf(processed)), "WAF 큐 수동 처리");
        return processed;
    }

    /** 규칙 변경 후 런타임 차단 캐시를 즉시 재적재한다(트랜잭션 내 read-your-writes → 커밋될 상태 반영). best-effort. */
    private void invalidateBlockCache() {
        try {
            blockRuleCacheService.invalidateAndRefresh();
        } catch (Exception e) {
            log.warn("[BlockCache] 규칙 변경 후 캐시 갱신 실패(다음 변경/수동 동기화 시 복구): {}", e.getMessage());
        }
    }

    /** 관리자가 수동으로 차단 캐시를 DB 와 재동기화. */
    @Transactional
    public BlockCacheStatus syncBlockCache(AuthUser authUser) {
        AdminAccess.requireAdmin(authUser);
        BlockRuleCacheService.BlockRuleCacheSnapshot snapshot = blockRuleCacheService.invalidateAndRefresh();
        actionLogService.record(authUser, null, "SECURITY_BLOCK_CACHE_SYNCED", "SECURITY_BLOCK_RULE",
                null, jsonObject("ruleCount", String.valueOf(snapshot.size())), "수동 캐시 동기화");
        return new BlockCacheStatus(snapshot.getSource(), snapshot.size(), snapshot.getLoadedAt());
    }

    /** 현재 차단 캐시 상태 조회. */
    @Transactional(readOnly = true)
    public BlockCacheStatus blockCacheStatus(AuthUser authUser) {
        AdminAccess.requireAdmin(authUser);
        BlockRuleCacheService.BlockRuleCacheSnapshot snapshot = blockRuleCacheService.getSnapshot();
        return new BlockCacheStatus(snapshot.getSource(), snapshot.size(), snapshot.getLoadedAt());
    }

    /** 차단 캐시 상태 응답. */
    public record BlockCacheStatus(String source, int ruleCount, java.time.LocalDateTime loadedAt) {
    }

    @Transactional(readOnly = true)
    public SecurityOpsSummaryResponse summary(AuthUser authUser) {
        AdminAccess.requireAdmin(authUser);
        return mapper.summary();
    }

    @Transactional(readOnly = true)
    public List<SecurityBlockRuleRow> blockRules(AuthUser authUser, String keyword, String ruleType, Boolean active, int limit) {
        AdminAccess.requireAdmin(authUser);
        return mapper.findBlockRules(blankToNull(keyword), normalizeOptional(ruleType, RULE_TYPES), active, normalizeLimit(limit));
    }

    @Transactional
    public SecurityBlockRuleRow createBlockRule(AuthUser authUser, SecurityBlockRuleRequest request) {
        AdminAccess.requireAdmin(authUser);
        NormalizedBlockRule normalized = normalizeBlockRule(request, null);
        mapper.insertBlockRule(normalized.ruleType(), normalized.ruleValue(), normalized.scope(), normalized.actionType(),
                normalized.category(), normalized.reason(), normalized.memo(), normalized.active(), normalized.wafSyncEnabled(),
                normalized.wafSyncStatus(), normalized.expiresAt(), authUser.id());
        SecurityBlockRuleRow created = mapper.findLatestBlockRule(normalized.ruleType(), normalized.ruleValue());
        actionLogService.record(authUser, null, "SECURITY_BLOCK_RULE_CREATED", "SECURITY_BLOCK_RULE",
                null, blockRuleSnapshot(created), normalized.reason());
        invalidateBlockCache();
        if (created != null && created.wafSyncEnabled()) {
            queueWafSync(authUser, created.id(), "UPSERT");
            return mapper.findBlockRuleById(created.id());
        }
        return created;
    }

    @Transactional
    public SecurityBlockRuleRow updateBlockRule(AuthUser authUser, Long id, SecurityBlockRuleRequest request) {
        AdminAccess.requireAdmin(authUser);
        SecurityBlockRuleRow before = requireBlockRule(id);
        NormalizedBlockRule normalized = normalizeBlockRule(request, before);
        int updated = mapper.updateBlockRule(id, normalized.ruleType(), normalized.ruleValue(), normalized.scope(),
                normalized.actionType(), normalized.category(), normalized.reason(), normalized.memo(), normalized.active(),
                normalized.wafSyncEnabled(), normalized.wafSyncStatus(), normalized.expiresAt(), authUser.id());
        if (updated == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "차단 규칙을 찾을 수 없습니다.");
        }
        SecurityBlockRuleRow after = requireBlockRule(id);
        actionLogService.record(authUser, null, "SECURITY_BLOCK_RULE_UPDATED", "SECURITY_BLOCK_RULE",
                blockRuleSnapshot(before), blockRuleSnapshot(after), normalized.reason());
        invalidateBlockCache();
        return after;
    }

    @Transactional
    public SecurityBlockRuleRow queueWafSync(AuthUser authUser, Long id, String operationType) {
        AdminAccess.requireAdmin(authUser);
        SecurityBlockRuleRow rule = requireBlockRule(id);
        String operation = normalizeEnum(operationType, Set.of("UPSERT", "DELETE"), "UPSERT");
        String payload = jsonObject(
                "ruleId", String.valueOf(rule.id()),
                "ruleType", rule.ruleType(),
                "ruleValue", rule.ruleValue(),
                "operation", operation
        );
        mapper.insertWafEvent(id, "MOCK_WAF", operation, "QUEUED", payload, null, null, authUser.id(), null);
        mapper.updateBlockRuleWafStatus(id, "PENDING", null, null, authUser.id());
        actionLogService.record(authUser, null, "SECURITY_WAF_SYNC_QUEUED", "SECURITY_BLOCK_RULE",
                null, payload, rule.reason());
        return requireBlockRule(id);
    }

    @Transactional(readOnly = true)
    public List<WafSyncEventRow> wafEvents(AuthUser authUser, String keyword, String status, int limit) {
        AdminAccess.requireAdmin(authUser);
        return mapper.findWafEvents(blankToNull(keyword), normalizeOptional(status, Set.of("QUEUED", "SYNCED", "FAILED", "SKIPPED")), normalizeLimit(limit));
    }

    @Transactional(readOnly = true)
    public List<SecurityProviderConfigRow> providers(AuthUser authUser, String keyword, String providerType) {
        AdminAccess.requireSuperAdmin(authUser);
        return mapper.findProviders(blankToNull(keyword), normalizeOptional(providerType, PROVIDER_TYPES));
    }

    @Transactional(readOnly = true)
    public List<SecurityProviderHealthHistoryRow> providerHealthHistory(AuthUser authUser, String keyword, String statusAfter, int limit) {
        AdminAccess.requireAdmin(authUser);
        return mapper.findProviderHealthHistory(blankToNull(keyword),
                normalizeOptional(statusAfter, Set.of("OK", "DISABLED", "FAILED", "UNKNOWN", "SKIPPED")),
                normalizeLimit(limit));
    }

    @Transactional
    public SecurityProviderConfigRow updateProvider(AuthUser authUser, String providerCode, SecurityProviderConfigRequest request) {
        AdminAccess.requireSuperAdmin(authUser);
        String code = normalizeCode(providerCode);
        SecurityProviderConfigRow before = requireProvider(code);
        String providerType = normalizeEnum(request.providerType(), PROVIDER_TYPES, before.providerType());
        String mode = normalizeCode(defaultString(request.mode(), before.mode()));
        String configJson = normalizeJson(request.configJson(), before.configJson());
        int updated = mapper.updateProvider(code, defaultString(request.displayName(), before.displayName()), providerType,
                mode, request.enabled() == null ? before.enabled() : request.enabled(),
                blankToNull(request.endpointUrl()), configJson, authUser.id());
        if (updated == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Provider 설정을 찾을 수 없습니다.");
        }
        SecurityProviderConfigRow after = requireProvider(code);
        actionLogService.record(authUser, null, "SECURITY_PROVIDER_UPDATED", "SECURITY_PROVIDER",
                providerSnapshot(before), providerSnapshot(after), null);
        return after;
    }

    @Transactional
    public SecurityProviderConfigRow runProviderHealthCheck(AuthUser authUser, String providerCode) {
        AdminAccess.requireSuperAdmin(authUser);
        String code = normalizeCode(providerCode);
        SecurityProviderConfigRow before = requireProvider(code);
        String status = before.enabled() ? "OK" : "DISABLED";
        mapper.updateProviderHealth(code, status, authUser.id());
        mapper.insertProviderHealthHistory(before.id(), before.providerCode(), before.providerType(), "MANUAL",
                before.healthStatus(), status,
                status.equals("OK") ? "수동 헬스체크 성공" : "Provider가 비활성 상태라 점검을 건너뜀",
                authUser.id());
        mapper.insertWafEvent(null, code, "HEALTH_CHECK", status.equals("OK") ? "SYNCED" : "SKIPPED",
                jsonObject("providerCode", code, "mode", before.mode()), jsonObject("healthStatus", status),
                null, authUser.id(), LocalDateTime.now());
        SecurityProviderConfigRow after = requireProvider(code);
        actionLogService.record(authUser, null, "SECURITY_PROVIDER_HEALTH_CHECKED", "SECURITY_PROVIDER",
                providerSnapshot(before), providerSnapshot(after), null);
        return after;
    }

    @Transactional(readOnly = true)
    public List<SecurityReviewRow> reviews(AuthUser authUser, String keyword, String status, String reviewType, int limit) {
        AdminAccess.requireAdmin(authUser);
        return mapper.findReviews(blankToNull(keyword), normalizeOptional(status, REVIEW_STATUSES),
                normalizeOptional(reviewType, REVIEW_TYPES), normalizeLimit(limit));
    }

    @Transactional
    public SecurityReviewRow createReview(AuthUser authUser, SecurityReviewRequest request) {
        AdminAccess.requireAdmin(authUser);
        NormalizedReview normalized = normalizeReview(request, null);
        mapper.insertReview(normalized.reviewType(), normalized.subjectType(), normalized.subjectValue(), normalized.riskScore(),
                normalized.riskLevel(), normalized.status(), normalized.decisionAction(), normalized.reason(),
                normalized.evidenceJson(), normalized.assignedTo(), authUser.id());
        actionLogService.record(authUser, null, "SECURITY_REVIEW_CREATED", "SECURITY_REVIEW",
                null, reviewSnapshot(normalized), normalized.reason());
        return mapper.findReviews(normalized.subjectValue(), normalized.status(), normalized.reviewType(), 1).stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR, "검토 큐 생성 결과를 찾을 수 없습니다."));
    }

    @Transactional
    public SecurityReviewRow updateReview(AuthUser authUser, Long id, SecurityReviewRequest request) {
        AdminAccess.requireAdmin(authUser);
        SecurityReviewRow before = requireReview(id);
        String status = normalizeEnum(request.status(), REVIEW_STATUSES, before.status());
        String decision = blankToNull(request.decisionAction());
        LocalDateTime decidedAt = status.equals("OPEN") ? null : LocalDateTime.now();
        int updated = mapper.updateReview(id, status, decision, blankToNull(request.reason()),
                request.assignedTo() == null ? before.assignedTo() : request.assignedTo(), authUser.id(), decidedAt);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "검토 항목을 찾을 수 없습니다.");
        }
        SecurityReviewRow after = requireReview(id);
        actionLogService.record(authUser, null, "SECURITY_REVIEW_UPDATED", "SECURITY_REVIEW",
                reviewSnapshot(before), reviewSnapshot(after), request.reason());
        return after;
    }

    @Transactional(readOnly = true)
    public SecurityAppealPolicyRow appealPolicy(AuthUser authUser) {
        AdminAccess.requireAdmin(authUser);
        SecurityAppealPolicyRow policy = mapper.findAppealPolicy();
        if (policy == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "이의제기 정책을 찾을 수 없습니다.");
        }
        return policy;
    }

    @Transactional
    public SecurityAppealPolicyRow updateAppealPolicy(AuthUser authUser, SecurityAppealPolicyRequest request) {
        AdminAccess.requireAdmin(authUser);
        SecurityAppealPolicyRow before = appealPolicy(authUser);
        mapper.updateAppealPolicy(defaultString(request.displayName(), before.displayName()),
                request.enabled() == null ? before.enabled() : request.enabled(),
                request.allowMultipleOpen() == null ? before.allowMultipleOpen() : request.allowMultipleOpen(),
                positiveOrDefault(request.maxOpenPerSubject(), before.maxOpenPerSubject()),
                positiveOrDefault(request.submitterDailyLimit(), before.submitterDailyLimit()),
                positiveOrDefault(request.tokenTtlHours(), before.tokenTtlHours()),
                normalizeJson(request.configJson(), before.configJson()), authUser.id());
        SecurityAppealPolicyRow after = appealPolicy(authUser);
        actionLogService.record(authUser, null, "SECURITY_APPEAL_POLICY_UPDATED", "SECURITY_APPEAL_POLICY",
                appealPolicySnapshot(before), appealPolicySnapshot(after), request.reason());
        return after;
    }

    @Transactional(readOnly = true)
    public List<SecurityAppealRow> appeals(AuthUser authUser, String keyword, String status, int limit) {
        AdminAccess.requireAdmin(authUser);
        return mapper.findAppeals(blankToNull(keyword), normalizeOptional(status, APPEAL_STATUSES), normalizeLimit(limit));
    }

    @Transactional
    public SecurityAppealRow decideAppeal(AuthUser authUser, Long id, SecurityAppealDecisionRequest request) {
        AdminAccess.requireAdmin(authUser);
        SecurityAppealRow before = requireAppeal(id);
        String status = normalizeEnum(request.status(), APPEAL_STATUSES, before.status());
        int updated = mapper.updateAppeal(id, status, blankToNull(request.decisionReason()), authUser.id(), LocalDateTime.now());
        if (updated == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "이의제기 항목을 찾을 수 없습니다.");
        }
        SecurityAppealRow after = requireAppeal(id);
        actionLogService.record(authUser, null, "SECURITY_APPEAL_DECIDED", "SECURITY_APPEAL",
                appealSnapshot(before), appealSnapshot(after), request.decisionReason());
        return after;
    }

    private SecurityBlockRuleRow requireBlockRule(Long id) {
        SecurityBlockRuleRow row = mapper.findBlockRuleById(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "차단 규칙을 찾을 수 없습니다.");
        }
        return row;
    }

    private SecurityProviderConfigRow requireProvider(String providerCode) {
        SecurityProviderConfigRow row = mapper.findProviderByCode(providerCode);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Provider 설정을 찾을 수 없습니다.");
        }
        return row;
    }

    private SecurityReviewRow requireReview(Long id) {
        SecurityReviewRow row = mapper.findReviewById(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "검토 항목을 찾을 수 없습니다.");
        }
        return row;
    }

    private SecurityAppealRow requireAppeal(Long id) {
        SecurityAppealRow row = mapper.findAppealById(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "이의제기 항목을 찾을 수 없습니다.");
        }
        return row;
    }

    private static NormalizedBlockRule normalizeBlockRule(SecurityBlockRuleRequest request, SecurityBlockRuleRow before) {
        String ruleType = normalizeEnum(request.ruleType(), RULE_TYPES, before == null ? null : before.ruleType());
        String ruleValue = normalizeRuleValue(ruleType, defaultString(request.ruleValue(), before == null ? null : before.ruleValue()));
        boolean active = request.active() == null ? before == null || before.active() : request.active();
        boolean wafSyncEnabled = request.wafSyncEnabled() == null ? before != null && before.wafSyncEnabled() : request.wafSyncEnabled();
        return new NormalizedBlockRule(
                ruleType,
                ruleValue,
                normalizeEnum(request.scope(), SCOPES, before == null ? "GLOBAL" : before.scope()),
                normalizeEnum(request.actionType(), ACTION_TYPES, before == null ? "BLOCK" : before.actionType()),
                normalizeEnum(request.category(), CATEGORIES, before == null ? "MANUAL" : before.category()),
                blankToNull(request.reason()),
                blankToNull(request.memo()),
                active,
                wafSyncEnabled,
                request.expiresAt() == null && before != null ? before.expiresAt() : request.expiresAt()
        );
    }

    private static NormalizedReview normalizeReview(SecurityReviewRequest request, SecurityReviewRow before) {
        int score = request.riskScore() == null ? before == null ? 0 : before.riskScore() : request.riskScore();
        return new NormalizedReview(
                normalizeEnum(request.reviewType(), REVIEW_TYPES, before == null ? "GENERAL" : before.reviewType()),
                normalizeEnum(request.subjectType(), SUBJECT_TYPES, before == null ? null : before.subjectType()),
                requireText(defaultString(request.subjectValue(), before == null ? null : before.subjectValue()), "검토 대상 값이 필요합니다."),
                score,
                normalizeEnum(request.riskLevel(), RISK_LEVELS, riskLevelFromScore(score)),
                normalizeEnum(request.status(), REVIEW_STATUSES, before == null ? "OPEN" : before.status()),
                blankToNull(request.decisionAction()),
                blankToNull(request.reason()),
                normalizeJson(request.evidenceJson(), before == null ? "{}" : before.evidenceJson()),
                request.assignedTo()
        );
    }

    private static String normalizeRuleValue(String ruleType, String value) {
        String text = requireText(value, "차단 대상 값이 필요합니다.");
        if ("COUNTRY".equals(ruleType)) {
            if (text.length() != 2) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "국가 차단은 ISO 3166-1 alpha-2 코드 2자리를 사용합니다.");
            }
            return text.toUpperCase(Locale.ROOT);
        }
        if ("EMAIL_DOMAIN".equals(ruleType)) {
            return text.toLowerCase(Locale.ROOT).replaceFirst("^@", "");
        }
        if ("EMAIL".equals(ruleType)) {
            return text.toLowerCase(Locale.ROOT);
        }
        return text;
    }

    private static String normalizeEnum(String value, Set<String> allowed, String defaultValue) {
        String target = value == null || value.isBlank() ? defaultValue : value;
        if (target == null || target.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "필수 값이 누락되었습니다.");
        }
        String normalized = normalizeCode(target);
        if (!allowed.contains(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "허용되지 않은 값입니다: " + normalized);
        }
        return normalized;
    }

    private static String normalizeOptional(String value, Set<String> allowed) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return normalizeEnum(value, allowed, null);
    }

    private static String normalizeCode(String value) {
        return requireText(value, "코드가 필요합니다.").trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private static String normalizeJson(String value, String defaultValue) {
        String target = value == null || value.isBlank() ? defaultValue : value.trim();
        if (target == null || target.isBlank()) {
            return "{}";
        }
        if (!target.startsWith("{") && !target.startsWith("[")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "JSON은 객체 또는 배열 형식이어야 합니다.");
        }
        return target;
    }

    private static String riskLevelFromScore(int score) {
        if (score >= 90) return "CRITICAL";
        if (score >= 70) return "HIGH";
        if (score >= 40) return "MEDIUM";
        return "LOW";
    }

    private static int normalizeLimit(int limit) {
        if (limit <= 0) {
            return 100;
        }
        return Math.min(limit, 500);
    }

    private static int positiveOrDefault(Integer value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "정책 수치는 1 이상이어야 합니다.");
        }
        return value;
    }

    private static String defaultString(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, message);
        }
        return value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String blockRuleSnapshot(SecurityBlockRuleRow row) {
        if (row == null) return "null";
        return jsonObject("id", String.valueOf(row.id()), "ruleType", row.ruleType(), "ruleValue", row.ruleValue(),
                "scope", row.scope(), "actionType", row.actionType(), "active", String.valueOf(row.active()));
    }

    private static String providerSnapshot(SecurityProviderConfigRow row) {
        if (row == null) return "null";
        return jsonObject("providerCode", row.providerCode(), "providerType", row.providerType(),
                "mode", row.mode(), "enabled", String.valueOf(row.enabled()), "healthStatus", row.healthStatus());
    }

    private static String reviewSnapshot(SecurityReviewRow row) {
        if (row == null) return "null";
        return jsonObject("id", String.valueOf(row.id()), "reviewType", row.reviewType(), "subjectValue", row.subjectValue(),
                "riskLevel", row.riskLevel(), "status", row.status(), "decisionAction", row.decisionAction());
    }

    private static String reviewSnapshot(NormalizedReview row) {
        return jsonObject("reviewType", row.reviewType(), "subjectType", row.subjectType(), "subjectValue", row.subjectValue(),
                "riskLevel", row.riskLevel(), "status", row.status());
    }

    private static String appealPolicySnapshot(SecurityAppealPolicyRow row) {
        if (row == null) return "null";
        return jsonObject("policyCode", row.policyCode(), "enabled", String.valueOf(row.enabled()),
                "maxOpenPerSubject", String.valueOf(row.maxOpenPerSubject()), "tokenTtlHours", String.valueOf(row.tokenTtlHours()));
    }

    private static String appealSnapshot(SecurityAppealRow row) {
        if (row == null) return "null";
        return jsonObject("id", String.valueOf(row.id()), "publicRequestId", row.publicRequestId(),
                "status", row.status(), "subjectValue", row.subjectValue());
    }

    private static String jsonObject(String... pairs) {
        StringBuilder builder = new StringBuilder("{");
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(jsonQuote(pairs[i])).append(':').append(jsonQuote(pairs[i + 1]));
        }
        return builder.append('}').toString();
    }

    private static String jsonQuote(String value) {
        if (value == null) {
            return "null";
        }
        String escaped = value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
        return "\"" + escaped + "\"";
    }

    private record NormalizedBlockRule(
            String ruleType,
            String ruleValue,
            String scope,
            String actionType,
            String category,
            String reason,
            String memo,
            boolean active,
            boolean wafSyncEnabled,
            LocalDateTime expiresAt) {

        String wafSyncStatus() {
            return wafSyncEnabled ? "PENDING" : "SKIPPED";
        }
    }

    private record NormalizedReview(
            String reviewType,
            String subjectType,
            String subjectValue,
            int riskScore,
            String riskLevel,
            String status,
            String decisionAction,
            String reason,
            String evidenceJson,
            Long assignedTo) {
    }
}
