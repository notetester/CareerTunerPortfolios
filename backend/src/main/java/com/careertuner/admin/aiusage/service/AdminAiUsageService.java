package com.careertuner.admin.aiusage.service;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.admin.aiusage.dto.AdminAiUsageLogRow;
import com.careertuner.admin.aiusage.dto.AdminAiUsageSearchCriteria;
import com.careertuner.admin.aiusage.dto.AdminAiUsageSummary;
import com.careertuner.admin.aiusage.mapper.AdminAiUsageMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminAiUsageService {

    private static final List<String> FEATURE_TYPES = List.of("JOB_ANALYSIS", "COMPANY_RESEARCH", "JOB_POSTING_OCR");
    private static final List<String> STATUSES = List.of("SUCCESS", "FAILED");

    private final AdminAiUsageMapper mapper;

    @Transactional(readOnly = true)
    public List<AdminAiUsageLogRow> bUsageLogs(AuthUser authUser, AdminAiUsageSearchCriteria criteria) {
        requireAdmin(authUser);
        return mapper.findBUsageLogs(normalizeCriteria(criteria));
    }

    @Transactional(readOnly = true)
    public AdminAiUsageSummary bUsageSummary(AuthUser authUser, AdminAiUsageSearchCriteria criteria) {
        requireAdmin(authUser);
        return mapper.summarizeBUsageLogs(normalizeCriteria(criteria));
    }

    private static AdminAiUsageSearchCriteria normalizeCriteria(AdminAiUsageSearchCriteria criteria) {
        AdminAiUsageSearchCriteria base = criteria == null
                ? AdminAiUsageSearchCriteria.builder().build()
                : criteria;
        return AdminAiUsageSearchCriteria.builder()
                .featureType(normalizeAllowedToken(base.featureType(), FEATURE_TYPES, "featureType"))
                .status(normalizeAllowedToken(base.status(), STATUSES, "status"))
                .keyword(blankToNull(base.keyword()))
                .applicationCaseId(base.applicationCaseId())
                .userId(base.userId())
                .model(blankToNull(base.model()))
                .createdFrom(base.createdFrom())
                .createdTo(base.createdTo())
                .sort(normalizeSort(base.sort()))
                .limit(normalizeLimit(base.limit()))
                .offset(normalizeOffset(base.offset()))
                .build();
    }

    private static void requireAdmin(AuthUser authUser) {
        if (authUser == null || !"ADMIN".equals(authUser.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Admin role is required.");
        }
    }

    private static int normalizeLimit(int limit) {
        if (limit <= 0) {
            return 50;
        }
        return Math.min(limit, 200);
    }

    private static int normalizeOffset(int offset) {
        return Math.max(offset, 0);
    }

    private static String normalizeSort(String value) {
        String sort = blankToNull(value);
        if (sort == null) {
            return "CREATED_AT_DESC";
        }
        if (sort.startsWith("-") || sort.startsWith("+")) {
            return sortByField(sort.substring(1), sort.startsWith("-"));
        }
        return switch (compactKey(sort)) {
            case "CREATEDATDESC", "CREATEDDESC" -> "CREATED_AT_DESC";
            case "CREATEDATASC", "CREATEDASC" -> "CREATED_AT_ASC";
            case "TOKENUSAGEDESC", "TOKENDESC" -> "TOKEN_USAGE_DESC";
            case "TOKENUSAGEASC", "TOKENASC" -> "TOKEN_USAGE_ASC";
            case "CREDITUSEDDESC", "CREDITDESC" -> "CREDIT_USED_DESC";
            case "CREDITUSEDASC", "CREDITASC" -> "CREDIT_USED_ASC";
            case "MODELDESC" -> "MODEL_DESC";
            case "MODELASC" -> "MODEL_ASC";
            case "FEATURETYPEDESC", "FEATUREDESC" -> "FEATURE_TYPE_DESC";
            case "FEATURETYPEASC", "FEATUREASC" -> "FEATURE_TYPE_ASC";
            case "STATUSDESC" -> "STATUS_DESC";
            case "STATUSASC" -> "STATUS_ASC";
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT, "sort is not allowed.");
        };
    }

    private static String sortByField(String value, boolean descending) {
        return switch (compactKey(value)) {
            case "CREATEDAT", "CREATED" -> descending ? "CREATED_AT_DESC" : "CREATED_AT_ASC";
            case "TOKENUSAGE", "TOKEN" -> descending ? "TOKEN_USAGE_DESC" : "TOKEN_USAGE_ASC";
            case "CREDITUSED", "CREDIT" -> descending ? "CREDIT_USED_DESC" : "CREDIT_USED_ASC";
            case "MODEL" -> descending ? "MODEL_DESC" : "MODEL_ASC";
            case "FEATURETYPE", "FEATURE" -> descending ? "FEATURE_TYPE_DESC" : "FEATURE_TYPE_ASC";
            case "STATUS" -> descending ? "STATUS_DESC" : "STATUS_ASC";
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT, "sort is not allowed.");
        };
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String normalizedToken(String value) {
        String normalized = blankToNull(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private static String normalizeAllowedToken(String value, List<String> allowedValues, String fieldName) {
        String normalized = normalizedToken(value);
        if (normalized == null) {
            return null;
        }
        if (!allowedValues.contains(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "%s is not allowed.".formatted(fieldName));
        }
        return normalized;
    }

    private static String compactKey(String value) {
        return value.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }
}
