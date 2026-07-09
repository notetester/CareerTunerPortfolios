package com.careertuner.admin.notice.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.admin.notice.dto.AdminNoticeResponse;

@Mapper
public interface AdminNoticeMapper {

    List<AdminNoticeResponse> findAll();

    AdminNoticeResponse findById(@Param("id") Long id);

    void insert(@Param("title") String title,
                @Param("content") String content,
                @Param("category") String category,
                @Param("status") String status,
                @Param("pinned") boolean pinned,
                @Param("thumbnailUrl") String thumbnailUrl,
                @Param("adminId") Long adminId,
                @Param("scheduledAt") LocalDateTime scheduledAt,
                @Param("setPublishedAt") boolean setPublishedAt);

    void update(@Param("id") Long id,
                @Param("title") String title,
                @Param("content") String content,
                @Param("category") String category,
                @Param("status") String status,
                @Param("pinned") boolean pinned,
                @Param("thumbnailUrl") String thumbnailUrl,
                @Param("scheduledAt") LocalDateTime scheduledAt,
                @Param("setPublishedAt") boolean setPublishedAt);

    void delete(@Param("id") Long id);

    Long lastInsertId();
}
