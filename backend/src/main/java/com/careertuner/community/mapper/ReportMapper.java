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

    CommentReport findCommentReport(@Param("reporterId") Long reporterId,
                                    @Param("commentId") Long commentId);

    void insertCommentReport(CommentReport report);
}
