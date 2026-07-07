package com.careertuner.activitylog.domain;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

/** 일반 활동 로그 1건(user_activity_log). 인터셉터가 요청마다 채운다. */
@Data
@Builder
public class UserActivityLog {
    private Long id;
    private String requestId;
    private String flowTraceId;
    private Long userId;
    private String sessionId;
    private String requestUri;
    private String httpMethod;
    private String activityDomain;
    private String activityType;
    private String activityCode;
    private String activityProvider;
    private String authEventType;
    private String targetType;
    private String targetId;
    private String handlerName;
    private String queryString;
    private String referer;
    private String ipAddress;
    private String userAgent;
    private Integer responseStatus;
    private Integer responseTimeMs;
    private Boolean success;
    private String detailSummary;
    private LocalDateTime createdAt;
}
