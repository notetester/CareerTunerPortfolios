package com.careertuner.admin.permission.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.admin.permission.mapper.AdminNotificationOptOutMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * 관리자 알림 카테고리 opt-out 설정.
 *
 * <p>저장소는 기존 notification_preference.categories_json 의 "admin.{TYPE}" 하위 키.
 * 키가 없으면 수신(true)이 기본이다. 팬아웃 수신자 쿼리(AdminRecipientMapper)가
 * 같은 키를 읽어 false 인 관리자를 제외한다.</p>
 */
@Service
@RequiredArgsConstructor
public class AdminNotificationOptOutService {

    /** opt-out 을 지원하는 관리자 알림 type (알림 타입 레지스트리에 이미 등록된 값만). */
    public static final List<String> ADMIN_OPT_OUT_TYPES = List.of(
            "NEW_REPORT",
            "NEW_TICKET",
            "NEW_USER",
            "NEW_COMPANY_APPLICATION",
            "NEW_JOB_POSTING_REVIEW");

    private static final String KEY_PREFIX = "admin.";

    private final AdminNotificationOptOutMapper mapper;
    private final ObjectMapper objectMapper;

    /** 관리자 알림 type 별 수신 여부(키 없으면 true). */
    @Transactional(readOnly = true)
    public Map<String, Boolean> getAdminCategories(Long userId) {
        Map<String, Boolean> stored = parse(mapper.findCategoriesJson(userId));
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (String type : ADMIN_OPT_OUT_TYPES) {
            result.put(type, stored.getOrDefault(KEY_PREFIX + type, Boolean.TRUE));
        }
        return result;
    }

    /** 관리자 알림 type 하나의 수신 여부 변경. */
    @Transactional
    public Map<String, Boolean> updateAdminCategory(Long userId, String type, Boolean enabled) {
        if (type == null || !ADMIN_OPT_OUT_TYPES.contains(type)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 관리자 알림 유형입니다.");
        }
        if (enabled == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "enabled 값이 필요합니다.");
        }
        mapper.upsertAdminCategory(userId, KEY_PREFIX + type, enabled);
        return getAdminCategories(userId);
    }

    private Map<String, Boolean> parse(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Boolean>>() {
            });
        } catch (RuntimeException ex) {
            // 다른 타입 값(rules 등)이 섞여 파싱이 깨져도 설정 화면은 기본값으로 동작해야 한다.
            return Map.of();
        }
    }
}
