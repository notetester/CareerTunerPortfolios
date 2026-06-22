package com.careertuner.interview.dto;

import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

/**
 * 음성 모의면접 종료 후, 대화 트랜스크립트를 질문별 답변으로 매핑해 내용 채점을 요청한다.
 *
 * <p>트랜스크립트는 {@code [{"role":"ai|user","text":"..."}]} 형태의 대화 흐름이다.
 * 서버가 준비된 질문과 함께 LLM 에 넘겨 질문별 답을 추출·채점하고 {@code interview_answer} 로 저장한다.
 *
 * @param transcript 대화 트랜스크립트 JSON 배열
 */
public record ScoreVoiceTranscriptRequest(@NotNull JsonNode transcript) {
}
