package com.careertuner.community.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.community.domain.PostScrap;

@Mapper
public interface PostScrapMapper {

    /** 원본 글 기준 활성 스크랩(토글 판정). 원본이 소실돼 post_id 가 NULL 인 행은 대상 아님. */
    PostScrap findByUserAndPost(@Param("userId") Long userId, @Param("postId") Long postId);

    PostScrap findById(@Param("id") Long id);

    void insert(PostScrap scrap);

    void deleteById(@Param("id") Long id);

    List<PostScrap> findByUser(@Param("userId") Long userId,
                               @Param("offset") int offset,
                               @Param("limit") int limit);

    int countByUser(@Param("userId") Long userId);

    /** 스크랩 수 캐시 증감(음수 방지). */
    void adjustScrapCount(@Param("postId") Long postId, @Param("delta") int delta);
}
