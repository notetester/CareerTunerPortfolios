package com.careertuner.notification.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.notification.domain.PushSubscription;

@Mapper
public interface PushSubscriptionMapper {

    void upsert(PushSubscription subscription);

    List<PushSubscription> findByUserId(@Param("userId") Long userId);

    int deleteByToken(@Param("userId") Long userId, @Param("token") String token);

    int deleteAllByUserId(@Param("userId") Long userId);

    int countByUserId(@Param("userId") Long userId);
}
