package com.careertuner.admin.common.security;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdminAccountGuardMapper {

    /** 모든 활성 슈퍼 관리자 행을 동일 순서로 잠가 마지막 관리자 경쟁 조건을 직렬화한다. */
    List<Long> lockActiveSuperAdminIds();

    AdminAccountState findAccountForUpdate(@Param("userId") Long userId);
}
