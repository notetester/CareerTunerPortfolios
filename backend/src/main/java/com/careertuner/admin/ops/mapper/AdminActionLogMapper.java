package com.careertuner.admin.ops.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.admin.ops.dto.AdminActionLogCreate;
import com.careertuner.admin.ops.dto.AdminActionLogRow;

@Mapper
public interface AdminActionLogMapper {

    void insert(AdminActionLogCreate log);

    List<AdminActionLogRow> findRecent(@Param("keyword") String keyword,
                                       @Param("actionType") String actionType,
                                       @Param("targetType") String targetType,
                                       @Param("limit") int limit);
}
