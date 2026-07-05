package com.careertuner.admin.securityops.batch;

import lombok.Builder;
import lombok.Data;

/** IP 정책 배치 insert 용 도메인(useGeneratedKeys 로 id 채움). */
@Data
@Builder
public class IpBlockBatch {
    private Long id;
    private String batchCode;
    private String batchName;
    private String sourceType;
    private String sourceName;
    private String ruleAction;
    private int defaultPriority;
    private boolean active;
    private String memo;
    private Long createdBy;
}
