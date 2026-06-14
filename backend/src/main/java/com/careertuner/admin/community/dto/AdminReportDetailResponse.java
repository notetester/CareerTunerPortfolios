package com.careertuner.admin.community.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminReportDetailResponse {

    private Long id;
    private String reason;
    private String type;
    private int cnt;
    private String title;
    private String excerpt;
    private String cat;
    private String catKey;
    private String author;
    private String time;
    private String status;
    private String action;
    private List<ReportReasonCount> reasons;
}
