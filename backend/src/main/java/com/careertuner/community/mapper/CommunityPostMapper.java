package com.careertuner.community.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.community.domain.CommunityInterviewReview;
import com.careertuner.community.domain.CommunityPost;

@Mapper
public interface CommunityPostMapper {

    List<CommunityPost> findAll(@Param("category") String category,
                                @Param("status") String status,
                                @Param("sort") String sort,
                                @Param("offset") int offset,
                                @Param("limit") int limit);

    int countAll(@Param("category") String category,
                 @Param("status") String status);

    CommunityPost findById(Long id);

    void insert(CommunityPost post);

    void update(CommunityPost post);

    void updateStatus(@Param("id") Long id,
                      @Param("status") String status);

    void incrementViewCount(Long id);

    // 인기글 조회
    List<CommunityPost> findHotPosts(@Param("status") String status,
                                     @Param("since") LocalDateTime since,
                                     @Param("limit") int limit);

    // 댓글 수 증감
    void incrementCommentCount(Long id);

    void decrementCommentCount(Long id);

    // 좋아요 수 증감
    void incrementLikeCount(Long id);

    void decrementLikeCount(Long id);

    // 북마크 수 증감
    void incrementBookmarkCount(Long id);

    void decrementBookmarkCount(Long id);

    // 면접후기 확장
    CommunityInterviewReview findInterviewReviewByPostId(Long postId);

    void upsertInterviewReview(CommunityInterviewReview review);

    void deleteInterviewReview(Long postId);
}
