package com.careertuner.admin.companyanalysis.service;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.admin.companyanalysis.dto.AdminCompanyAnalysisMetadataRequest;
import com.careertuner.admin.companyanalysis.dto.AdminCompanyAnalysisRow;
import com.careertuner.admin.companyanalysis.dto.AdminCompanyAnalysisSearchCriteria;
import com.careertuner.admin.companyanalysis.dto.AdminCompanyAnalysisSummary;
import com.careertuner.admin.companyanalysis.mapper.AdminCompanyAnalysisMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;
import com.careertuner.companyanalysis.mapper.CompanyAnalysisMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminCompanyAnalysisService {

    private static final List<String> SOURCE_TYPES = List.of("WEB", "JOB_POSTING", "MANUAL", "API");

    private final AdminCompanyAnalysisMapper mapper;
    private final CompanyAnalysisMapper companyAnalysisMapper;

    @Transactional(readOnly = true)
    public List<AdminCompanyAnalysisRow> companyAnalyses(AuthUser authUser, AdminCompanyAnalysisSearchCriteria criteria) {
        requireAdmin(authUser);
        return mapper.findCompanyAnalyses(normalizeCriteria(criteria));
    }

    @Transactional(readOnly = true)
    public AdminCompanyAnalysisSummary summary(AuthUser authUser, AdminCompanyAnalysisSearchCriteria criteria) {
        requireAdmin(authUser);
        return mapper.summarizeCompanyAnalyses(normalizeCriteria(criteria));
    }

    @Transactional
    public void updateMemo(AuthUser authUser, Long analysisId, String adminMemo) {
        requireAdmin(authUser);
        int updated = companyAnalysisMapper.updateAdminMemo(analysisId, blankToNull(adminMemo));
        if (updated == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Company analysis not found.");
        }
    }

    @Transactional
    public void updateMetadata(AuthUser authUser, Long analysisId, AdminCompanyAnalysisMetadataRequest request) {
        requireAdmin(authUser);
        String sourceType = requiredSourceType(request.sourceType());
        int updated = mapper.updateMetadata(
                analysisId,
                sourceType,
                request.checkedAt(),
                request.refreshRecommendedAt(),
                Boolean.TRUE.equals(request.clearCheckedAt()),
                Boolean.TRUE.equals(request.clearRefreshRecommendedAt()));
        if (updated == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Company analysis not found.");
        }
    }

    private static AdminCompanyAnalysisSearchCriteria normalizeCriteria(AdminCompanyAnalysisSearchCriteria criteria) {
        AdminCompanyAnalysisSearchCriteria base = criteria == null
                ? AdminCompanyAnalysisSearchCriteria.builder().build()
                : criteria;
        return AdminCompanyAnalysisSearchCriteria.builder()
                .keyword(blankToNull(base.keyword()))
                .sourceType(normalizeAllowedToken(base.sourceType(), SOURCE_TYPES, "sourceType"))
                .industry(blankToNull(base.industry()))
                .confirmed(base.confirmed())
                .hasMemo(base.hasMemo())
                .checked(base.checked())
                .refreshDue(base.refreshDue())
                .applicationCaseId(base.applicationCaseId())
                .userId(base.userId())
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
            case "CHECKEDATDESC", "CHECKEDDESC" -> "CHECKED_AT_DESC";
            case "CHECKEDATASC", "CHECKEDASC" -> "CHECKED_AT_ASC";
            case "REFRESHRECOMMENDEDATDESC", "REFRESHDESC" -> "REFRESH_RECOMMENDED_AT_DESC";
            case "REFRESHRECOMMENDEDATASC", "REFRESHASC" -> "REFRESH_RECOMMENDED_AT_ASC";
            case "COMPANYNAMEDESC", "COMPANYDESC" -> "COMPANY_NAME_DESC";
            case "COMPANYNAMEASC", "COMPANYASC" -> "COMPANY_NAME_ASC";
            case "INDUSTRYDESC" -> "INDUSTRY_DESC";
            case "INDUSTRYASC" -> "INDUSTRY_ASC";
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT, "sort is not allowed.");
        };
    }

    private static String sortByField(String value, boolean descending) {
        return switch (compactKey(value)) {
            case "CREATEDAT", "CREATED" -> descending ? "CREATED_AT_DESC" : "CREATED_AT_ASC";
            case "CHECKEDAT", "CHECKED" -> descending ? "CHECKED_AT_DESC" : "CHECKED_AT_ASC";
            case "REFRESHRECOMMENDEDAT", "REFRESH" -> descending ? "REFRESH_RECOMMENDED_AT_DESC" : "REFRESH_RECOMMENDED_AT_ASC";
            case "COMPANYNAME", "COMPANY" -> descending ? "COMPANY_NAME_DESC" : "COMPANY_NAME_ASC";
            case "INDUSTRY" -> descending ? "INDUSTRY_DESC" : "INDUSTRY_ASC";
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

    private static String requiredSourceType(String value) {
        String sourceType = blankToNull(value);
        if (sourceType == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "sourceType is required.");
        }
        return sourceType;
    }

    private static String compactKey(String value) {
        return value.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }
}
