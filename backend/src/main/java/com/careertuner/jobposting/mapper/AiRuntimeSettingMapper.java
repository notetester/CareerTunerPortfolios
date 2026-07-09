package com.careertuner.jobposting.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AiRuntimeSettingMapper {

    String findValueJson(@Param("settingKey") String settingKey);

    int upsertValueJson(@Param("settingKey") String settingKey,
                        @Param("valueJson") String valueJson,
                        @Param("updatedBy") Long updatedBy);
}
