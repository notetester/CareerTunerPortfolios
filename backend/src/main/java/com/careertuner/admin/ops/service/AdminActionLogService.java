package com.careertuner.admin.ops.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.admin.common.AdminAccess;
import com.careertuner.admin.ops.dto.AdminActionLogCreate;
import com.careertuner.admin.ops.dto.AdminActionLogRow;
import com.careertuner.admin.ops.mapper.AdminActionLogMapper;
import com.careertuner.common.security.AuthUser;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class AdminActionLogService {

    private final AdminActionLogMapper mapper;
    private final ObjectMapper objectMapper;

    @Transactional
    public void record(AuthUser actor, Long targetUserId, String actionType, String targetType,
                       Object beforeValue, Object afterValue, String reason) {
        Long actorId = actor == null ? null : actor.id();
        mapper.insert(new AdminActionLogCreate(actorId, targetUserId, actionType, targetType,
                toJson(beforeValue), toJson(afterValue), blankToNull(reason), null, null));
    }

    @Transactional(readOnly = true)
    public List<AdminActionLogRow> recent(AuthUser authUser, String keyword, String actionType, String targetType, int limit) {
        AdminAccess.requireAdmin(authUser);
        return mapper.findRecent(blankToNull(keyword), blankToNull(actionType), blankToNull(targetType),
                limit <= 0 ? 100 : Math.min(limit, 300));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            String normalized = blankToNull(text);
            if (normalized == null) {
                return null;
            }
            // 기존 호출자가 넘기던 JSON object/array 문자열만 구조로 보존한다.
            // 숫자·true·null처럼 보이는 일반 메모까지 JSON scalar로 오인하지 않는다.
            if (looksLikeStructuredJson(normalized)) {
                try {
                    JsonNode parsed = objectMapper.readTree(normalized);
                    if (parsed != null) {
                        return objectMapper.writeValueAsString(parsed);
                    }
                } catch (JacksonException ignored) {
                    // 유효하지 않은 object/array 모양 문자열은 일반 문자열로 기록한다.
                }
            }
            return writeJsonString(normalized);
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException ignored) {
            return writeJsonString(String.valueOf(value));
        }
    }

    private String writeJsonString(String value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException ignored) {
            return "\"[unserializable]\"";
        }
    }

    private static boolean looksLikeStructuredJson(String value) {
        return (value.startsWith("{") && value.endsWith("}"))
                || (value.startsWith("[") && value.endsWith("]"));
    }
}
