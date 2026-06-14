package com.careertuner.community.service;

import com.careertuner.community.dto.CreateReportRequest;

public interface ReportService {

    void createReport(CreateReportRequest request, Long userId);
}
