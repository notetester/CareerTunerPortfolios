package com.careertuner.admin.applicationcase.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.admin.applicationcase.dto.AdminApplicationCaseRow;

@Mapper
public interface AdminApplicationCaseMapper {

    List<AdminApplicationCaseRow> findApplicationCases(
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("includeArchived") boolean includeArchived,
            @Param("includeDeleted") boolean includeDeleted,
            @Param("limit") int limit);

    AdminApplicationCaseRow findApplicationCase(@Param("id") Long id);

    int updateStatus(@Param("id") Long id, @Param("status") String status);
}
