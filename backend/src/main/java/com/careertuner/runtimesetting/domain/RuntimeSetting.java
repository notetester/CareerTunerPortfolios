package com.careertuner.runtimesetting.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 런타임 설정 1건(application_runtime_setting). DB 값이 코드 기본값보다 우선. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuntimeSetting {
    private Long id;
    private String settingKey;
    private String settingGroup;
    private String displayName;
    private String settingValue;
    private String fallbackValue;
    private String valueType;
    private boolean secret;
    private boolean editable;
    private boolean active;
    private String description;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
