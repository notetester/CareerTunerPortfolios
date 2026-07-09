package com.careertuner.community.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.community.domain.CommentReport;
import com.careertuner.community.domain.PostReport;

@Mapper
public interface ReportMapper {

    PostReport findPostReport(@Param("reporterId") Long reporterId,
                              @Param("postId") Long postId);

    void insertPostReport(PostReport report);

    /** 게시글 누적 신고 수 +1(자동 블러 판정용). */
    void incrementPostReportCount(@Param("postId") Long postId);

    CommentReport findCommentReport(@Param("reporterId") Long reporterId,
                                    @Param("commentId") Long commentId);

    void insertCommentReport(CommentReport report);

    /** 댓글 누적 신고 수 +1(자동 블러 판정용, 게시글과 동형). */
    void incrementCommentReportCount(@Param("commentId") Long commentId);
}
