package com.careertuner.runtimesetting.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.runtimesetting.domain.RuntimeSetting;
import com.careertuner.runtimesetting.domain.RuntimeSettingHistory;

/** 런타임 설정 CRUD + 변경 이력 매퍼. */
@Mapper
public interface RuntimeSettingMapper {

    RuntimeSetting findActiveSettingByKey(@Param("settingKey") String settingKey);

    RuntimeSetting findByKey(@Param("settingKey") String settingKey);

    RuntimeSetting findById(@Param("id") Long id);

    List<RuntimeSetting> findSettings(@Param("settingGroup") String settingGroup,
                                      @Param("keyword") String keyword,
                                      @Param("includeInactive") boolean includeInactive);

    void insertSetting(RuntimeSetting setting);

    int updateSetting(RuntimeSetting setting);

    int nextVersionNo(@Param("settingKey") String settingKey);

    void insertHistory(@Param("settingId") Long settingId,
                       @Param("settingKey") String settingKey,
                       @Param("versionNo") int versionNo,
                       @Param("changeType") String changeType,
                       @Param("actorUserId") Long actorUserId,
                       @Param("beforeValue") String beforeValue,
                       @Param("afterValue") String afterValue,
                       @Param("beforeFallback") String beforeFallback,
                       @Param("afterFallback") String afterFallback,
                       @Param("beforeSnapshot") String beforeSnapshot,
                       @Param("afterSnapshot") String afterSnapshot,
                       // reason: 관리자가 설정을 변경한 사유(자유 텍스트, nullable)
                       @Param("reason") String reason);

    List<RuntimeSettingHistory> findHistory(@Param("settingKey") String settingKey, @Param("limit") int limit);
}
