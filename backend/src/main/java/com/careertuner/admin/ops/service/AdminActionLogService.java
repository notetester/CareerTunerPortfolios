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

@Service
@RequiredArgsConstructor
public class AdminActionLogService {

    private final AdminActionLogMapper mapper;

    @Transactional
    public void record(AuthUser actor, Long targetUserId, String actionType, String targetType,
                       String beforeValue, String afterValue, String reason) {
        Long actorId = actor == null ? null : actor.id();
        mapper.insert(new AdminActionLogCreate(actorId, targetUserId, actionType, targetType,
                blankToNull(beforeValue), blankToNull(afterValue), blankToNull(reason), null, null));
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
}
