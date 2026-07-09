package com.careertuner.support.dto;

import com.careertuner.file.domain.FileAsset;

/** 문의 메시지에 딸린 첨부 한 건. contentUrl 로 바이트를 내려받는다(소유자=본인). */
public record TicketAttachmentView(
        Long id,
        String name,
        Long size,
        String contentType,
        String contentUrl
) {
    public static TicketAttachmentView from(FileAsset asset) {
        return new TicketAttachmentView(
                asset.getId(),
                asset.getOriginalName(),
                asset.getSizeBytes(),
                asset.getContentType(),
                "/api/file/" + asset.getId() + "/content");
    }
}
