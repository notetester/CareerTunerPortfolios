package com.careertuner.admin.common.security;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdminAccountGuardMapper {

    /** 검증된 비seed 활성 슈퍼 관리자 행을 동일 순서로 잠가 안전 quorum 변경을 직렬화한다. */
    List<Long> lockSafeActiveSuperAdminIds();

    AdminAccountState findAccountForUpdate(@Param("userId") Long userId);

    /** ACTIVE·이메일 검증·개별 BCrypt·비seed 조건을 모두 만족하는 승격 후보인지 확인한다. */
    boolean isSafeSuperAdminPromotionCandidate(@Param("userId") Long userId);
}
