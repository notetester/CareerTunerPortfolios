package com.careertuner.community.moderation.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * comment_ai_result 테이블 매핑 도메인.
 * post_ai_result 구조 복제(대상만 commentId). result_json은 String으로 다루고 파싱은 서비스 책임.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentAiResult {

    private Long id;
    private Long commentId;
    private AiTaskType taskType;
    private AiResultStatus status;
    private String resultJson;
    private String model;
    private String errorMessage;
    private int attemptCount;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
