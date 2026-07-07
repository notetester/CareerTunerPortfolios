package com.careertuner.admin.ads.service;

import java.util.List;

import com.careertuner.admin.ads.dto.AdminAdRequest;
import com.careertuner.admin.ads.dto.AdminAdResponse;
import com.careertuner.common.security.AuthUser;

/** 관리자 광고 CRUD. */
public interface AdminAdService {

    List<AdminAdResponse> list(AuthUser authUser, String placement, String platform, boolean activeOnly);

    AdminAdResponse get(AuthUser authUser, Long id);

    AdminAdResponse create(AuthUser authUser, AdminAdRequest request);

    AdminAdResponse update(AuthUser authUser, Long id, AdminAdRequest request);

    AdminAdResponse toggleActive(AuthUser authUser, Long id, boolean active);

    void delete(AuthUser authUser, Long id);
}
