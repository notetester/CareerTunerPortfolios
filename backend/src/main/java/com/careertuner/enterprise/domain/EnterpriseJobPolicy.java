package com.careertuner.enterprise.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnterpriseJobPolicy {

    private Long userId;
    private boolean trusted;
    private boolean createRequiresReview;
    private boolean editRequiresReview;
    private int maxActivePosts;
    private Long updatedBy;
    private String updateReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
