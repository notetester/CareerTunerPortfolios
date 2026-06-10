package com.careertuner.file.dto;

import java.time.LocalDateTime;

import com.careertuner.file.domain.FileAsset;

/** 업로드 결과. contentUrl 로 바이트를 다시 내려받을 수 있다. */
public record FileAssetResponse(
        Long id,
        String kind,
        String refType,
        Long refId,
        String originalName,
        String contentType,
        Long sizeBytes,
        String contentUrl,
        LocalDateTime createdAt) {

    public static FileAssetResponse from(FileAsset asset) {
        return new FileAssetResponse(
                asset.getId(),
                asset.getKind(),
                asset.getRefType(),
                asset.getRefId(),
                asset.getOriginalName(),
                asset.getContentType(),
                asset.getSizeBytes(),
                "/api/file/" + asset.getId() + "/content",
                asset.getCreatedAt());
    }
}
