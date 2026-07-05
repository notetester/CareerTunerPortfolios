package com.careertuner.community.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.community.domain.CommentReaction;
import com.careertuner.community.domain.PostReaction;

@Mapper
public interface ReactionMapper {

    /* ── 게시글 리액션 ── */

    PostReaction findPostReaction(@Param("userId") Long userId,
                                  @Param("postId") Long postId,
                                  @Param("reactionType") String reactionType);

    /** 같은 축의 기존 리액션(추천↔비추천 교체 판정용). */
    PostReaction findPostReactionByAxis(@Param("userId") Long userId,
                                        @Param("postId") Long postId,
                                        @Param("axis") String axis);

    /** 뷰어의 이 글 리액션 전체(상세 화면 상태 벌크 조회). */
    List<PostReaction> findPostReactionsByUser(@Param("userId") Long userId,
                                               @Param("postId") Long postId);

    // dev(#238~240): affected-rows 검증을 위해 insert 반환형을 int 로.
    int insertPostReaction(PostReaction reaction);

    int deletePostReaction(@Param("userId") Long userId,
                           @Param("postId") Long postId,
                           @Param("reactionType") String reactionType);

    /** 게시글 리액션 카운트 캐시 증감(delta=+1/-1, 음수 방지). type 은 enum name 만 들어온다. */
    void adjustPostReactionCount(@Param("postId") Long postId,
                                 @Param("reactionType") String reactionType,
                                 @Param("delta") int delta);

    /** 반응자 목록 — 익명 리액션은 본인 것만 포함(타인 시점 제외 규칙). */
    List<PostReaction> findPostReactors(@Param("postId") Long postId,
                                        @Param("viewerId") Long viewerId);

    /** 게시글 수정 시 release 설정 사용자의 리액션 일괄 삭제(reactionRetention). */
    int deleteReleasedPostReactions(@Param("postId") Long postId);

    /** 리액션 카운트 캐시를 실제 행 수로 재계산(release 삭제 후 대사). */
    void reconcilePostReactionCounts(@Param("postId") Long postId);

    /* ── 댓글 리액션 ── */

    CommentReaction findCommentReaction(@Param("userId") Long userId,
                                        @Param("commentId") Long commentId,
                                        @Param("reactionType") String reactionType);

    CommentReaction findCommentReactionByAxis(@Param("userId") Long userId,
                                              @Param("commentId") Long commentId,
                                              @Param("axis") String axis);

    /** 뷰어의 이 글 댓글 리액션 전체(댓글 목록 상태 벌크 조회 — N+1 방지). */
    List<CommentReaction> findCommentReactionsByUserForPost(@Param("userId") Long userId,
                                                            @Param("postId") Long postId);

    // dev(#238~240): affected-rows 검증을 위해 insert/delete 반환형을 int 로.
    int insertCommentReaction(CommentReaction reaction);

    int deleteCommentReaction(@Param("userId") Long userId,
                              @Param("commentId") Long commentId,
                              @Param("reactionType") String reactionType);

    /** 댓글 리액션 카운트 캐시 증감(delta=+1/-1, 음수 방지). */
    void adjustCommentReactionCount(@Param("commentId") Long commentId,
                                    @Param("reactionType") String reactionType,
                                    @Param("delta") int delta);
}
