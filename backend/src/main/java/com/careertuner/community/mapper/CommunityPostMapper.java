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

    // tags_json 캐시 갱신 (AI 태깅 파이프라인에서 사용)
    void updateTagsJson(@Param("id") Long id,
                        @Param("tagsJson") String tagsJson);

    /** 태깅 배치 대상 게시글 ID 목록 (PUBLISHED만, force=false면 이미 TAG COMPLETED인 건 제외) */
    List<Long> findPostIdsForTagging(@Param("force") boolean force);

    /** 검열 배치 대상 게시글 ID 목록 (PUBLISHED만, force=false면 이미 MODERATION COMPLETED인 건 제외) */
    List<Long> findPostIdsForModeration(@Param("force") boolean force);

    // AI 검열에 의한 숨김 (PUBLISHED → HIDDEN 전환, 다른 상태는 무시)
    int hideIfPublished(@Param("postId") Long postId);

    /** 면접 질문 추출 배치 대상 (INTERVIEW_REVIEW + PUBLISHED, force=false면 이미 COMPLETED인 건 제외) */
    List<Long> findPostIdsForInterviewExtract(@Param("force") boolean force);

    // 면접후기 AI 추출 결과 저장
    void updateAiExtractedQuestions(@Param("postId") Long postId,
                                    @Param("aiExtractedQuestions") String aiExtractedQuestions);

    // 면접 질문 추출 중복 방지: source 기준 InterviewKnowledge 삭제
    void deleteInterviewKnowledgeBySource(@Param("source") String source);
}
