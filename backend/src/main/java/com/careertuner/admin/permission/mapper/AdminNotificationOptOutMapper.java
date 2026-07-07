package com.careertuner.admin.permission.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 관리자 알림 opt-out 저장 매퍼.
 *
 * <p>별도 테이블 없이 기존 notification_preference.categories_json 에
 * {@code "admin.NEW_REPORT": false} 형태의 하위 키로 저장한다(스키마 변경 없음).
 * 사용자 카테고리 키(ai_analysis 등)와 이름 공간이 겹치지 않도록 항상 "admin." 접두사를 쓴다.</p>
 */
@Mapper
public interface AdminNotificationOptOutMapper {

    /** 사용자의 categories_json 원문. 행이 없으면 null. */
    String findCategoriesJson(@Param("userId") Long userId);

    /** categories_json 의 admin 하위 키 하나를 upsert 한다(행이 없으면 생성). */
    void upsertAdminCategory(@Param("userId") Long userId,
                             @Param("categoryKey") String categoryKey,
                             @Param("enabled") boolean enabled);
}
