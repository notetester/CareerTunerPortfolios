package com.careertuner.admin.staffgrade.service;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.careertuner.admin.common.AdminAccess;
import com.careertuner.admin.staffgrade.domain.AdminStaffGrade;
import com.careertuner.admin.staffgrade.domain.AdminStaffGradeHistory;
import com.careertuner.admin.staffgrade.dto.AdminStaffCandidate;
import com.careertuner.admin.staffgrade.dto.AdminStaffGradeImportApplyRequest;
import com.careertuner.admin.staffgrade.dto.AdminStaffGradeImportPreview;
import com.careertuner.admin.staffgrade.dto.AdminStaffGradeImportResult;
import com.careertuner.admin.staffgrade.dto.AdminStaffGradeImportRow;
import com.careertuner.admin.staffgrade.dto.AdminStaffGradePage;
import com.careertuner.admin.staffgrade.dto.AdminStaffGradeRow;
import com.careertuner.admin.staffgrade.dto.AdminStaffGradeUpsertRequest;
import com.careertuner.admin.staffgrade.mapper.AdminStaffGradeMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

/**
 * 관리자/직원 등급·급여 관리. 급여(base_salary)는 민감정보이므로 전 엔드포인트 SUPER_ADMIN 전용.
 * 변경은 old/new 스냅샷으로 admin_staff_grade_history 에 감사.
 */
@Service
@RequiredArgsConstructor
public class AdminStaffGradeService {

    private static final List<String> IMPORT_KEYS = List.of(
            "email", "department", "seniority", "job_tier", "pay_band",
            "job_grade", "pay_step", "base_salary", "currency", "effective_date");

    private final AdminStaffGradeMapper mapper;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public AdminStaffGradePage grades(AuthUser authUser, String keyword, String department, int page, int size) {
        AdminAccess.requireSuperAdmin(authUser);
        int p = Math.max(page, 1);
        int s = size <= 0 ? 20 : Math.min(size, 100);
        String kw = blankToNull(keyword);
        String dept = blankToNull(department);
        return new AdminStaffGradePage(
                mapper.findGrades(kw, dept, s, (long) (p - 1) * s),
                mapper.countGrades(kw, dept),
                p, s);
    }

