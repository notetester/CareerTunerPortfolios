package com.careertuner.admin.faq.dto;

public record AdminFaqRequest(
        String category,
        String question,
        String answer,
        Boolean isPublished,
        Integer sortOrder
) {}
