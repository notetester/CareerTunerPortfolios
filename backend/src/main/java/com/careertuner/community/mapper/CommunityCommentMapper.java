package com.careertuner.community.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.community.domain.CommunityComment;

@Mapper
public interface CommunityCommentMapper {

    List<CommunityComment> findByPostId(@Param("postId") Long postId,
                                        @Param("status") String status);

    /**
     * 글의 모든 댓글을 상태 무관(PUBLISHED+HIDDEN+DELETED)으로 조회.
     * soft-delete만 존재하고 물리 purge가 없으므로 전체 row가 곧 익명번호 앵커이자 tombstone 판정 입력이다.
     * created_at 동일초 tie 를 id 로 안정 정렬한다.
     */
    List<CommunityComment> findAllByPostId(@Param("postId") Long postId);

    CommunityComment findById(Long id);

    void insert(CommunityComment comment);

    void updateStatus(@Param("id") Long id,
                      @Param("status") String status);

    void incrementLikeCount(Long id);

    void decrementLikeCount(Long id);
}
