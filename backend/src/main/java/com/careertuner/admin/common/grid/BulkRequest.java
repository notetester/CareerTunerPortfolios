package com.careertuner.admin.common.grid;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 일괄 작업 공통 요청 바디. {@code POST /api/admin/{x}/bulk/{action}} 에서
 * {@code {"ids":[1,2], "params":{"status":"BLOCKED"}}} 형태로 받는다.
 */
public record BulkRequest(List<Long> ids, Map<String, String> params) {

    /** 양수만 남기고 중복을 제거(순서 유지)하며 상한을 적용한 id 목록. */
    public List<Long> sanitizedIds(int cap) {
        return sanitizeIds(ids, cap);
    }

    public static List<Long> sanitizeIds(List<Long> raw, int cap) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        for (Long id : raw) {
            if (id != null && id > 0) {
                unique.add(id);
            }
            if (unique.size() >= cap) {
                break;
            }
        }
        return List.copyOf(unique);
    }

    /** params 에서 값을 꺼낸다. 없으면 null. */
    public String param(String key) {
        return params == null ? null : params.get(key);
    }
}
