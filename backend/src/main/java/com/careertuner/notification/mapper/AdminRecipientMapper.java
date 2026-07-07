package com.careertuner.notification.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 관리자 알림 팬아웃 대상(userId) 조회 전용 매퍼.
 * <p>notification 도메인에 두어 타 도메인(user) 매퍼에 의존하지 않는다.
 */
@Mapper
public interface AdminRecipientMapper {

    /** role IN (ADMIN, SUPER_ADMIN) 이고 ACTIVE 인 관리자 userId 목록. */
    List<Long> findActiveAdminIds();

    /**
     * 타입별 팬아웃 대상: (필요 권한 실효 보유 ADMIN ∪ SUPER_ADMIN) − opt-out.
     *
     * @param permissionCodes 필요 권한 코드(ANY-of). null 이면 권한 필터 없이 활성 관리자 전원.
     * @param optOutKey       notification_preference.categories_json 의 opt-out 키
     *                        (예: "admin.NEW_REPORT"). 값이 false 인 관리자는 제외.
     */
    List<Long> findAdminIdsForType(@Param("permissionCodes") List<String> permissionCodes,
                                   @Param("optOutKey") String optOutKey);
}
