package com.careertuner.admin.securityops.engine;

import lombok.Builder;
import lombok.Data;

/** 차단된 요청 1건의 감사 로그(admin_block_access_log). */
@Data
@Builder
public class BlockAccessLogEntry {
    private String requestId;
    private Long userId;
    private String requestUri;
    private String httpMethod;
    private String blockKind;
    private String blockMatchType;
    private String blockTargetKey;
    private Long blockRuleId;
    private String blockReason;
    private String clientIp;
    private String countryCode;
    private String asn;
    private String cacheSource;
    private String userAgent;
}
