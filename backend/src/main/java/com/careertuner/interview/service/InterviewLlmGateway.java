package com.careertuner.interview.service;

import java.util.Map;

import tools.jackson.databind.JsonNode;

/**
 * 면접 도메인 구조화 LLM 호출 게이트웨이.
 *
 * <p>도메인 로직(프롬프트 구성·JSON 스키마·응답 매핑)은 호출부({@link InterviewOpenAiClient})에 두고,
 * 이 게이트웨이는 "provider 로의 전송"만 담당한다. 덕분에 한 곳만 갈아끼우면 전 호출의 provider 가 바뀐다.
 *
 * <ul>
 *   <li>{@link OssLlmGateway} — 자체 파인튜닝 모델(학습된 생성 task 1차, 점진 확대)</li>
 *   <li>{@link AnthropicLlmGateway} — Claude(Haiku) (자체 모델 미학습 task 담당 + 1차 폴백)</li>
 *   <li>{@link OpenAiLlmGateway} — 최종 폴백 provider</li>
 *   <li>{@link FallbackInterviewLlmGateway} — oss→Claude→OpenAI 폴백 디스패처(@Primary, 호출부가 주입받는 구현)</li>
 * </ul>
 */
public interface InterviewLlmGateway {

    Result complete(Request request);

    /**
     * 구조화 LLM 요청.
     *
     * @param schemaName   구조화 출력 이름(OpenAI json_schema name)
     * @param jsonSchema   기대 출력 JSON 스키마(OpenAI json_schema 형식; Gemini 는 프롬프트에 임베드)
     * @param systemPrompt 시스템 프롬프트
     * @param userPrompt   유저 프롬프트
     * @param model        OpenAI 작업별 모델(생성/채점). Gemini 게이트웨이는 자체 모델을 사용한다.
     */
    record Request(String schemaName, Map<String, Object> jsonSchema,
                   String systemPrompt, String userPrompt, String model) {
    }

    /** 파싱된 출력 payload(JSON 객체) + 사용량(실제 응답한 provider/model 반영). */
    record Result(JsonNode payload, InterviewOpenAiClient.Usage usage) {
    }
}
