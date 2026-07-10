package com.careertuner.admin.applicationcase.service;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.admin.aiusage.mapper.AdminAiUsageMapper;
import com.careertuner.admin.applicationcase.dto.AdminApplicationCaseDetail;
import com.careertuner.admin.applicationcase.dto.AdminApplicationCaseRow;
import com.careertuner.admin.applicationcase.dto.AdminApplicationCaseSearchCriteria;
import com.careertuner.admin.applicationcase.dto.AdminApplicationCaseSummary;
import com.careertuner.admin.applicationcase.dto.AdminStatusUpdateRequest;
import com.careertuner.admin.applicationcase.mapper.AdminApplicationCaseMapper;
import com.careertuner.applicationcase.mapper.ApplicationCaseMapper;
import com.careertuner.applicationcase.support.BDisplayTime;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;
import com.careertuner.companyanalysis.domain.CompanyAnalysis;
import com.careertuner.companyanalysis.dto.CompanyAnalysisResponse;
import com.careertuner.companyanalysis.mapper.CompanyAnalysisMapper;
import com.careertuner.companyanalysis.service.BCompanyAnalysisCanonicalizer;
import com.careertuner.jobanalysis.domain.JobAnalysis;
import com.careertuner.jobanalysis.dto.JobAnalysisResponse;
import com.careertuner.jobanalysis.mapper.JobAnalysisMapper;
import com.careertuner.jobposting.domain.JobPosting;
import com.careertuner.jobposting.dto.JobPostingResponse;
import com.careertuner.jobposting.mapper.JobPostingMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminApplicationCaseService {

    private static final Set<String> STATUSES = Set.of("DRAFT", "ANALYZING", "READY", "APPLIED", "CLOSED");
    private static final Set<String> SOURCE_TYPES = Set.of("TEXT", "PDF", "IMAGE", "URL", "MANUAL");

    private final AdminApplicationCaseMapper mapper;
    private final ApplicationCaseMapper applicationCaseMapper;
    private final JobPostingMapper jobPostingMapper;
    private final JobAnalysisMapper jobAnalysisMapper;
    private final CompanyAnalysisMapper companyAnalysisMapper;
    private final AdminAiUsageMapper aiUsageMapper;
    private final BCompanyAnalysisCanonicalizer companyAnalysisCanonicalizer;

    @Transactional(readOnly = true)
    public List<AdminApplicationCaseRow> applicationCases(AuthUser authUser, AdminApplicationCaseSearchCriteria criteria) {
        requireAdmin(authUser);
        return mapper.findApplicationCases(normalizeCriteria(criteria)).stream()
                .map(AdminApplicationCaseService::toDisplayRow)
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminApplicationCaseSummary summary(AuthUser authUser, AdminApplicationCaseSearchCriteria criteria) {
        requireAdmin(authUser);
        return mapper.summarizeApplicationCases(normalizeCriteria(criteria));
    }

    @Transactional(readOnly = true)
    public AdminApplicationCaseDetail detail(AuthUser authUser, Long id) {
        requireAdmin(authUser);
        AdminApplicationCaseRow applicationCase = mapper.findApplicationCase(id);
        if (applicationCase == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Application case not found.");
        }
        // created_at 등은 DB CURRENT_TIMESTAMP(UTC)로 저장된다. 사용자 화면과 동일하게 관리자 상세도 KST 로 표시한다(BDisplayTime).
        return new AdminApplicationCaseDetail(
                toDisplayRow(applicationCase),
                jobPostingMapper.findJobPostingRevisionsByCaseId(id).stream().map(this::toJobPostingResponse).toList(),
                jobAnalysisMapper.findJobAnalysisHistoryByCaseId(id).stream().map(this::toJobAnalysisResponse).toList(),
                companyAnalysisMapper.findCompanyAnalysisHistoryByCaseId(id).stream()
                        .map(this::toCompanyAnalysisResponse)
                        .toList(),
                aiUsageMapper.findBUsageLogsByCaseId(id, 100),
                applicationCaseMapper.findStatusHistoryByCaseId(id).stream()
                        .peek(h -> h.setCreatedAt(BDisplayTime.dbToDisplay(h.getCreatedAt())))
                        .toList());
    }

    private JobPostingResponse toJobPostingResponse(JobPosting jobPosting) {
        jobPosting.setCreatedAt(BDisplayTime.dbToDisplay(jobPosting.getCreatedAt()));
        return JobPostingResponse.from(jobPosting);
    }

    private JobAnalysisResponse toJobAnalysisResponse(JobAnalysis analysis) {
        analysis.setCreatedAt(BDisplayTime.dbToDisplay(analysis.getCreatedAt()));
        return JobAnalysisResponse.from(analysis);
    }

    private CompanyAnalysisResponse toCompanyAnalysisResponse(CompanyAnalysis analysis) {
        analysis.setCreatedAt(BDisplayTime.dbToDisplay(analysis.getCreatedAt()));
        return CompanyAnalysisResponse.from(
                analysis,
                companyAnalysisCanonicalizer.withoutUnknownMarkers(analysis.getAiInferences()),
                companyAnalysisCanonicalizer.extractUnknowns(analysis.getAiInferences()));
    }

    @Transactional
    public AdminApplicationCaseRow updateStatus(AuthUser authUser, Long id, AdminStatusUpdateRequest request) {
        requireAdmin(authUser);
        AdminApplicationCaseRow existing = mapper.findApplicationCase(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Application case not found.");
        }
        String nextStatus = normalizeStatus(request.status(), true);
        int updated = mapper.updateStatus(id, nextStatus);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Application case not found.");
        }
        applicationCaseMapper.insertStatusHistory(id, authUser.id(), existing.getStatus(), nextStatus, blankToNull(request.memo()));
        return toDisplayRow(mapper.findApplicationCase(id));
    }

    /**
     * 관리자 행의 표시 시각을 KST 로 맞춘다. created_at/updated_at/deleted_at 과 최신 분석 시각(MAX(created_at))은
     * DB(UTC)라 UTC→KST 변환하고, archived_at 은 Java(now)로 이미 KST 라 그대로 둔다(BDisplayTime).
     */
    private static AdminApplicationCaseRow toDisplayRow(AdminApplicationCaseRow row) {
        if (row == null) {
            return null;
        }
        row.setCreatedAt(BDisplayTime.dbToDisplay(row.getCreatedAt()));
        row.setUpdatedAt(BDisplayTime.dbToDisplay(row.getUpdatedAt()));
        row.setDeletedAt(BDisplayTime.dbToDisplay(row.getDeletedAt()));
        row.setLatestJobAnalysisAt(BDisplayTime.dbToDisplay(row.getLatestJobAnalysisAt()));
        row.setLatestCompanyAnalysisAt(BDisplayTime.dbToDisplay(row.getLatestCompanyAnalysisAt()));
        return row;
    }

    private static AdminApplicationCaseSearchCriteria normalizeCriteria(AdminApplicationCaseSearchCriteria criteria) {
        AdminApplicationCaseSearchCriteria base = criteria == null
                ? AdminApplicationCaseSearchCriteria.builder().includeArchived(true).build()
                : criteria;
        return AdminApplicationCaseSearchCriteria.builder()
                .keyword(blankToNull(base.keyword()))
                .status(normalizeStatus(base.status(), false))
                .includeArchived(base.includeArchived())
                .includeDeleted(base.includeDeleted())
                .sourceType(normalizeSourceType(base.sourceType()))
                .favorite(base.favorite())
                .createdFrom(base.createdFrom())
                .createdTo(base.createdTo())
                .deadlineFrom(base.deadlineFrom())
                .deadlineTo(base.deadlineTo())
                .analysisState(normalizeAnalysisState(base.analysisState()))
                .sort(normalizeSort(base.sort()))
                .limit(normalizeLimit(base.limit()))
                .offset(normalizeOffset(base.offset()))
                .build();
    }

    private static void requireAdmin(AuthUser authUser) {
        com.careertuner.admin.common.AdminAccess.requireAdmin(authUser);
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

    private static String normalizeStatus(String value, boolean required) {
        String normalized = normalizedToken(value);
        if (normalized == null) {
            if (required) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "status is required.");
            }
            return null;
        }
        if (!STATUSES.contains(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "status is not allowed.");
        }
        return normalized;
    }

    private static String normalizeSourceType(String value) {
        String normalized = normalizedToken(value);
        if (normalized == null) {
            return null;
        }
        if (!SOURCE_TYPES.contains(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "sourceType is not allowed.");
        }
        return normalized;
    }

    private static String normalizeAnalysisState(String value) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            return null;
        }
        return switch (compactKey(normalized)) {
            case "NONE", "NOANALYSIS", "MISSINGALL", "MISSINGALLANALYSIS" -> "NO_ANALYSIS";
            case "MISSINGJOB", "MISSINGJOBANALYSIS" -> "MISSING_JOB_ANALYSIS";
            case "MISSINGCOMPANY", "MISSINGCOMPANYANALYSIS" -> "MISSING_COMPANY_ANALYSIS";
            case "MISSINGANY", "MISSINGANYANALYSIS", "INCOMPLETE" -> "MISSING_ANY_ANALYSIS";
            case "COMPLETE", "COMPLETEANALYSIS" -> "COMPLETE_ANALYSIS";
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT, "analysisState is not allowed.");
        };
    }

    private static String normalizeSort(String value) {
        String sort = blankToNull(value);
        if (sort == null) {
            return "UPDATED_AT_DESC";
        }
        if (sort.startsWith("-") || sort.startsWith("+")) {
            return sortByField(sort.substring(1), sort.startsWith("-"));
        }
        return switch (compactKey(sort)) {
            case "UPDATEDATDESC", "UPDATEDDESC" -> "UPDATED_AT_DESC";
            case "UPDATEDATASC", "UPDATEDASC" -> "UPDATED_AT_ASC";
            case "CREATEDATDESC", "CREATEDDESC" -> "CREATED_AT_DESC";
            case "CREATEDATASC", "CREATEDASC" -> "CREATED_AT_ASC";
            case "DEADLINEDATEDESC", "DEADLINEDESC" -> "DEADLINE_DATE_DESC";
            case "DEADLINEDATEASC", "DEADLINEASC" -> "DEADLINE_DATE_ASC";
            case "COMPANYNAMEDESC", "COMPANYDESC" -> "COMPANY_NAME_DESC";
            case "COMPANYNAMEASC", "COMPANYASC" -> "COMPANY_NAME_ASC";
            case "LATESTJOBANALYSISATDESC", "JOBANALYSISDESC" -> "LATEST_JOB_ANALYSIS_AT_DESC";
            case "LATESTJOBANALYSISATASC", "JOBANALYSISASC" -> "LATEST_JOB_ANALYSIS_AT_ASC";
            case "LATESTCOMPANYANALYSISATDESC", "COMPANYANALYSISDESC" -> "LATEST_COMPANY_ANALYSIS_AT_DESC";
            case "LATESTCOMPANYANALYSISATASC", "COMPANYANALYSISASC" -> "LATEST_COMPANY_ANALYSIS_AT_ASC";
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT, "sort is not allowed.");
        };
    }

    private static String sortByField(String value, boolean descending) {
        return switch (compactKey(value)) {
            case "UPDATEDAT", "UPDATED" -> descending ? "UPDATED_AT_DESC" : "UPDATED_AT_ASC";
            case "CREATEDAT", "CREATED" -> descending ? "CREATED_AT_DESC" : "CREATED_AT_ASC";
            case "DEADLINEDATE", "DEADLINE" -> descending ? "DEADLINE_DATE_DESC" : "DEADLINE_DATE_ASC";
            case "COMPANYNAME", "COMPANY" -> descending ? "COMPANY_NAME_DESC" : "COMPANY_NAME_ASC";
            case "LATESTJOBANALYSISAT", "JOBANALYSIS" -> descending ? "LATEST_JOB_ANALYSIS_AT_DESC" : "LATEST_JOB_ANALYSIS_AT_ASC";
            case "LATESTCOMPANYANALYSISAT", "COMPANYANALYSIS" -> descending ? "LATEST_COMPANY_ANALYSIS_AT_DESC" : "LATEST_COMPANY_ANALYSIS_AT_ASC";
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

    private static String compactKey(String value) {
        return value.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }
}
