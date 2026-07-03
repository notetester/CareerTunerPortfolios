package com.careertuner.community.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.community.domain.RecommendationCandidate;

@Mapper
public interface RecommendationCandidateMapper {

    /**
     * 추천 알림 후보 조회 — ACTIVE 상태이고 희망 직무(desired_job)를 입력한 사용자.
     * 글 작성자는 제외한다. 토큰 매칭은 Java 에서 하므로 여기서는 후보만 좁힌다.
     *
     * @param excludeUserId 글 작성자 id (수신 제외)
     * @param limit         후보 상한 (전체 스캔 방지용 안전장치)
     */
    List<RecommendationCandidate> findActiveCandidates(@Param("excludeUserId") Long excludeUserId,
                                                       @Param("limit") int limit);
}
