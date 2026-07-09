package com.careertuner.billing.domain;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RefundPolicy {

    private Long id;
    private String policyCode;
    private int version;
    private String title;
    private String summary;
    private String content;
    private String rulesJson;
    private String status;
    private boolean adverse;
    private LocalDateTime effectiveAt;
    private LocalDateTime publishedAt;
    private Long noticeId;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
