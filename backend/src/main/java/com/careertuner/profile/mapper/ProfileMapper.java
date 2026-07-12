package com.careertuner.profile.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.profile.domain.UserProfile;
import com.careertuner.profile.domain.UserProfileVersion;

@Mapper
public interface ProfileMapper {

    UserProfile findByUserId(Long userId);

    UserProfile findByUserIdForUpdate(Long userId);

    void insertEmptyIfAbsent(Long userId);

    void upsert(UserProfile profile);

    /** 현재 프로필의 version_no와 전체 입력을 불변 스냅샷으로 보존한다. */
    int insertVersionFromCurrent(@Param("userId") Long userId,
                                 @Param("source") String source);

    /** AI가 실제로 읽은 프로필 객체를 그대로 보존한다. 동시 저장으로 현재값이 바뀌어도 입력 버전이 흔들리지 않는다. */
    int insertVersionSnapshot(@Param("profile") UserProfile profile,
                              @Param("source") String source);

    List<UserProfileVersion> findVersions(@Param("userId") Long userId,
                                          @Param("limit") int limit);

    UserProfileVersion findVersion(@Param("userId") Long userId,
                                   @Param("versionId") Long versionId);

    UserProfileVersion findVersionByNo(@Param("userId") Long userId,
                                       @Param("versionNo") Integer versionNo);

    UserProfileVersion findLatestVersion(@Param("userId") Long userId);

    List<UserProfile> findAdminProfiles(@Param("keyword") String keyword,
                                        @Param("limit") int limit);
}
