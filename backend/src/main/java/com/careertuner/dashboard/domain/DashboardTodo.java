package com.careertuner.dashboard.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * C 소유 dashboard_todo 행. derivedKey가 null이면 사용자가 직접 추가한 할 일,
 * 값이 있으면 파생(자동 계산) 할 일의 완료 오버라이드다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardTodo {

    private Long id;
    private Long userId;
    private String derivedKey;
    private String task;
    private String timeLabel;
    private boolean done;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
