package com.careertuner.admin.companyanalysis.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.admin.companyanalysis.dto.AdminCompanyAnalysisRow;

@Mapper
public interface AdminCompanyAnalysisMapper {

    List<AdminCompanyAnalysisRow> findCompanyAnalyses(@Param("limit") int limit);

    int updateMetadata(@Param("id") Long id,
                       @Param("sourceType") String sourceType,
                       @Param("checkedAt") LocalDateTime checkedAt,
                       @Param("refreshRecommendedAt") LocalDateTime refreshRecommendedAt,
                       @Param("clearCheckedAt") boolean clearCheckedAt,
                       @Param("clearRefreshRecommendedAt") boolean clearRefreshRecommendedAt);
}
