package com.careertuner.user.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.user.domain.User;

@Mapper
public interface UserMapper {

    User findById(Long id);

    User findByEmail(String email);

    int countByEmail(String email);

    /** id 는 useGeneratedKeys 로 user 객체에 채워진다. */
    void insert(User user);

    void touchLastLogin(Long id);

    void markEmailVerified(Long id);

    void updatePassword(@Param("id") Long id, @Param("password") String password);
}
