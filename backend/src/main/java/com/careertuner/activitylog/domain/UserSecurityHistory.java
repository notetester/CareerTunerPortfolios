package com.careertuner.activitylog.domain;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

/** 민감 계정 이벤트 감사 1건(user_security_history) — 아이디찾기/비번재설정/이메일·전화 인증 등. */
@Data
@Builder
public class UserSecurityHistory {
    private Long id;
    private Long userId;
    private Long actorUserId;
    private String eventType;
    private String eventStage;
    private String inputIdentifier;
    private String targetEmail;
    private Boolean success;
    private String failReason;
    private String detailMessage;
    private String requestId;
    private String flowTraceId;
    private String ipAddress;
    private String userAgent;
    private LocalDateTime occurredAt;
}
