package com.careertuner.community.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.community.domain.CommunityComment;

@Mapper
public interface CommunityCommentMapper {

    List<CommunityComment> findByPostId(@Param("postId") Long postId,
                                        @Param("status") String status);

    CommunityComment findById(Long id);

    void insert(CommunityComment comment);

    void updateStatus(@Param("id") Long id,
                      @Param("status") String status);

    void incrementLikeCount(Long id);

    void decrementLikeCount(Long id);
}
