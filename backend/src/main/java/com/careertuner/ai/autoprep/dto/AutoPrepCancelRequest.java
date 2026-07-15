package com.careertuner.ai.autoprep.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 진행 중인 SSE 실행을 사용자 범위에서 취소하는 요청. */
public record AutoPrepCancelRequest(
        @NotBlank
        @Size(max = 80)
        @Pattern(regexp = "[A-Za-z0-9_-]+", message = "runId 형식이 올바르지 않습니다.")
        String runId
) {
}
