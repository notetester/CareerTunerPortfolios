package com.careertuner.community.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CommunitySubscriptionMapper {

    /* ── 글 구독 ── */

    boolean existsPostSubscription(@Param("userId") Long userId, @Param("postId") Long postId);

    void insertPostSubscription(@Param("userId") Long userId, @Param("postId") Long postId);

    void deletePostSubscription(@Param("userId") Long userId, @Param("postId") Long postId);

    /** 글 구독자 id 목록(새 댓글 팬아웃 — 작성자 본인 제외는 서비스에서). */
    List<Long> findPostSubscriberIds(@Param("postId") Long postId);

    /* ── 댓글 구독 ── */

    boolean existsCommentSubscription(@Param("userId") Long userId, @Param("commentId") Long commentId);

    void insertCommentSubscription(@Param("userId") Long userId, @Param("commentId") Long commentId);

    void deleteCommentSubscription(@Param("userId") Long userId, @Param("commentId") Long commentId);

    /** 여러 댓글의 구독자 id 목록(루트+클릭 대상 dedup 은 서비스에서). */
    List<Long> findCommentSubscriberIds(@Param("commentIds") List<Long> commentIds);

    /** 뷰어가 구독 중인 이 글의 댓글 id 목록(댓글 목록 상태 벌크 조회). */
    List<Long> findSubscribedCommentIds(@Param("userId") Long userId, @Param("postId") Long postId);
}
