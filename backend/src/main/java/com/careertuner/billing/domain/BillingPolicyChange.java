package com.careertuner.billing.domain;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BillingPolicyChange {

    private Long id;
    private String targetType;
    private String targetCode;
    private String currentSnapshotJson;
    private String nextSnapshotJson;
    private LocalDateTime effectiveFrom;
    private String applyMode;
    private String status;
    private Long createdBy;
    private LocalDateTime createdAt;
    private Long canceledBy;
    private LocalDateTime canceledAt;
    private LocalDateTime appliedAt;
}
