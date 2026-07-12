package com.careertuner.community.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CommunityTagMapper {

    /** 태그명으로 ID 조회. 없으면 null. */
    Long findIdByName(@Param("name") String name);

    /** 태그 신규 등록. useGeneratedKeys로 id 반환. */
    void insertTag(@Param("name") String name);

    /** usage_count 증감. */
    void incrementUsageCount(@Param("id") Long id);

    void decrementUsageCount(@Param("id") Long id);

    /** 해당 게시글의 AI 태그(is_ai=1) 전체 삭제. 삭제된 tag_id 목록 반환. */
    List<Long> findAiTagIds(@Param("postId") Long postId);

    void deleteAiPostTags(@Param("postId") Long postId);

    /** 해당 게시글의 사용자 입력 태그(is_ai=0) tag_id 목록. */
    List<Long> findUserTagIds(@Param("postId") Long postId);

    /** 해당 게시글의 사용자 입력 태그(is_ai=0) 전체 삭제. */
    void deleteUserPostTags(@Param("postId") Long postId);

    /**
     * 게시글-태그 매핑 추가. PK(post_id, tag_id) 중복은 ON DUPLICATE KEY UPDATE로 흡수한다.
     * 반환 affected rows: 신규 INSERT=1, 기존 행 갱신=2(변경 없음 0).
     * 호출부는 1(신규)일 때만 usage_count를 증가시켜 카운트 이중증가를 막는다.
     */
    int insertPostTag(@Param("postId") Long postId,
                      @Param("tagId") Long tagId,
                      @Param("isAi") boolean isAi);

    /** 해당 게시글의 모든 태그명 조회 (is_ai 무관). */
    List<String> findTagNamesByPostId(@Param("postId") Long postId);

    /** soft delete/복원 후 태그 사용량 캐시를 활성 연결 수와 일치시킨다. */
    void reconcileUsageCount(@Param("id") Long id);
}
