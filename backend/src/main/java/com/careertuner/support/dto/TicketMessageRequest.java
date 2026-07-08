package com.careertuner.support.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 사용자가 자신의 문의에 추가로 남기는 메시지. */
public record TicketMessageRequest(
        @NotBlank(message = "내용을 입력해 주세요.")
        @Size(max = 2000, message = "내용은 2000자 이내로 입력해 주세요.")
        String content,
        /** 첨부 파일 id 목록(선택). /api/file/upload(kind=ATTACHMENT)로 먼저 올린 뒤 그 id를 넘긴다. */
        List<Long> attachmentFileIds
) {}
