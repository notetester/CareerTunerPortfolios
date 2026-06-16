package com.careertuner.support.dto;

public record FaqResponse(
        Long id,
        String category,
        String question,
        String answer
) {}
