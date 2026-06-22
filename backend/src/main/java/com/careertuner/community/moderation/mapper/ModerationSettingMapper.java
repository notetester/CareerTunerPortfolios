package com.careertuner.community.moderation.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.community.moderation.domain.ModerationSetting;

@Mapper
public interface ModerationSettingMapper {

    ModerationSetting findById(@Param("id") int id);

    int update(@Param("id") int id,
               @Param("strictness") String strictness,
               @Param("hideThreshold") double hideThreshold,
               @Param("sanctionThreshold") int sanctionThreshold,
               @Param("blockDays") int blockDays);
}
