package com.careertuner.profile.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.profile.domain.UserProfile;

@Mapper
public interface ProfileMapper {

    UserProfile findByUserId(Long userId);

    UserProfile findByUserIdForUpdate(Long userId);

    void insertEmptyIfAbsent(Long userId);

    void upsert(UserProfile profile);

    List<UserProfile> findAdminProfiles(@Param("keyword") String keyword,
                                        @Param("limit") int limit);
}
