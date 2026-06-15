package com.careertuner.admin.applicationcase.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.admin.applicationcase.dto.AdminApplicationCaseRow;
import com.careertuner.admin.applicationcase.dto.AdminApplicationCaseSearchCriteria;
import com.careertuner.admin.applicationcase.dto.AdminApplicationCaseSummary;

@Mapper
public interface AdminApplicationCaseMapper {

    List<AdminApplicationCaseRow> findApplicationCases(@Param("criteria") AdminApplicationCaseSearchCriteria criteria);

    AdminApplicationCaseSummary summarizeApplicationCases(@Param("criteria") AdminApplicationCaseSearchCriteria criteria);

    AdminApplicationCaseRow findApplicationCase(@Param("id") Long id);

    int updateStatus(@Param("id") Long id, @Param("status") String status);
}
