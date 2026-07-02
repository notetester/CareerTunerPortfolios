package com.careertuner.collaboration.dto;

import java.util.List;

import jakarta.validation.constraints.Size;

public record SendMessageRequest(
        @Size(max = 20) String kind,
        @Size(max = 4000) String content,
        List<Long> attachmentFileIds
) {
}
