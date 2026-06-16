package com.careertuner.community.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentReport {

    private Long id;
    private Long reporterId;
    private Long commentId;
    private String reason;
    private String detail;
    private String status;
    private String actionTaken;
    private String aiLabel;
    private BigDecimal aiConfidence;
    private Long adminId;
    private LocalDateTime resolvedAt;
    private LocalDateTime createdAt;
}
