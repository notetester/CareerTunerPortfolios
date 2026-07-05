package com.careertuner.admin.notification.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

/**
 * 관리자 캠페인 팬아웃 대상(userId) 조회 전용 매퍼.
 * <p>notification 도메인의 AdminRecipientMapper 패턴을 따르되, 캠페인 전용 쿼리라 admin 쪽에 둔다.
 */
@Mapper
public interface AdminCampaignRecipientMapper {

    /** status = ACTIVE 인 전체 사용자 userId 목록. */
    List<Long> findActiveUserIds();
}
