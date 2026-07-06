package com.careertuner.community.moderation.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.community.moderation.domain.ModerationSetting;

@Mapper
public interface ModerationSettingMapper {

    /** 코어 설정 조회(엄격도·숨김 임계·사용자 제재) — 항상 존재하는 컬럼만. */
    ModerationSetting findById(@Param("id") int id);

    /**
     * rate-limit 3쌍 + 신고 블러 임계 조회(patches/20260706b 컬럼).
     * 미적용 DB 에서 실패해도 코어 로드를 막지 않도록 분리 조회한다.
     */
    ModerationSetting findRateLimits(@Param("id") int id);

    /** 코어 검열 정책 갱신(엄격도·숨김 임계·사용자 제재) — 항상 존재하는 컬럼만. */
    int updateCore(ModerationSetting setting);

    /**
     * rate-limit 3쌍 + 신고 블러 임계 갱신(patches/20260706b 컬럼).
     * 미적용 DB 에서 실패해도 코어 갱신을 막지 않도록 서비스에서 분리 호출한다.
     */
    int updateRateLimits(ModerationSetting setting);
}
