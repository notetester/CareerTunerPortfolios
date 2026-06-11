package com.careertuner.file.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 업로드 파일의 메타데이터. 실제 바이트는 디스크에 저장하고 본 레코드는 위치/종류만 보관한다. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileAsset {

    private Long id;
    private Long ownerUserId;
    private String kind;          // AUDIO/VIDEO/RESUME/PORTFOLIO/POSTING/ATTACHMENT
    private String refType;       // 연결 대상 종류 (예: INTERVIEW_ANSWER)
    private Long refId;           // 연결 대상 id
    private String originalName;
    private String contentType;
    private Long sizeBytes;
    private String storageKey;    // mediaDir 기준 상대 경로 (예: 12/uuid.webm)
    private LocalDateTime createdAt;
}
