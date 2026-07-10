package com.careertuner.community.moderation.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * post_ai_result 테이블 매핑 도메인.
 * result_json(JSON 컬럼)은 String으로 다루고 파싱은 서비스 계층 책임.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostAiResult {

    private Long id;
    private Long postId;
    private AiTaskType taskType;
    private AiResultStatus status;
    private String resultJson;
    private String model;
    private String errorMessage;
    private int attemptCount;
    private ModerationReviewAction reviewAction;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
