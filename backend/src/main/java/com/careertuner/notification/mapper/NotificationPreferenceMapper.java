package com.careertuner.notification.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.notification.domain.NotificationPreference;

@Mapper
public interface NotificationPreferenceMapper {

    NotificationPreference findByUserId(@Param("userId") Long userId);

    void upsert(NotificationPreference preference);
}
