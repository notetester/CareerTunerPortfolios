package com.careertuner.privacy.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 사용자별 관계 정책 문서(1행). policyJson = {relations: {관계: {표면키: allow|block}}}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPrivacyPolicy {

    private Long id;
    private Long userId;
    private String policyJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
