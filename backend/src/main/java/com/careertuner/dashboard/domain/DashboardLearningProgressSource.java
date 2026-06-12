package com.careertuner.dashboard.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 준비도 게이지용 학습 로드맵 진행 현황(지원 건별 최신 분석의 체크리스트 기준). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardLearningProgressSource {

    private int totalTasks;
    private int completedTasks;
}
