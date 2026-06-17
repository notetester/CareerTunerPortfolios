package com.careertuner.support.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 사용자가 자신의 문의에 추가로 남기는 메시지. */
public record TicketMessageRequest(
        @NotBlank(message = "내용을 입력해 주세요.")
        @Size(max = 2000, message = "내용은 2000자 이내로 입력해 주세요.")
        String content
) {}
