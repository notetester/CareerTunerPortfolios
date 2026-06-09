package com.careertuner.admin.interview.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.admin.interview.dto.AdminInterviewSessionRow;

@Mapper
public interface AdminInterviewMapper {

    List<AdminInterviewSessionRow> findSessions(
            @Param("keyword") String keyword,
            @Param("mode") String mode,
            @Param("limit") int limit);

    AdminInterviewSessionRow findSession(@Param("id") Long id);

    String findReport(@Param("id") Long id);
}
