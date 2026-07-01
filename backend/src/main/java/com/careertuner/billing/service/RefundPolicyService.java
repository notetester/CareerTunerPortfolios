package com.careertuner.billing.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.admin.notice.dto.AdminNoticeRequest;
import com.careertuner.admin.notice.dto.AdminNoticeResponse;
import com.careertuner.admin.notice.service.AdminNoticeService;
import com.careertuner.billing.domain.RefundPolicy;
import com.careertuner.billing.dto.AdminRefundPolicySaveRequest;
import com.careertuner.billing.dto.CurrentRefundPolicyResponse;
import com.careertuner.billing.dto.RefundPolicyResponse;
import com.careertuner.billing.mapper.RefundPolicyMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class RefundPolicyService {

    public static final String POLICY_CODE = "REFUND_DEFAULT";
    public static final String TRIGGER_NOTICE = "NOTICE";
    public static final String TRIGGER_PAYMENT = "PAYMENT";
    public static final String TRIGGER_CREDIT_USE = "CREDIT_USE";
    public static final String TRIGGER_BENEFIT_USE = "BENEFIT_USE";

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String NOTICE_CATEGORY = "REFUND_POLICY";
    private static final Set<String> TRIGGERS = Set.of(
            TRIGGER_NOTICE, TRIGGER_PAYMENT, TRIGGER_CREDIT_USE, TRIGGER_BENEFIT_USE);
    private static final Set<String> UNUSED_POLICIES = Set.of("FULL_REFUND", "MANUAL_REVIEW");
    private static final Set<String> USED_POLICIES = Set.of("MANUAL_REVIEW", "PRORATED_REFUND", "NO_REFUND");
    private static final DateTimeFormatter NOTICE_DATE = DateTimeFormatter.ofPattern("yyyy년 M월 d일 HH:mm");
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final RefundPolicyMapper mapper;
    private final AdminNoticeService adminNoticeService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<RefundPolicyResponse> list(AuthUser authUser) {
        requireAdmin(authUser);
        return mapper.findRecent(POLICY_CODE, 100).stream()
                .map(RefundPolicyResponse::from)
                .toList();
    }

    @Transactional
    public RefundPolicyResponse saveDraft(AuthUser authUser, AdminRefundPolicySaveRequest request) {
        requireAdmin(authUser);
        validateRequest(request);

        RefundPolicy draft = mapper.findDraft(POLICY_CODE);
        if (draft == null) {
            draft = new RefundPolicy();
            draft.setPolicyCode(POLICY_CODE);
            draft.setVersion(nextVersion());
            draft.setStatus(STATUS_DRAFT);
            draft.setCreatedBy(authUser.id());
            applyRequest(draft, request);
            try {
                mapper.insert(draft);
            } catch (DuplicateKeyException ex) {
                throw new BusinessException(ErrorCode.CONFLICT, "이미 작성 중인 환불 정책 초안이 있습니다.");
            }
        } else {
            applyRequest(draft, request);
            if (mapper.updateDraft(draft) == 0) {
                throw new BusinessException(ErrorCode.CONFLICT, "게시된 환불 정책은 수정할 수 없습니다.");
            }
        }
        return RefundPolicyResponse.from(requirePolicy(draft.getId()));
    }

    @Transactional
    public RefundPolicyResponse publish(AuthUser authUser, Long id) {
        requireAdmin(authUser);
        RefundPolicy draft = requirePolicy(id);
        if (!POLICY_CODE.equals(draft.getPolicyCode()) || !STATUS_DRAFT.equals(draft.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "환불 정책 초안만 게시할 수 있습니다.");
        }
        validateStoredPolicy(draft);

        unpinPreviousRefundNotices(authUser);
        AdminNoticeResponse notice = adminNoticeService.createNotice(authUser, new AdminNoticeRequest(
                "[환불정책 변경] " + draft.getTitle(),
                noticeContent(draft),
                STATUS_PUBLISHED,
                true,
                NOTICE_CATEGORY,
                null));
        if (notice == null || notice.getId() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "환불 정책 공지 생성에 실패했습니다.");
        }

        int updated = mapper.publishDraft(id, notice.getId(), LocalDateTime.now());
        if (updated == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "환불 정책 상태가 변경되어 게시할 수 없습니다.");
        }
        return RefundPolicyResponse.from(requirePolicy(id));
    }

    @Transactional(readOnly = true)
    public CurrentRefundPolicyResponse currentForUser(Long userId) {
        requireUser(userId);
        RefundPolicy policy = requireCurrent();
        Set<String> acknowledgements = Set.copyOf(mapper.findAcknowledgedTriggers(userId, policy.getId()));
        return new CurrentRefundPolicyResponse(
                policy.getId(),
                policy.getPolicyCode(),
                policy.getVersion(),
                policy.getTitle(),
                policy.getSummary(),
                policy.getContent(),
                policy.getRulesJson(),
                policy.getEffectiveAt(),
                policy.getNoticeId(),
                acknowledgements);
    }

    @Transactional(readOnly = true)
    public RefundPolicy currentPolicy() {
        return requireCurrent();
    }

    @Transactional
    public CurrentRefundPolicyResponse acknowledge(
            Long userId, Long policyId, String triggerType, String actionKey) {
        requireUser(userId);
        String trigger = normalizeTrigger(triggerType);
        String normalizedActionKey = normalizeActionKey(actionKey);
        RefundPolicy current = requireCurrent();
        if (!current.getId().equals(policyId)) {
            throw new BusinessException(ErrorCode.CONFLICT, "환불 정책이 변경되었습니다. 최신 정책을 다시 확인해 주세요.");
        }
        mapper.insertAcknowledgement(userId, policyId, trigger, normalizedActionKey);
        return currentForUser(userId);
    }

    @Transactional(readOnly = true)
    public RefundPolicy requirePaymentAcknowledgement(Long userId, Long policyId, String actionKey) {
        requireUser(userId);
        String normalizedActionKey = normalizeActionKey(actionKey);
        if (policyId == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "결제 전에 환불 정책을 확인해 주세요.");
        }
        RefundPolicy current = requireCurrent();
        if (!current.getId().equals(policyId)) {
            throw new BusinessException(ErrorCode.CONFLICT, "환불 정책이 변경되었습니다. 최신 정책을 다시 확인해 주세요.");
        }
        if (!mapper.existsAcknowledgement(
                userId, current.getId(), TRIGGER_PAYMENT, normalizedActionKey)) {
            throw new BusinessException(ErrorCode.CONFLICT, "결제 전에 환불 정책 동의가 필요합니다.");
        }
        return current;
    }

    @Transactional(readOnly = true)
    public void requireUsageAcknowledgement(Long userId, String triggerType, String actionKey) {
        requireUser(userId);
        String trigger = normalizeTrigger(triggerType);
        String normalizedActionKey = normalizeActionKey(actionKey);
        if (!TRIGGER_CREDIT_USE.equals(trigger) && !TRIGGER_BENEFIT_USE.equals(trigger)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "사용 고지 유형이 올바르지 않습니다.");
        }
        RefundPolicy current = requireCurrent();
        if (!mapper.existsAcknowledgement(userId, current.getId(), trigger, normalizedActionKey)) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "변경된 환불 정책을 확인한 뒤 크레딧 또는 사용권을 이용해 주세요.");
        }
    }

    public String appendPaymentSnapshot(
            String billingSnapshotJson, RefundPolicy policy, String policyAcknowledgementKey) {
        if (policy == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "결제에 적용할 환불 정책이 없습니다.");
        }
        try {
            LinkedHashMap<String, Object> snapshot = billingSnapshotJson == null || billingSnapshotJson.isBlank()
                    ? new LinkedHashMap<>()
                    : objectMapper.readValue(billingSnapshotJson, MAP_TYPE);
            LinkedHashMap<String, Object> refund = new LinkedHashMap<>();
            refund.put("id", policy.getId());
            refund.put("policyCode", policy.getPolicyCode());
            refund.put("version", policy.getVersion());
            refund.put("title", policy.getTitle());
            refund.put("summary", policy.getSummary());
            refund.put("content", policy.getContent());
            refund.put("rules", objectMapper.readValue(policy.getRulesJson(), MAP_TYPE));
            refund.put("effectiveAt", policy.getEffectiveAt());
            refund.put("noticeId", policy.getNoticeId());
            refund.put("acknowledgementKey", normalizeActionKey(policyAcknowledgementKey));
            snapshot.put("refundPolicy", refund);
            return objectMapper.writeValueAsString(snapshot);
        } catch (JacksonException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "결제 환불 정책 스냅샷 생성에 실패했습니다.");
        }
    }

    private void applyRequest(RefundPolicy policy, AdminRefundPolicySaveRequest request) {
        policy.setTitle(request.title().trim());
        policy.setSummary(trimToNull(request.summary()));
        policy.setContent(request.content().trim());
        policy.setRulesJson(writeJson(request.rules()));
        policy.setAdverse(Boolean.TRUE.equals(request.adverse()));
        policy.setEffectiveAt(request.effectiveAt());
    }

    private void validateRequest(AdminRefundPolicySaveRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "환불 정책 내용이 필요합니다.");
        }
        if (isBlank(request.title()) || request.title().trim().length() > 255) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "환불 정책 제목은 1자 이상 255자 이하여야 합니다.");
        }
        if (isBlank(request.content())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "사용자에게 고지할 환불 정책 본문이 필요합니다.");
        }
        if (request.summary() != null && request.summary().trim().length() > 500) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "환불 정책 요약은 500자 이하여야 합니다.");
        }
        if (request.effectiveAt() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "환불 정책 시행 시각이 필요합니다.");
        }
        validateRules(request.rules());
    }

    private void validateStoredPolicy(RefundPolicy policy) {
        if (policy.getEffectiveAt() == null || isBlank(policy.getTitle()) || isBlank(policy.getContent())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "제목, 본문, 시행 시각을 모두 입력해야 게시할 수 있습니다.");
        }
        try {
            validateRules(objectMapper.readValue(policy.getRulesJson(), MAP_TYPE));
        } catch (JacksonException ex) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "환불 정책 규칙 JSON이 올바르지 않습니다.");
        }
    }

    private void validateRules(Map<String, Object> rules) {
        if (rules == null || rules.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "환불 판정 규칙이 필요합니다.");
        }
        if (isBlank(String.valueOf(rules.getOrDefault("legalBasis", "")))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "환불 정책의 법적 근거를 입력해 주세요.");
        }
        Object daysValue = rules.get("withdrawalDays");
        if (!(daysValue instanceof Number number) || number.intValue() < 7 || number.intValue() > 365) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "미사용 청약철회 기간은 7일 이상 365일 이하여야 합니다.");
        }
        String unusedPolicy = normalizeValue(rules.get("unusedPolicy"));
        String usedPolicy = normalizeValue(rules.get("usedPolicy"));
        if (!UNUSED_POLICIES.contains(unusedPolicy)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "미사용 환불 기준이 올바르지 않습니다.");
        }
        if (!USED_POLICIES.contains(usedPolicy)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "사용 후 환불 기준이 올바르지 않습니다.");
        }
        Object scopesValue = rules.get("noticeScopes");
        if (!(scopesValue instanceof Collection<?> scopes)
                || !scopes.containsAll(List.of(TRIGGER_PAYMENT, TRIGGER_CREDIT_USE, TRIGGER_BENEFIT_USE))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "결제·크레딧·사용권 고지 범위가 모두 필요합니다.");
        }
    }

    private void unpinPreviousRefundNotices(AuthUser authUser) {
        for (AdminNoticeResponse notice : adminNoticeService.getNotices(authUser)) {
            if (!notice.isPinned() || !NOTICE_CATEGORY.equalsIgnoreCase(notice.getCategory())) {
                continue;
            }
            adminNoticeService.updateNotice(authUser, notice.getId(), new AdminNoticeRequest(
                    notice.getTitle(),
                    notice.getContent(),
                    notice.getStatus(),
                    false,
                    notice.getCategory(),
                    notice.getThumbnailUrl()));
        }
    }

    private String noticeContent(RefundPolicy policy) {
        StringBuilder content = new StringBuilder();
        content.append("시행일: ").append(policy.getEffectiveAt().format(NOTICE_DATE)).append('\n');
        content.append("정책 버전: v").append(policy.getVersion()).append("\n\n");
        if (!isBlank(policy.getSummary())) {
            content.append(policy.getSummary().trim()).append("\n\n");
        }
        content.append(policy.getContent().trim());
        return content.toString();
    }

    private RefundPolicy requireCurrent() {
        RefundPolicy policy = mapper.findCurrent(POLICY_CODE, LocalDateTime.now());
        if (policy == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "현재 시행 중인 환불 정책이 없습니다.");
        }
        return policy;
    }

    private RefundPolicy requirePolicy(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "환불 정책 ID가 필요합니다.");
        }
        RefundPolicy policy = mapper.findById(id);
        if (policy == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "환불 정책을 찾을 수 없습니다.");
        }
        return policy;
    }

    private int nextVersion() {
        Integer max = mapper.findMaxVersion(POLICY_CODE);
        return (max == null ? 0 : max) + 1;
    }

    private String normalizeTrigger(String value) {
        String normalized = normalizeValue(value);
        if (!TRIGGERS.contains(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 환불 정책 고지 유형입니다.");
        }
        return normalized;
    }

    private String normalizeActionKey(String value) {
        String key = isBlank(value) ? "GLOBAL" : value.trim();
        if (key.length() > 120 || !key.matches("[A-Za-z0-9:_-]+")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "정책 고지 확인키가 올바르지 않습니다.");
        }
        return key;
    }

    private String normalizeValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim().toUpperCase(Locale.ROOT);
    }

    private String writeJson(Map<String, Object> rules) {
        try {
            return objectMapper.writeValueAsString(rules);
        } catch (JacksonException ex) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "환불 정책 규칙 JSON이 올바르지 않습니다.");
        }
    }

    private void requireAdmin(AuthUser authUser) {
        com.careertuner.admin.common.AdminAccess.requireAdmin(authUser);
    }

    private void requireUser(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "로그인 후 환불 정책을 확인할 수 있습니다.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }
}
