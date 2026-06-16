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
public class Faq {

    private Long id;
    private String question;
    private String answer;
    private String category;
    private int sortOrder;
    private boolean published;
    private Long adminId;
    private int viewCount;
    private String embedding; // JSON 문자열 (bge-m3 1024차원 벡터)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
