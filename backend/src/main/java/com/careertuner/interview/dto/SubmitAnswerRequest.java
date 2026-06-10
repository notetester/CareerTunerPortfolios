package com.careertuner.interview.dto;

import jakarta.validation.constraints.NotBlank;

/** 텍스트 답변은 AI 평가의 필수 입력. 음성/영상 URL은 file 업로드 후 선택적으로 함께 저장한다. */
public record SubmitAnswerRequest(
        @NotBlank String answerText,
        String audioUrl,
        String videoUrl) {
}
