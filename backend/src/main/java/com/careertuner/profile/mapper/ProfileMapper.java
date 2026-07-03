package com.careertuner.profile.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.profile.domain.UserProfile;

@Mapper
public interface ProfileMapper {

    UserProfile findByUserId(Long userId);

    void upsert(UserProfile profile);

    int countLoginId(@Param("loginId") String loginId, @Param("excludeUserId") Long excludeUserId);

    void updateAccountBasics(@Param("userId") Long userId,
                             @Param("loginId") String loginId,
                             @Param("phoneNumber") String phoneNumber);

    List<UserProfile> findAdminProfiles(@Param("keyword") String keyword,
                                        @Param("limit") int limit);
}
