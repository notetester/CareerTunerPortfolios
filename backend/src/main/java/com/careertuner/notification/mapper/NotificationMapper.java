package com.careertuner.notification.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.notification.domain.Notification;

@Mapper
public interface NotificationMapper {

    List<Notification> findByUserId(@Param("userId") Long userId,
                                    @Param("offset") int offset,
                                    @Param("limit") int limit);

    int countByUserId(@Param("userId") Long userId);

    int countUnreadByUserId(@Param("userId") Long userId);

    Notification findById(@Param("id") Long id);

    void markAsRead(@Param("id") Long id);

    void markAllAsRead(@Param("userId") Long userId);

    int deleteByIdAndUser(@Param("id") Long id, @Param("userId") Long userId);

    int deleteAllByUser(@Param("userId") Long userId);

    int markTypeAsReadByTarget(@Param("userId") Long userId,
                               @Param("type") String type,
                               @Param("targetType") String targetType,
                               @Param("targetId") Long targetId);

    void insert(Notification notification);

    String findUserRole(@Param("userId") Long userId);

    int countFriendship(@Param("userId") Long userId, @Param("otherUserId") Long otherUserId);
}
