package com.careertuner.admin.guideline.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.admin.guideline.dto.AdminGuidelineResponse;

@Mapper
public interface AdminGuidelineMapper {

    List<AdminGuidelineResponse> findAll();

    AdminGuidelineResponse findById(@Param("id") Long id);

    AdminGuidelineResponse findPublished();

    void insert(
        @Param("versionLabel") String versionLabel,
        @Param("summary") String summary,
        @Param("lede") String lede,
        @Param("oksJson") String oksJson,
        @Param("nosJson") String nosJson,
        @Param("rulesJson") String rulesJson,
        @Param("paramsJson") String paramsJson,
        @Param("status") String status,
        @Param("enforceType") String enforceType,
        @Param("scheduledAt") java.time.LocalDateTime scheduledAt,
        @Param("adminId") Long adminId,
        @Param("setPublishedAt") boolean setPublishedAt
    );

    void update(
        @Param("id") Long id,
        @Param("versionLabel") String versionLabel,
        @Param("summary") String summary,
        @Param("lede") String lede,
        @Param("oksJson") String oksJson,
        @Param("nosJson") String nosJson,
        @Param("rulesJson") String rulesJson,
        @Param("paramsJson") String paramsJson,
        @Param("status") String status,
        @Param("enforceType") String enforceType,
        @Param("scheduledAt") java.time.LocalDateTime scheduledAt,
        @Param("setPublishedAt") boolean setPublishedAt
    );

    void delete(@Param("id") Long id);

    Long lastInsertId();
}
