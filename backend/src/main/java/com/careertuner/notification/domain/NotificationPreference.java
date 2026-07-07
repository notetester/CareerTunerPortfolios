package com.careertuner.notification.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 사용자별 알림 수신 설정(1행). categoriesJson/rulesJson 에 카테고리·이벤트별 on/off 를 저장한다. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPreference {

    private Long id;
    private Long userId;
    private boolean pushEnabled;
    private boolean emailEnabled;
    private String categoriesJson;
    private String rulesJson;
    private String keywordsJson;
    private String quietHoursStart;
    private String quietHoursEnd;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
