package com.careertuner.dashboard.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 최근 알림 카드용 읽기 전용 조회 결과. notification(F 소유)은 조회만 하고 수정하지 않는다.
 * PRODUCT_STRUCTURE.md가 대시보드의 참조 대상으로 notification 도메인을 명시한다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardNotificationSource {

    private Long id;
    private String type;
    private String title;
    private String message;
    private String link;
    private boolean read;
    private LocalDateTime createdAt;
}
