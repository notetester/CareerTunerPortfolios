package com.careertuner.community.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.community.domain.RecommendationCandidate;

/**
 * 개인화 피드 신호 조회 매퍼.
 *
 * <p>글 후보 조회는 {@link CommunityPostMapper} 가 담당하고(커뮤니티 도메인 소유),
 * 여기서는 뷰어 본인의 프로필 신호(희망 직무·스킬)만 단건으로 읽는다.
 * 결과 형태는 추천 알림과 동일한 {@link RecommendationCandidate} 를 재사용한다.
 */
@Mapper
public interface PersonalizedFeedMapper {

    /**
     * 뷰어의 개인화 매칭 재료(희망 직무·스킬 JSON)를 단건 조회한다.
     * 프로필이 없으면 null. desired_job/skills 가 전부 비어 있으면 신호 부족으로 폴백 대상이 된다.
     */
    RecommendationCandidate findViewerProfile(@Param("userId") Long userId);
}
