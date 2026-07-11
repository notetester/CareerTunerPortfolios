package com.careertuner.interview.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 텍스트 답변은 AI 평가의 필수 입력. 음성/영상 URL은 file 업로드 후 선택적으로 함께 저장한다.
 * modelAnswer 는 사용자에게 보여준 모범답안(답안지) — 있으면 채점의 만점 기준으로 함께 넘긴다.
 */
public record SubmitAnswerRequest(
        @NotBlank String answerText,
        String audioUrl,
        String videoUrl,
        Long audioFileId,
        Long videoFileId,
        String modelAnswer) {
}
