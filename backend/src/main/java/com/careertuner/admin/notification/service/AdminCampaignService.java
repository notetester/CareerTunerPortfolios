package com.careertuner.admin.notification.service;

import com.careertuner.admin.notification.dto.AdminCampaignRequest;
import com.careertuner.admin.notification.dto.AdminCampaignResponse;
import com.careertuner.common.security.AuthUser;

public interface AdminCampaignService {

    /** 캠페인(공지/광고/추천) 알림을 ACTIVE 사용자 전원에게 팬아웃한다. */
    AdminCampaignResponse sendCampaign(AuthUser authUser, AdminCampaignRequest request);
}
