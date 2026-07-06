package com.careertuner.admin.permission.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 관리자 실효 권한(직접 부여 ∪ 그룹 경유) 조회 매퍼. */
@Mapper
public interface EffectivePermissionMapper {

    /** 사용자의 실효 권한 코드 목록. 2-way UNION DISTINCT. */
    List<String> findEffectivePermissionCodes(@Param("userId") Long userId);
}
