package com.careertuner.admin.notification.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.admin.notification.dto.AdminNotificationResponse;
import com.careertuner.admin.notification.mapper.AdminNotificationMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminNotificationServiceImpl implements AdminNotificationService {

    private final AdminNotificationMapper notificationMapper;

    @Override
    public List<AdminNotificationResponse> getNotifications(AuthUser authUser, int size) {
        requireAdmin(authUser);
        int limit = Math.max(20, Math.min(size, 200));
        return notificationMapper.findRecent(limit);
    }

    private void requireAdmin(AuthUser authUser) {
        if (authUser == null || !"ADMIN".equals(authUser.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "관리자 권한이 필요합니다.");
        }
    }
}
