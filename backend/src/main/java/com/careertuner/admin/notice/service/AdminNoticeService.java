package com.careertuner.admin.notice.service;

import java.util.List;

import com.careertuner.admin.notice.dto.AdminNoticeRequest;
import com.careertuner.admin.notice.dto.AdminNoticeResponse;
import com.careertuner.common.security.AuthUser;

public interface AdminNoticeService {

    List<AdminNoticeResponse> getNotices(AuthUser authUser);

    AdminNoticeResponse createNotice(AuthUser authUser, AdminNoticeRequest request);

    AdminNoticeResponse updateNotice(AuthUser authUser, Long id, AdminNoticeRequest request);

    void deleteNotice(AuthUser authUser, Long id);
}
