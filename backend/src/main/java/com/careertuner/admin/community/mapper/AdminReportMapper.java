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
                                @Param("actionTaken") String actionTaken,
                                @Param("adminId") Long adminId);

    void updateCommentReportStatus(@Param("commentId") Long commentId,
                                   @Param("status") String status,
                                   @Param("actionTaken") String actionTaken,
                                   @Param("adminId") Long adminId);

    void updatePostStatus(@Param("postId") Long postId, @Param("status") String status);

    void updateCommentStatus(@Param("commentId") Long commentId, @Param("status") String status);

    /** 처리 직전 PENDING 신고의 신고자 id 목록(결과 알림 대상). type: 'post' | 'comment'. */
    List<Long> findPendingReporterIds(@Param("targetId") Long targetId, @Param("type") String type);

    /** 종결(DISMISSED/CANCELLED) 게시글 신고를 PENDING 으로 재활성화. 처리 흔적은 초기화. */
    int reactivatePostReports(@Param("postId") Long postId);

    /** 종결(DISMISSED/CANCELLED) 댓글 신고를 PENDING 으로 재활성화. */
    int reactivateCommentReports(@Param("commentId") Long commentId);
}
