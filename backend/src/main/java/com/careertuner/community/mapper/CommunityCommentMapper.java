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

    /**
     * PUBLISHED 댓글만 HIDDEN 으로 조건부 flip. 멱등(이미 HIDDEN/DELETED면 0행).
     * affected-rows>0 일 때만 comment_count 를 감소시켜 이중감소·경합을 방지한다.
     * (AI 검열 숨김·관리자 숨김 공통 경로)
     */
    int hideCommentIfPublished(@Param("id") Long id);

    /**
     * PUBLISHED 댓글만 DELETED 로 조건부 전이. affected-rows>0 일 때만 comment_count 감소.
     * 이미 HIDDEN(=count 에서 이미 빠짐)인 댓글 삭제 시 0행 → 이중감소 없음.
     */
    int deleteCommentIfPublished(@Param("id") Long id);

    /**
     * HIDDEN 댓글만 PUBLISHED 로 복원. affected-rows>0 일 때만 comment_count 증가.
     * (오탐 복원 경로)
     */
    int restoreCommentIfHidden(@Param("id") Long id);

    void incrementLikeCount(Long id);

    void decrementLikeCount(Long id);
}