    @Transactional(readOnly = true)
    public AdminStaffGradeRow grade(AuthUser authUser, Long userId) {
        AdminAccess.requireSuperAdmin(authUser);
        AdminStaffGradeRow row = mapper.findRowByUserId(userId);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다.");
        }
        return row;
    }

    @Transactional(readOnly = true)
    public List<AdminStaffGradeHistory> history(AuthUser authUser, Long userId) {
        AdminAccess.requireSuperAdmin(authUser);
        return mapper.findHistoryByUser(userId);
    }

    @Transactional(readOnly = true)
    public List<AdminStaffCandidate> candidates(AuthUser authUser) {
        AdminAccess.requireSuperAdmin(authUser);
        return mapper.findCandidates();
    }

    @Transactional
    public AdminStaffGradeRow upsert(AuthUser authUser, Long userId, AdminStaffGradeUpsertRequest req) {
        AdminAccess.requireSuperAdmin(authUser);
        AdminStaffGradeRow existingRow = mapper.findRowByUserId(userId);
        if (existingRow == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "등급을 배정할 사용자를 찾을 수 없습니다.");
        }
        AdminStaffGrade old = mapper.findByUserId(userId);
        AdminStaffGrade next = AdminStaffGrade.builder()
                .userId(userId)
                .department(blankToNull(req.department()))
                .seniority(blankToNull(req.seniority()))
                .jobTier(blankToNull(req.jobTier()))
                .payBand(blankToNull(req.payBand()))
                .jobGrade(blankToNull(req.jobGrade()))
                .payStep(blankToNull(req.payStep()))
                .baseSalary(req.baseSalary())
                .currency(req.currency() == null || req.currency().isBlank() ? "KRW" : req.currency().trim())
                .effectiveDate(req.effectiveDate())
                .memo(blankToNull(req.memo()))
                .updatedBy(authUser.id())
                .build();
        mapper.upsertGrade(next);
        recordHistory(userId, old, next, authUser.id(), "MANUAL", req.memo());
        return mapper.findRowByUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<AdminStaffGradeRow> exportRows(AuthUser authUser, String keyword, String department) {
        AdminAccess.requireSuperAdmin(authUser);
        return mapper.findAllForExport(blankToNull(keyword), blankToNull(department));
    }

    // ── Excel 업로드(미리보기 → 확정) ──
    @Transactional(readOnly = true)
    public AdminStaffGradeImportPreview previewImport(AuthUser authUser, MultipartFile file) {
        AdminAccess.requireSuperAdmin(authUser);
        List<AdminStaffGradeImportRow> rows = parseWorkbook(file);
        int ok = 0;
        for (AdminStaffGradeImportRow row : rows) {
            if ("OK".equals(row.getStatus())) {
                ok++;
            }
        }
        return new AdminStaffGradeImportPreview(rows.size(), ok, rows.size() - ok, rows);
    }

    @Transactional
    public AdminStaffGradeImportResult applyImport(AuthUser authUser, AdminStaffGradeImportApplyRequest request) {
        AdminAccess.requireSuperAdmin(authUser);
        int applied = 0;
        int skipped = 0;
        for (AdminStaffGradeImportRow row : request.rows()) {
            if (row.getUserId() == null || "ERROR".equals(row.getStatus())) {
                skipped++;
                continue;
            }
            AdminStaffGrade old = mapper.findByUserId(row.getUserId());
            AdminStaffGrade next = AdminStaffGrade.builder()
                    .userId(row.getUserId())
                    .department(blankToNull(row.getDepartment()))
                    .seniority(blankToNull(row.getSeniority()))
                    .jobTier(blankToNull(row.getJobTier()))
                    .payBand(blankToNull(row.getPayBand()))
                    .jobGrade(blankToNull(row.getJobGrade()))
                    .payStep(blankToNull(row.getPayStep()))
                    .baseSalary(row.getBaseSalary())
                    .currency(row.getCurrency() == null || row.getCurrency().isBlank() ? "KRW" : row.getCurrency().trim())
                    .effectiveDate(parseDate(row.getEffectiveDate()))
                    .memo(null)
                    .updatedBy(authUser.id())
                    .build();
            mapper.upsertGrade(next);
            recordHistory(row.getUserId(), old, next, authUser.id(), "EXCEL", "Excel 일괄 업로드");
            applied++;
        }
        return new AdminStaffGradeImportResult(applied, skipped);
    }

    private List<AdminStaffGradeImportRow> parseWorkbook(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "업로드할 파일이 없습니다.");
        }
        List<AdminStaffGradeImportRow> rows = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();
        try (InputStream in = file.getInputStream(); Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null || sheet.getPhysicalNumberOfRows() == 0) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "시트가 비어 있습니다.");
            }
            Row header = sheet.getRow(sheet.getFirstRowNum());
            Map<String, Integer> idx = headerIndex(header, formatter);
            if (!idx.containsKey("email")) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "헤더에 email 컬럼이 필요합니다. 지원 헤더: " + String.join(", ", IMPORT_KEYS));
            }
            for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row sheetRow = sheet.getRow(r);
                if (sheetRow == null || isBlankRow(sheetRow, formatter)) {
                    continue;
                }
                rows.add(parseRow(r + 1, sheetRow, idx, formatter));
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "엑셀 파일을 읽을 수 없습니다: " + e.getMessage());
        }
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "처리할 데이터 행이 없습니다.");
        }
        return rows;
    }

    private AdminStaffGradeImportRow parseRow(int rowNumber, Row sheetRow, Map<String, Integer> idx,
                                              DataFormatter formatter) {
        AdminStaffGradeImportRow row = new AdminStaffGradeImportRow();
        row.setRowNumber(rowNumber);
        row.setEmail(cell(sheetRow, idx.get("email"), formatter));
        row.setDepartment(cell(sheetRow, idx.get("department"), formatter));
        row.setSeniority(cell(sheetRow, idx.get("seniority"), formatter));
        row.setJobTier(cell(sheetRow, idx.get("job_tier"), formatter));
        row.setPayBand(cell(sheetRow, idx.get("pay_band"), formatter));
        row.setJobGrade(cell(sheetRow, idx.get("job_grade"), formatter));
        row.setPayStep(cell(sheetRow, idx.get("pay_step"), formatter));
        row.setCurrency(cell(sheetRow, idx.get("currency"), formatter));
        row.setEffectiveDate(cell(sheetRow, idx.get("effective_date"), formatter));

        String salaryRaw = cell(sheetRow, idx.get("base_salary"), formatter);
        StringBuilder err = new StringBuilder();
        if (salaryRaw != null && !salaryRaw.isBlank()) {
            try {
                int salary = Integer.parseInt(salaryRaw.replace(",", "").trim());
                if (salary < 0) {
                    err.append("급여는 0 이상이어야 함. ");
                } else {
                    row.setBaseSalary(salary);
                }
            } catch (NumberFormatException e) {
                err.append("급여 숫자 형식 오류. ");
            }
        }
        if (row.getEffectiveDate() != null && !row.getEffectiveDate().isBlank() && parseDate(row.getEffectiveDate()) == null) {
            err.append("적용일 형식(YYYY-MM-DD) 오류. ");
        }
        if (row.getEmail() == null || row.getEmail().isBlank()) {
            err.append("email 누락. ");
        } else {
            Long userId = mapper.findUserIdByEmail(row.getEmail().trim());
            if (userId == null) {
                err.append("존재하지 않는 email. ");
            } else {
                row.setUserId(userId);
            }
        }

        if (err.length() == 0) {
            row.setStatus("OK");
        } else {
            row.setStatus("ERROR");
            row.setMessage(err.toString().trim());
        }
        return row;
    }

    private Map<String, Integer> headerIndex(Row header, DataFormatter formatter) {
        Map<String, Integer> idx = new HashMap<>();
        if (header == null) {
            return idx;
        }
        for (int c = header.getFirstCellNum(); c < header.getLastCellNum(); c++) {
            String name = cell(header, c, formatter);
            if (name == null) {
                continue;
            }
            idx.put(normalizeHeader(name), c);
        }
        return idx;
    }

    /** 헤더명을 소문자·공백/한글 별칭 정규화. */
    private String normalizeHeader(String raw) {
        String v = raw.trim().toLowerCase().replace(" ", "_");
        return switch (v) {
            case "이메일" -> "email";
            case "부서" -> "department";
            case "연차" -> "seniority";
            case "티어", "직군티어" -> "job_tier";
            case "밴드", "급여밴드" -> "pay_band";
            case "등급", "직급" -> "job_grade";
            case "호봉", "스텝" -> "pay_step";
            case "기본급", "연봉", "급여" -> "base_salary";
            case "통화" -> "currency";
            case "적용일" -> "effective_date";
            default -> v;
        };
    }

    private void recordHistory(Long userId, AdminStaffGrade oldGrade, AdminStaffGrade newGrade,
                               Long changedBy, String source, String memo) {
        mapper.insertHistory(AdminStaffGradeHistory.builder()
                .userId(userId)
                .oldValuesJson(toJson(oldGrade))
                .newValuesJson(toJson(newGrade))
                .changedBy(changedBy)
                .source(source)
                .memo(blankToNull(memo))
                .build());
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String cell(Row row, Integer index, DataFormatter formatter) {
        if (index == null || index < 0) {
            return null;
        }
        var c = row.getCell(index);
        if (c == null) {
            return null;
        }
        String v = formatter.formatCellValue(c);
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static boolean isBlankRow(Row row, DataFormatter formatter) {
        for (int c = row.getFirstCellNum(); c >= 0 && c < row.getLastCellNum(); c++) {
            if (cell(row, c, formatter) != null) {
                return false;
            }
        }
        return true;
    }

    private static String blankToNull(String v) {
        if (v == null) {
            return null;
        }
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }
}
