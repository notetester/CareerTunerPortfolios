package com.careertuner.admin.community.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.admin.community.dto.AdminReportListResponse;
import com.careertuner.admin.community.dto.ReportReasonCount;

@Mapper
public interface AdminReportMapper {

    List<AdminReportListResponse> findAll(@Param("status") String status);

    AdminReportListResponse findById(@Param("id") Long id, @Param("type") String type);

    List<ReportReasonCount> findReasonCounts(@Param("targetId") Long targetId, @Param("type") String type);

    void updatePostReportStatus(@Param("postId") Long postId,
                                @Param("status") String status,
                                @Param("actionTaken") String actionTaken);

    void updateCommentReportStatus(@Param("commentId") Long commentId,
                                   @Param("status") String status,
                                   @Param("actionTaken") String actionTaken);

    void updatePostStatus(@Param("postId") Long postId, @Param("status") String status);

    void updateCommentStatus(@Param("commentId") Long commentId, @Param("status") String status);
}
