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
    private LocalDateTime updatedAt;
}
