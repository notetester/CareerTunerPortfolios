package com.careertuner.support.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notice {

    private Long id;
    private String title;
    private String content;
    private String category;
    private String status;
    private boolean pinned;
    private String thumbnailUrl;
    private Long adminId;
    private int viewCount;
    private LocalDateTime scheduledAt;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
