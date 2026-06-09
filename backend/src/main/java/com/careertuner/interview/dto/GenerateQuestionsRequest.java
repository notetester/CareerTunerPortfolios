package com.careertuner.interview.dto;

/** mode 는 세션 값으로 대체 가능하므로 선택. count 미지정 시 기본값 적용. */
public record GenerateQuestionsRequest(
        String mode,
        Integer count) {
}
