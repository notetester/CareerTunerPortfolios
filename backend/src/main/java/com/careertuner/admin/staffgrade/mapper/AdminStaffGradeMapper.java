package com.careertuner.admin.staffgrade.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.admin.staffgrade.domain.AdminStaffGrade;
import com.careertuner.admin.staffgrade.domain.AdminStaffGradeHistory;
import com.careertuner.admin.staffgrade.dto.AdminStaffCandidate;
import com.careertuner.admin.staffgrade.dto.AdminStaffGradeRow;

/** 관리자/직원 등급·급여 및 변경 이력 접근. */
@Mapper
public interface AdminStaffGradeMapper {

    List<AdminStaffGradeRow> findGrades(@Param("keyword") String keyword,
                                        @Param("department") String department,
                                        @Param("size") int size, @Param("offset") long offset);

    long countGrades(@Param("keyword") String keyword, @Param("department") String department);

    List<AdminStaffGradeRow> findAllForExport(@Param("keyword") String keyword,
                                              @Param("department") String department);

    /** users 기준 LEFT JOIN — 사용자가 없으면 null, 등급 미배정이면 등급 컬럼만 null. */
    AdminStaffGradeRow findRowByUserId(@Param("userId") Long userId);

    /** 등급 스냅샷(변경 전 old 값 산출용). 미배정이면 null. */
    AdminStaffGrade findByUserId(@Param("userId") Long userId);

    void upsertGrade(AdminStaffGrade grade);

    void insertHistory(AdminStaffGradeHistory history);

    List<AdminStaffGradeHistory> findHistoryByUser(@Param("userId") Long userId);

    Long findUserIdByEmail(@Param("email") String email);

    String findUserRoleForUpdate(@Param("userId") Long userId);

    List<AdminStaffCandidate> findCandidates();
}
