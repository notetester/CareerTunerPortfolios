package com.careertuner.admin.jobanalysis.service;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.admin.jobanalysis.dto.AdminJobAnalysisRow;
import com.careertuner.admin.jobanalysis.dto.AdminJobAnalysisSearchCriteria;
import com.careertuner.admin.jobanalysis.dto.AdminJobAnalysisSummary;
import com.careertuner.admin.jobanalysis.mapper.AdminJobAnalysisMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;
import com.careertuner.jobanalysis.mapper.JobAnalysisMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminJobAnalysisService {

    private static final List<String> DIFFICULTIES = List.of("EASY", "LOW", "NORMAL", "MEDIUM", "HARD", "HIGH");

    private final AdminJobAnalysisMapper mapper;
    private final JobAnalysisMapper jobAnalysisMapper;

    @Transactional(readOnly = true)
    public List<AdminJobAnalysisRow> jobAnalyses(AuthUser authUser, AdminJobAnalysisSearchCriteria criteria) {
        requireAdmin(authUser);
        return mapper.findJobAnalyses(normalizeCriteria(criteria));
    }

    @Transactional(readOnly = true)
    public AdminJobAnalysisSummary summary(AuthUser authUser, AdminJobAnalysisSearchCriteria criteria) {
        requireAdmin(authUser);
        return mapper.summarizeJobAnalyses(normalizeCriteria(criteria));
    }

    @Transactional
    public void updateMemo(AuthUser authUser, Long analysisId, String adminMemo) {
        requireAdmin(authUser);
        int updated = jobAnalysisMapper.updateAdminMemo(analysisId, blankToNull(adminMemo));
        if (updated == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Job analysis not found.");
        }
    }

    private static AdminJobAnalysisSearchCriteria normalizeCriteria(AdminJobAnalysisSearchCriteria criteria) {
        AdminJobAnalysisSearchCriteria base = criteria == null
                ? AdminJobAnalysisSearchCriteria.builder().build()
                : criteria;
        return AdminJobAnalysisSearchCriteria.builder()
                .keyword(blankToNull(base.keyword()))
                .difficulty(normalizeAllowedToken(base.difficulty(), DIFFICULTIES, "difficulty"))
                .confirmed(base.confirmed())
                .hasMemo(base.hasMemo())
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
            case "CONFIRMEDATDESC", "CONFIRMEDDESC" -> "CONFIRMED_AT_DESC";
            case "CONFIRMEDATASC", "CONFIRMEDASC" -> "CONFIRMED_AT_ASC";
            case "DIFFICULTYDESC" -> "DIFFICULTY_DESC";
            case "DIFFICULTYASC" -> "DIFFICULTY_ASC";
            case "COMPANYNAMEDESC", "COMPANYDESC" -> "COMPANY_NAME_DESC";
            case "COMPANYNAMEASC", "COMPANYASC" -> "COMPANY_NAME_ASC";
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT, "sort is not allowed.");
        };
    }

    private static String sortByField(String value, boolean descending) {
        return switch (compactKey(value)) {
            case "CREATEDAT", "CREATED" -> descending ? "CREATED_AT_DESC" : "CREATED_AT_ASC";
            case "CONFIRMEDAT", "CONFIRMED" -> descending ? "CONFIRMED_AT_DESC" : "CONFIRMED_AT_ASC";
            case "DIFFICULTY" -> descending ? "DIFFICULTY_DESC" : "DIFFICULTY_ASC";
            case "COMPANYNAME", "COMPANY" -> descending ? "COMPANY_NAME_DESC" : "COMPANY_NAME_ASC";
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
