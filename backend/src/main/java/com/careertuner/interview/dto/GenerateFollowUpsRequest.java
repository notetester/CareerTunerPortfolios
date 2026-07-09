package com.careertuner.interview.dto;

/** 꼬리 질문 생성 요청. count 미지정 시 기본 개수를 적용한다. */
public record GenerateFollowUpsRequest(
        Integer count) {
}
