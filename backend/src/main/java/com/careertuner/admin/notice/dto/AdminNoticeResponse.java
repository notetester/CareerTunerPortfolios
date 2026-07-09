package com.careertuner.admin.notice.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminNoticeResponse {

    private Long id;
    private String title;
    private String content;
    private String category;
    private String status;
    private boolean pinned;
    private String thumbnailUrl;
    private int viewCount;
    private LocalDateTime scheduledAt;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
