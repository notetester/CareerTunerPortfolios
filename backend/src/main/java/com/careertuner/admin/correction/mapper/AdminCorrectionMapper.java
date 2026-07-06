package com.careertuner.admin.correction.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.admin.correction.dto.AdminCorrectionDetail;
import com.careertuner.admin.correction.dto.AdminCorrectionFailureRow;
import com.careertuner.admin.correction.dto.AdminCorrectionRow;
import com.careertuner.admin.correction.dto.AdminCorrectionSearchCriteria;
import com.careertuner.admin.correction.dto.AdminCorrectionSummary;

@Mapper
public interface AdminCorrectionMapper {
    List<AdminCorrectionRow> findCorrections(@Param("criteria") AdminCorrectionSearchCriteria criteria);

    long countCorrections(@Param("criteria") AdminCorrectionSearchCriteria criteria);

    AdminCorrectionSummary findSummary();

    AdminCorrectionDetail findCorrection(@Param("id") Long id);

    List<AdminCorrectionFailureRow> findAiFailures(@Param("limit") int limit);

    int updateAdminMemo(@Param("id") Long id, @Param("adminMemo") String adminMemo);
}
