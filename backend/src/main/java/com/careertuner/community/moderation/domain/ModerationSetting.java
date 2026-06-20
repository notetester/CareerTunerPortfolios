package com.careertuner.community.moderation.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModerationSetting {

    private int id;
    private Strictness strictness;
    private double hideThreshold;
    private int sanctionThreshold;   // 누적 숨김 글 수 임계(이 이상이면 사용자 자동 차단)
    private int blockDays;           // 자동 차단 유지 일수
    private LocalDateTime updatedAt;
}
