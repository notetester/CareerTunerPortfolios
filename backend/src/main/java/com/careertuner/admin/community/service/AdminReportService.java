package com.careertuner.admin.community.service;

import java.util.List;

import com.careertuner.admin.community.dto.AdminReportActionRequest;
import com.careertuner.admin.community.dto.AdminReportDetailResponse;
import com.careertuner.admin.community.dto.AdminReportListResponse;
import com.careertuner.common.security.AuthUser;

public interface AdminReportService {

    List<AdminReportListResponse> getReports(AuthUser authUser, String status);

    AdminReportDetailResponse getReportDetail(AuthUser authUser, Long id);

    AdminReportDetailResponse takeAction(AuthUser authUser, Long id, AdminReportActionRequest request);

    AdminReportDetailResponse reclassify(AuthUser authUser, Long id);
}
