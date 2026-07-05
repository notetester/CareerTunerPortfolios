package com.careertuner.community.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.community.domain.ActivityItem;

/**
 * 활동 목록 조회 — 내 활동(includeAnonymous=true)과
 * 타인 프로필 활동(includeAnonymous=false — 익명 작성/익명 리액션 제외) 공용.
 */
@Mapper
public interface CommunityActivityMapper {

    String findUserName(@Param("userId") Long userId);

    /* ── 작성 글 ── */
    List<ActivityItem> findPosts(@Param("ownerId") Long ownerId,
                                 @Param("includeAnonymous") boolean includeAnonymous,
                                 @Param("offset") int offset, @Param("limit") int limit);

    int countPosts(@Param("ownerId") Long ownerId,
                   @Param("includeAnonymous") boolean includeAnonymous);

    /* ── 작성 댓글/답글 (replies=true 면 parent_id 있는 답글만) ── */
    List<ActivityItem> findComments(@Param("ownerId") Long ownerId,
                                    @Param("includeAnonymous") boolean includeAnonymous,
                                    @Param("replies") boolean replies,
                                    @Param("offset") int offset, @Param("limit") int limit);

    int countComments(@Param("ownerId") Long ownerId,
                      @Param("includeAnonymous") boolean includeAnonymous,
                      @Param("replies") boolean replies);

    /* ── 좋아요한 글·댓글 (post/comment LIKE UNION, 리액션 시각순) ── */
    List<ActivityItem> findLikedItems(@Param("ownerId") Long ownerId,
                                      @Param("includeAnonymous") boolean includeAnonymous,
                                      @Param("offset") int offset, @Param("limit") int limit);

    int countLikedItems(@Param("ownerId") Long ownerId,
                        @Param("includeAnonymous") boolean includeAnonymous);

    /* ── 즐겨찾기(BOOKMARK)한 글 ── */
    List<ActivityItem> findBookmarkedPosts(@Param("ownerId") Long ownerId,
                                           @Param("includeAnonymous") boolean includeAnonymous,
                                           @Param("offset") int offset, @Param("limit") int limit);

    int countBookmarkedPosts(@Param("ownerId") Long ownerId,
                             @Param("includeAnonymous") boolean includeAnonymous);

    /* ── 스크랩 ── */
    List<ActivityItem> findScraps(@Param("ownerId") Long ownerId,
                                  @Param("includeAnonymous") boolean includeAnonymous,
                                  @Param("offset") int offset, @Param("limit") int limit);

    int countScraps(@Param("ownerId") Long ownerId,
                    @Param("includeAnonymous") boolean includeAnonymous);
}
