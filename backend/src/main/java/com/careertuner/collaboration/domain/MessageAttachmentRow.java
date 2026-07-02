package com.careertuner.collaboration.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageAttachmentRow {

    private Long id;
    private Long messageId;
    private Long fileAssetId;
    private String originalName;
    private String contentType;
    private Long sizeBytes;
    private LocalDateTime createdAt;
}
