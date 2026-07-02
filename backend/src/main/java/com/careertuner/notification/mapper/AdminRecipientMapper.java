package com.careertuner.notification.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

/**
 * 관리자 알림 팬아웃 대상(userId) 조회 전용 매퍼.
 * <p>notification 도메인에 두어 타 도메인(user) 매퍼에 의존하지 않는다.
 */
@Mapper
public interface AdminRecipientMapper {

    /** role IN (ADMIN, SUPER_ADMIN) 이고 ACTIVE 인 관리자 userId 목록. */
    List<Long> findActiveAdminIds();
}
