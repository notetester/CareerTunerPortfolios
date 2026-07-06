package com.careertuner.community.moderation.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.community.moderation.domain.ModerationSetting;

@Mapper
public interface ModerationSettingMapper {

    /** 단일 정책 행 전체 조회(엄격도·숨김·제재 + rate-limit 3쌍 + 신고 블러 임계). */
    ModerationSetting findById(@Param("id") int id);

    /** 단일 정책 행 전체 갱신. */
    int update(ModerationSetting setting);
}
