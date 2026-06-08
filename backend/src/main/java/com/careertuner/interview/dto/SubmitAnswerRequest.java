package com.careertuner.interview.dto;

import jakarta.validation.constraints.NotBlank;

public record SubmitAnswerRequest(
        @NotBlank String answerText) {
}
