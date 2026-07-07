package com.careertuner.user.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.user.domain.User;
import com.careertuner.user.domain.UserResumeDetail;

/**
 * 계정 확충(로그인 아이디·전화번호) + 이력서 상세 스펙 매퍼.
 *
 * <p>기존 UserMapper 를 건드리지 않고 W6 신규 컬럼/테이블만 담당한다.</p>
 */
@Mapper
public interface UserAccountMapper {

    // ── 계정 정보 ──

    User findById(Long id);

    /** 연결된 소셜 provider 목록(user_social). */
    List<String> findLinkedProviders(Long userId);

    int countByLoginId(String loginId);

    int countByEmailExcludingUser(@Param("email") String email, @Param("excludeUserId") Long excludeUserId);

    int countByPhone(@Param("phone") String phone, @Param("excludeUserId") Long excludeUserId);

    /** 로그인 아이디는 아직 미설정(NULL)일 때만 최초 설정한다. */
    int setLoginIdIfAbsent(@Param("userId") Long userId, @Param("loginId") String loginId);

    void updatePhone(@Param("userId") Long userId, @Param("phone") String phone);

    // ── 이력서 상세 스펙 ──

    UserResumeDetail findResumeDetail(Long userId);

    void upsertResumeDetail(UserResumeDetail detail);
}
