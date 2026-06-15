package com.careertuner.admin.community.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 관리자 신고 상세 화면에 표시할 AI 분류 소견.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiOpinion {

    private String status;
    private Boolean toxic;
    private String category;
    private Double confidence;
    private String model;
    private String completedAt;
    private String errorMessage;
}
