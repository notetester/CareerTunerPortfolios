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
                                @Param("keyword") String keyword,
                                @Param("offset") int offset,
                                @Param("limit") int limit);

    /**
     * 개인화 피드 후보 — 뷰어의 희망 직무/스킬 토큰(제목·태그·회사·직무 부분일치)이나
     * 최근 반응 카테고리에 매칭되는 PUBLISHED 글을 최신순으로 넉넉히 뽑는다.
     * 본인 글은 제외한다. 실제 7:3 랭크/인터리브는 서비스(Java)에서 수행하므로 여기서는 후보만 좁힌다.
     *
     * @param userId     뷰어 id (본인 글 제외용)
     * @param category   카테고리 필터 (없으면 전체)
     * @param tokens     희망 직무·스킬에서 뽑은 소문자 토큰 (없거나 비면 토큰 매칭 생략)
     * @param categories 최근 반응 카테고리 목록 (없거나 비면 카테고리 매칭 생략)
     * @param limit      후보 상한
     */
    List<CommunityPost> findPersonalizedCandidates(@Param("userId") Long userId,
                                                   @Param("category") String category,
                                                   @Param("tokens") List<String> tokens,
                                                   @Param("categories") List<String> categories,
                                                   @Param("limit") int limit);

    /**
     * 신선·인기 후보 — 최근성과 like_count 로 정렬한 PUBLISHED 글.
     * excludeIds 로 이미 개인화 후보에 담긴 글을 배제해 중복을 줄인다(서비스에서도 Set 으로 최종 중복 제거).
     *
     * @param category   카테고리 필터 (없으면 전체)
     * @param excludeIds 제외할 postId 목록 (없거나 비면 배제 없음)
     * @param limit      후보 상한
     */
    List<CommunityPost> findFreshPopular(@Param("category") String category,
                                         @Param("excludeIds") List<Long> excludeIds,
                                         @Param("limit") int limit);

    /**
     * 뷰어가 최근 반응(post_reaction)한 글들의 카테고리 목록 — 개인화 신호(관심 카테고리)용.
     * 최근 반응 우선으로 limit 건까지, 중복 카테고리는 SQL DISTINCT 후 서비스에서 순서 유지.
     */
    List<String> findRecentReactedCategories(@Param("userId") Long userId,
                                             @Param("limit") int limit);

    int countAll(@Param("category") String category,
                 @Param("status") String status,
                 @Param("keyword") String keyword);

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

    /** 사용자별 누적 숨김 글 수 (검열 누적 제재 판정용). 복원되면 HIDDEN 해제라 자동 감소. */
    int countHiddenByUser(@Param("userId") Long userId);

    /** 면접 질문 추출 배치 대상 (INTERVIEW_REVIEW + PUBLISHED, force=false면 이미 COMPLETED인 건 제외) */
    List<Long> findPostIdsForInterviewExtract(@Param("force") boolean force);

    // 면접후기 AI 추출 결과 저장
    void updateAiExtractedQuestions(@Param("postId") Long postId,
                                    @Param("aiExtractedQuestions") String aiExtractedQuestions);

    // 면접 질문 추출 중복 방지: source 기준 InterviewKnowledge 삭제
    void deleteInterviewKnowledgeBySource(@Param("source") String source);

    // ── 챗봇 에이전트 검색용 임베딩 (community_post_embedding 별도 테이블) ──

    /** 임베딩이 아직 없는 PUBLISHED 글 (id/title/content만) — 배치 임베딩 대상 */
    List<CommunityPost> findPostsWithoutEmbedding();

    /** 임베딩 upsert */
    void upsertEmbedding(@Param("postId") Long postId,
                         @Param("embedding") String embedding);

    /**
     * 2단계 검색 1단계: SQL 후보 좁히기. 임베딩 있는 PUBLISHED 글 중
     * keywords(제목/본문/태그 OR LIKE) + category 로 느슨하게 후보 추출.
     * keywords·category 가 비면 해당 필터를 생략(완화 재조회용).
     */
    List<com.careertuner.community.search.PostCandidate> findSearchCandidates(
            @Param("keywords") List<String> keywords,
            @Param("category") String category,
            @Param("limit") int limit);
}
