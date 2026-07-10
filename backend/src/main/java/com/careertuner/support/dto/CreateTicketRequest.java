package com.careertuner.support.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateTicketRequest(
        @NotBlank
        @Pattern(regexp = "^(계정|결제|AI기능|기술문제|기타)$", message = "허용되지 않은 문의 카테고리입니다.")
        String category,
        @NotBlank @Size(max = 255) String subject,
        @NotBlank String content,
        /** 첨부 파일 id 목록(선택). /api/file/upload(kind=ATTACHMENT)로 먼저 올린 뒤 그 id를 넘긴다. */
        List<Long> attachmentFileIds
) {}
