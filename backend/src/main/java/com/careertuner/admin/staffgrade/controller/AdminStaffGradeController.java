package com.careertuner.admin.staffgrade.controller;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.careertuner.admin.common.grid.ExportColumn;
import com.careertuner.admin.common.grid.ExportFormat;
import com.careertuner.admin.common.grid.GridExporter;
import com.careertuner.admin.staffgrade.domain.AdminStaffGradeHistory;
import com.careertuner.admin.staffgrade.dto.AdminStaffCandidate;
import com.careertuner.admin.staffgrade.dto.AdminStaffGradeImportApplyRequest;
import com.careertuner.admin.staffgrade.dto.AdminStaffGradeImportPreview;
import com.careertuner.admin.staffgrade.dto.AdminStaffGradeImportResult;
import com.careertuner.admin.staffgrade.dto.AdminStaffGradePage;
import com.careertuner.admin.staffgrade.dto.AdminStaffGradeRow;
import com.careertuner.admin.staffgrade.dto.AdminStaffGradeUpsertRequest;
import com.careertuner.admin.staffgrade.service.AdminStaffGradeService;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 관리자/직원 등급·급여 콘솔(SUPER_ADMIN 전용). 편집·이력·Excel 내보내기/업로드. */
@RestController
@RequestMapping("/api/admin/staff-grades")
@RequiredArgsConstructor
@Validated
public class AdminStaffGradeController {

    private final AdminStaffGradeService service;

    /** Excel/CSV 내보내기·업로드 컬럼(라운드트립용 영문 키 헤더). */
    private static final List<ExportColumn<AdminStaffGradeRow>> EXPORT_COLUMNS = List.of(
            ExportColumn.of("userId", AdminStaffGradeRow::getUserId),
            ExportColumn.of("email", AdminStaffGradeRow::getUserEmail),
            ExportColumn.of("name", AdminStaffGradeRow::getUserName),
            ExportColumn.of("department", AdminStaffGradeRow::getDepartment),
            ExportColumn.of("seniority", AdminStaffGradeRow::getSeniority),
            ExportColumn.of("job_tier", AdminStaffGradeRow::getJobTier),
            ExportColumn.of("pay_band", AdminStaffGradeRow::getPayBand),
            ExportColumn.of("job_grade", AdminStaffGradeRow::getJobGrade),
            ExportColumn.of("pay_step", AdminStaffGradeRow::getPayStep),
            ExportColumn.of("base_salary", AdminStaffGradeRow::getBaseSalary),
            ExportColumn.of("currency", AdminStaffGradeRow::getCurrency),
            ExportColumn.of("effective_date", AdminStaffGradeRow::getEffectiveDate));

    @GetMapping
    public ApiResponse<AdminStaffGradePage> grades(@AuthenticationPrincipal AuthUser authUser,
                                                   @RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) String department,
                                                   @RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.grades(authUser, keyword, department, page, size));
    }

    @GetMapping("/candidates")
    public ApiResponse<List<AdminStaffCandidate>> candidates(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(service.candidates(authUser));
    }

    @GetMapping("/{userId}")
    public ApiResponse<AdminStaffGradeRow> grade(@AuthenticationPrincipal AuthUser authUser,
                                                 @PathVariable Long userId) {
        return ApiResponse.ok(service.grade(authUser, userId));
    }

    @PutMapping("/{userId}")
    public ApiResponse<AdminStaffGradeRow> upsert(@AuthenticationPrincipal AuthUser authUser,
                                                  @PathVariable Long userId,
                                                  @Valid @RequestBody AdminStaffGradeUpsertRequest req) {
        return ApiResponse.ok(service.upsert(authUser, userId, req));
    }

    @GetMapping("/{userId}/history")
    public ApiResponse<List<AdminStaffGradeHistory>> history(@AuthenticationPrincipal AuthUser authUser,
                                                             @PathVariable Long userId) {
        return ApiResponse.ok(service.history(authUser, userId));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal AuthUser authUser,
                                         @RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) String department,
                                         @RequestParam(defaultValue = "excel") String format) {
        List<AdminStaffGradeRow> rows = service.exportRows(authUser, keyword, department);
        return GridExporter.download("careertuner-staff-grades", ExportFormat.parse(format), EXPORT_COLUMNS, rows);
    }

    @PostMapping(value = "/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AdminStaffGradeImportPreview> previewImport(@AuthenticationPrincipal AuthUser authUser,
                                                                   @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(service.previewImport(authUser, file));
    }

    @PostMapping("/import/apply")
    public ApiResponse<AdminStaffGradeImportResult> applyImport(@AuthenticationPrincipal AuthUser authUser,
                                                                @Valid @RequestBody AdminStaffGradeImportApplyRequest req) {
        return ApiResponse.ok(service.applyImport(authUser, req));
    }
}
